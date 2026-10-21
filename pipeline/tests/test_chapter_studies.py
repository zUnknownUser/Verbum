import json
import os
import socket
import subprocess
import time
import urllib.error
import urllib.request

import psycopg
import pytest

from verbum_pipeline.chapter_studies import ROOT, build, validate_references
from verbum_pipeline.files import digest, read, write
from verbum_pipeline.models import Bundle
from verbum_pipeline.publish import publish
from verbum_pipeline.review import require_approval

from .test_events import approve_publish

CATALOG = ROOT / "sources/chapter-studies/exodus.json"


def test_bounded_bilingual_studies_cover_exactly_the_existing_exodus_readings():
    bundle = Bundle.model_validate(read(CATALOG))
    assert bundle == build()
    validate_references(bundle)
    require_approval(bundle, ROOT / "review/chapter-studies-2026-10-21.json")
    sequence = Bundle.model_validate(read(ROOT / "sources/sequences/exodus.json"))
    refs = {
        (p.bookId, p.chapter)
        for item in sequence.eventSequences[0].items
        for p in sequence.timelineDiscovery.presentations[item.timelineId]["en"].keyPassages
        if p.bookId != "John"  # John 3 is a connection, not a sequence step.
    }
    assert len(refs) == 21
    assert {(s.reference.bookId, s.reference.chapter) for s in bundle.chapterStudies} == refs
    assert not bundle.content.entities and not bundle.content.timeline
    for source, review in [
        ("themes/catalog.json", "themes-2026-10-08.json"),
        ("events/catalog.json", "events-2026-10-08.json"),
        ("sequences/exodus.json", "exodus-sequence-2026-10-12.json"),
    ]:
        require_approval(
            Bundle.model_validate(read(ROOT / "sources" / source)), ROOT / "review" / review
        )


@pytest.mark.parametrize("mutation", ["language", "evidence", "source", "outline", "verse"])
def test_invalid_or_incomplete_studies_are_rejected(mutation):
    value = read(CATALOG)
    p = value["chapterStudies"][0]["presentations"]
    if mutation == "language":
        del p["pt-BR"]
    elif mutation == "evidence":
        p["pt-BR"]["summary"]["references"].pop()
    else:
        for lang in p:
            if mutation == "source":
                p[lang]["sourceReferenceIds"].pop()
            elif mutation == "outline":
                p[lang]["outline"][0]["reference"]["verseStart"] = 2
            elif mutation == "verse":
                p[lang]["connections"][0]["reference"]["verseEnd"] = 999
    with pytest.raises(ValueError):
        validate_references(Bundle.model_validate(value))


def test_contract_examples_are_exact_editorial_presentations():
    bundle = build()
    study = bundle.chapterStudies[0]
    for language, suffix in [("en", "en"), ("pt-BR", "pt")]:
        example = read(ROOT.parent / f"api/examples/context/Exod.2.{suffix}.json")
        assert example["study"] == study.presentations[language].model_dump()
        assert {s["id"] for s in example["sources"]} == set(example["study"]["sourceReferenceIds"])


def test_publication_preserves_other_modules_is_idempotent_and_guards_updates(
    database, artifacts, tmp_path
):
    approve_publish(artifacts[0], tmp_path, "fixtures", database, True)
    with psycopg.connect(database) as conn:
        before = conn.execute("SELECT id,name,summary FROM entities ORDER BY id").fetchall()
        timeline = conn.execute(
            "SELECT id,title,summary FROM timeline_events ORDER BY id"
        ).fetchall()
    result, decisions = approve_publish(CATALOG, tmp_path, "studies", database)
    assert result["status"] == "published"
    assert publish(CATALOG, decisions, database)["status"] == "already_published"
    with psycopg.connect(database) as conn:
        assert conn.execute("SELECT count(*) FROM chapter_studies").fetchone()[0] == 21
        assert conn.execute("SELECT id,name,summary FROM entities ORDER BY id").fetchall() == before
        assert (
            conn.execute("SELECT id,title,summary FROM timeline_events ORDER BY id").fetchall()
            == timeline
        )
        assert conn.execute("SELECT count(*) FROM daily_verse_pool").fetchone()[0] == 101
        original = conn.execute(
            "SELECT presentations FROM chapter_studies WHERE book_id='Exod' AND chapter=2"
        ).fetchone()[0]
    changed = read(CATALOG)
    changed["chapterStudies"][0]["presentations"]["en"]["title"] += " (test revision)"
    source = tmp_path / "stale.json"
    write(source, changed)
    with pytest.raises(ValueError, match="changed since review"):
        approve_publish(source, tmp_path, "stale", database)
    changed["chapterStudies"][0]["previousHash"] = digest(original)
    source = tmp_path / "refreshed.json"
    write(source, changed)
    assert approve_publish(source, tmp_path, "refresh", database)[0]["status"] == "published"


def test_late_failure_rolls_back_sources_studies_and_receipt(database, tmp_path):
    with psycopg.connect(database) as conn:
        conn.execute("ALTER TABLE chapter_studies ADD CONSTRAINT fail_test CHECK (chapter<>34)")
    with pytest.raises(psycopg.errors.CheckViolation):
        approve_publish(CATALOG, tmp_path, "failed", database)
    with psycopg.connect(database) as conn:
        for table in (
            "sources",
            "chapter_studies",
            "chapter_study_sources",
            "content_publications",
        ):
            assert conn.execute(f"SELECT count(*) FROM {table}").fetchone()[0] == 0


def test_all_published_chapters_through_go_api_in_both_languages(database, artifacts, tmp_path):
    binary = os.environ.get("VERBUM_TEST_API_BINARY")
    if not binary:
        pytest.skip("VERBUM_TEST_API_BINARY is required for publication -> HTTP test")
    approve_publish(artifacts[0], tmp_path, "fixtures", database, True)
    approve_publish(CATALOG, tmp_path, "studies", database)
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        port = sock.getsockname()[1]
    env = {**os.environ, "VERBUM_DATABASE_URL": database, "VERBUM_ADDR": f"127.0.0.1:{port}"}
    process = subprocess.Popen(
        [binary], env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL
    )
    base = f"http://127.0.0.1:{port}"
    try:
        for _ in range(100):
            try:
                with urllib.request.urlopen(base + "/healthz", timeout=1):
                    break
            except urllib.error.URLError:
                if process.poll() is not None:
                    pytest.fail("API startup failed")
                time.sleep(0.05)
        else:
            pytest.fail("API startup timed out")
        for study in build().chapterStudies:
            for language, parameter in [("en", "en"), ("pt-BR", "pt")]:
                ref = study.reference
                route = f"/v1/passages/{ref.bookId}.{ref.chapter}/context?lang={parameter}&verse=1"
                with urllib.request.urlopen(base + route, timeout=5) as response:
                    page = json.load(response)
                assert page["study"] == study.presentations[language].model_dump()
                assert set(page["study"]["sourceReferenceIds"]) <= {
                    s["id"] for s in page["sources"]
                }
                assert page["reference"] == {
                    "bookId": study.reference.bookId,
                    "chapter": study.reference.chapter,
                }
        with pytest.raises(urllib.error.HTTPError) as err:
            urllib.request.urlopen(base + "/v1/passages/Neh.9/context", timeout=5)
        assert err.value.code == 404
    finally:
        process.terminate()
        try:
            process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=5)
