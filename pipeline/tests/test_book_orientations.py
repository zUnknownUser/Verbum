import json
import os
import socket
import subprocess
import time
import urllib.error
import urllib.request

import psycopg
import pytest

from verbum_pipeline.book_orientations import DIRECTORY, ROOT, build, canon
from verbum_pipeline.files import digest, read, write
from verbum_pipeline.models import Bundle
from verbum_pipeline.publish import publish
from verbum_pipeline.review import items

from .test_events import approve_publish

CATALOG = DIRECTORY / "catalog.json"


def test_complete_bilingual_catalog_and_resolvable_readings():
    bundle = build()
    assert read(CATALOG) == bundle.model_dump()
    assert len(bundle.bookOrientations) == 66
    assert len([i for i in items(bundle) if i["key"].startswith("bookOrientation:")]) == 66
    chapters = {
        (r.split("\t")[0], int(r.split("\t")[1]))
        for r in (ROOT / "VerbumKit/Sources/Clients/Resources/web.tsv").read_text().splitlines()
    }
    for book in bundle.bookOrientations:
        assert book.chapterCount == canon()[book.bookId][1]
        for language, p in book.presentations.items():
            assert all((r.bookId, r.chapter) in chapters for r in p.readings)
            assert p.context != book.presentations["pt-BR" if language == "en" else "en"].context


@pytest.mark.parametrize(
    "defect",
    [
        "missing_book",
        "duplicate_book",
        "language",
        "outline",
        "reading",
        "source",
        "foreign_catalog",
    ],
)
def test_incomplete_or_mismatched_catalog_cannot_be_reviewed(defect):
    value = read(CATALOG)
    first = value["bookOrientations"][0]
    if defect == "missing_book":
        value["bookOrientations"].pop()
    if defect == "duplicate_book":
        value["bookOrientations"][-1] = first
    if defect == "language":
        first["presentations"].pop("pt-BR")
    if defect == "outline":
        first["presentations"]["pt-BR"]["outline"][0]["chapterEnd"] = 10
    if defect == "reading":
        first["presentations"]["en"]["readings"][0]["chapter"] = 51
    if defect == "source":
        value["content"]["sources"][-1]["url"] = "https://example.com"
    if defect == "foreign_catalog":
        value["content"]["dailyVersePool"] = [{"bookId": "John", "chapter": 3}]
    with pytest.raises(ValueError):
        Bundle.model_validate(value)


def test_publication_idempotence_stale_guard_and_preserved_catalog(database, tmp_path, artifacts):
    approve_publish(artifacts[0], tmp_path, "fixtures", database, True)
    with psycopg.connect(database) as conn:
        before = conn.execute("SELECT id,name,summary FROM entities ORDER BY id").fetchall()
    result, approved = approve_publish(CATALOG, tmp_path, "books", database)
    assert result["status"] == "published"
    assert publish(CATALOG, approved, database)["status"] == "already_published"
    with psycopg.connect(database) as conn:
        assert conn.execute(
            "SELECT count(*),sum(chapter_count) FROM book_orientations"
        ).fetchone() == (66, 1189)
        assert conn.execute("SELECT count(*) FROM book_orientation_sources").fetchone()[0] == 132
        assert conn.execute("SELECT id,name,summary FROM entities ORDER BY id").fetchall() == before
        original = conn.execute(
            "SELECT presentations FROM book_orientations WHERE book_id='Gen'"
        ).fetchone()[0]
    changed = read(CATALOG)
    changed["bookOrientations"][0]["presentations"]["en"]["purpose"] += " Revised."
    path = tmp_path / "changed.json"
    write(path, changed)
    with pytest.raises(ValueError, match="changed since review"):
        approve_publish(path, tmp_path, "stale", database)
    changed["bookOrientations"][0]["previousHash"] = digest(original)
    path = tmp_path / "refreshed.json"
    write(path, changed)
    assert approve_publish(path, tmp_path, "fresh", database)[0]["status"] == "published"


def test_late_failure_rolls_back_everything(database, tmp_path):
    with psycopg.connect(database) as conn:
        conn.execute(
            "ALTER TABLE book_orientations ADD CONSTRAINT fail_test CHECK (book_id<>'Rev')"
        )
    with pytest.raises(psycopg.errors.CheckViolation):
        approve_publish(CATALOG, tmp_path, "fail", database)
    with psycopg.connect(database) as conn:
        for table in [
            "book_orientations",
            "book_orientation_sources",
            "sources",
            "content_publications",
        ]:
            assert conn.execute(f"SELECT count(*) FROM {table}").fetchone()[0] == 0


# Each testament uses an isolated server, within the ordinary public request budget.
@pytest.mark.parametrize("bounds", [(0, 39), (39, 66)], ids=["OT", "NT"])
def test_every_book_through_http_both_languages_with_chapter_study_preserved(
    database, tmp_path, bounds
):
    binary = os.environ.get("VERBUM_TEST_API_BINARY")
    if not binary:
        pytest.skip("VERBUM_TEST_API_BINARY required")
    approve_publish(CATALOG, tmp_path, "books", database)
    approve_publish(
        ROOT / "pipeline/sources/chapter-studies/exodus.json", tmp_path, "studies", database
    )
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        port = sock.getsockname()[1]
    process = subprocess.Popen(
        [binary],
        cwd=ROOT / "backend",
        env={**os.environ, "VERBUM_DATABASE_URL": database, "VERBUM_ADDR": f"127.0.0.1:{port}"},
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    base = f"http://127.0.0.1:{port}"
    try:
        for _ in range(100):
            try:
                with urllib.request.urlopen(base + "/healthz", timeout=1):
                    break
            except urllib.error.URLError:
                if process.poll() is not None:
                    pytest.fail("test API startup failed")
                time.sleep(0.05)
        else:
            pytest.fail("test API startup timed out")
        for book in build().bookOrientations[bounds[0] : bounds[1]]:
            for lang in ["en", "pt-BR"]:
                for ch in sorted({1, book.chapterCount}):
                    with urllib.request.urlopen(
                        f"{base}/v1/passages/{book.bookId}.{ch}/context?lang={lang}", timeout=5
                    ) as response:
                        page = json.load(response)
                    assert page["bookOrientation"] == book.presentations[lang].model_dump(
                        exclude_none=True
                    )
                    assert set(page["bookOrientation"]["sourceReferenceIds"]) <= {
                        s["id"] for s in page["sources"]
                    }
        with urllib.request.urlopen(
            base + "/v1/passages/Exod.14/context?lang=pt-BR", timeout=5
        ) as response:
            page = json.load(response)
        assert page["study"] and page["bookOrientation"]
        with pytest.raises(urllib.error.HTTPError) as error:
            urllib.request.urlopen(base + "/v1/passages/Gen.51/context", timeout=5)
        assert error.value.code == 404
    finally:
        process.terminate()
        try:
            process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=5)
