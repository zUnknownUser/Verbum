import json
import os
import socket
import subprocess
import time
import urllib.error
import urllib.request

import psycopg
import pytest

from verbum_pipeline.files import read, write
from verbum_pipeline.publish import publish
from verbum_pipeline.review import prepare

from .conftest import ROOT, approve_for_test

pytestmark = pytest.mark.postgres


def test_atomic_publication_idempotence_and_update(database, artifacts, tmp_path):
    source, _, decisions = artifacts
    approved = approve_for_test(decisions, tmp_path / "approved.json")
    assert publish(source, approved, database, allow_fixtures=True)["status"] == "published"
    assert publish(source, approved, database, allow_fixtures=True)["status"] == "already_published"
    raw = read(source)
    raw["content"]["timeline"][0]["title"] += " (test revision)"
    revision = tmp_path / "revision.json"
    write(revision, raw)
    prepare(revision, tmp_path / "queue2.json", tmp_path / "decisions2.json")
    approved2 = approve_for_test(tmp_path / "decisions2.json", tmp_path / "approved2.json")
    assert publish(revision, approved2, database, allow_fixtures=True)["status"] == "published"
    with psycopg.connect(database) as conn:
        assert conn.execute("SELECT count(*) FROM content_publications").fetchone()[0] == 2
        assert conn.execute("SELECT count(*) FROM entities").fetchone()[0] == len(
            raw["content"]["entities"]
        )
        assert (
            conn.execute(
                "SELECT title FROM timeline_events WHERE id=%s",
                (raw["content"]["timeline"][0]["id"],),
            )
            .fetchone()[0]
            .endswith("(test revision)")
        )


def test_late_database_failure_rolls_back(database, artifacts, tmp_path):
    source, _, decisions = artifacts
    approved = approve_for_test(decisions, tmp_path / "approved.json")
    with psycopg.connect(database) as conn:
        conn.execute("ALTER TABLE timeline_events ADD CONSTRAINT fail_test CHECK (position<0)")
    with pytest.raises(psycopg.errors.CheckViolation):
        publish(source, approved, database, allow_fixtures=True)
    with psycopg.connect(database) as conn:
        assert conn.execute("SELECT count(*) FROM entities").fetchone()[0] == 0
        assert conn.execute("SELECT count(*) FROM sources").fetchone()[0] == 0
        assert conn.execute("SELECT count(*) FROM content_publications").fetchone()[0] == 0


def test_fixture_requires_explicit_flag(artifacts, tmp_path):
    source, _, decisions = artifacts
    approved = approve_for_test(decisions, tmp_path / "approved.json")
    with pytest.raises(ValueError, match="allow-fixtures"):
        publish(source, approved, "unused")


def test_python_publication_matches_go_http_contract(database, artifacts, tmp_path):
    binary = os.environ.get("VERBUM_TEST_API_BINARY")
    if not binary:
        pytest.skip("VERBUM_TEST_API_BINARY is required for Python -> Go HTTP contract test")
    source, _, decisions = artifacts
    approved = approve_for_test(decisions, tmp_path / "approved.json")
    publish(source, approved, database, allow_fixtures=True)
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
                    pytest.fail("Go API exited during startup")
                time.sleep(0.05)
        else:
            pytest.fail("Go API startup timeout")
        cases = {
            "/v1/entities/fixture.person.david": "entities/david.json",
            "/v1/entities?type=person": "entities/people.json",
            "/v1/entities/fixture.person.david/graph?limit=24": "graph/david.json",
            "/v1/passages/1Sam.17/context": "context/1Sam.17.json",
            "/v1/timeline": "timeline/all.json",
            "/v1/search?q=David": "search/david.json",
            "/v1/daily-verse?from=2026-09-13&days=7": "daily-verse/week.json",
        }
        for route, example in cases.items():
            with urllib.request.urlopen(base + route, timeout=5) as response:
                assert json.load(response) == read(ROOT / "api/examples" / example)
        # New editorial entities without a curated detail page still expose their
        # actual evidence, never the legacy fixture editorial fallback.
        editorial = tmp_path / "editorial.json"
        write(
            editorial,
            {
                "version": 1,
                "kind": "editorial",
                "content": {
                    "sources": [
                        {
                            "id": "test.source",
                            "citation": "Synthetic integration source",
                            "url": None,
                        }
                    ],
                    "entities": [
                        {
                            "id": "test.entity",
                            "type": "person",
                            "name": "Test entity",
                            "summary": "Synthetic integration claim",
                        }
                    ],
                    "relationships": [],
                    "details": [],
                    "timeline": [],
                    "dailyVersePool": [],
                },
                "provenance": {
                    "test.source": {
                        "sourceId": "test.document",
                        "license": "test only",
                        "page": "1",
                        "section": "test",
                    }
                },
                "entitySources": {"test.entity": ["test.source"]},
            },
        )
        editorial_decisions = tmp_path / "editorial-decisions.json"
        prepare(editorial, tmp_path / "editorial-queue.json", editorial_decisions)
        approved_editorial = approve_for_test(editorial_decisions, tmp_path / "editorial-ok.json")
        publish(editorial, approved_editorial, database)
        with urllib.request.urlopen(base + "/v1/entities/test.entity", timeout=5) as response:
            detail = json.load(response)
            assert [s["id"] for s in detail["sources"]] == ["test.source"]
        with psycopg.connect(database) as conn:
            assert conn.execute(
                "SELECT license,page,section FROM source_provenance "
                "WHERE reference_id='test.source'"
            ).fetchone() == ("test only", "1", "test")
            assert conn.execute("SELECT count(*) FROM daily_verse_pool").fetchone()[0] == 101
    finally:
        process.terminate()
        try:
            process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=5)
