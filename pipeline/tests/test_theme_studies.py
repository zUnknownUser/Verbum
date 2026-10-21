import copy
import json
import os
import socket
import subprocess
import time
import urllib.error
import urllib.request

import psycopg
import pytest

from verbum_pipeline.files import digest, read, write
from verbum_pipeline.models import Bundle
from verbum_pipeline.publish import publish
from verbum_pipeline.review import require_approval
from verbum_pipeline.theme_studies import AUTHOR, FOLDER, ROOT, THEMES, build, validate_references

from .test_entity_profiles import seed_existing_identities
from .test_events import approve_publish

CATALOG = FOLDER / "catalog.json"


def seed_catalogues(database, tmp_path):
    seed_existing_identities(database)
    approve_publish(ROOT / "sources/themes/catalog.json", tmp_path, "themes", database)
    approve_publish(ROOT / "sources/entity-profiles/exodus.json", tmp_path, "profiles", database)
    # Use the canonical event catalogue for existing Exodus events and the two
    # covenant comparison events outside that narrative sequence.
    events = read(ROOT / "sources/events/catalog.json")
    with psycopg.connect(database) as conn:
        conn.execute("INSERT INTO sources(id,citation) VALUES('test.events','Event fixture')")
        connected_ids = set(build().content.referencedEntityIds)
        for entity in events["content"]["entities"]:
            if entity["id"] in connected_ids:
                conn.execute(
                    "INSERT INTO entities(id,type,name,summary) VALUES(%s,'event',%s,%s) "
                    "ON CONFLICT(id) DO NOTHING",
                    (entity["id"], entity["name"], entity["summary"]),
                )
            conn.execute(
                "UPDATE entities SET name=%s,summary=%s WHERE id=%s",
                (entity["name"], entity["summary"], entity["id"]),
            )
        for p in events["eventCatalog"]["presentations"]:
            conn.execute(
                """INSERT INTO entity_localizations
                (entity_id,language,source_id,name,description,aliases)
                SELECT id,%s,'test.events',%s,%s,%s FROM entities WHERE id=%s""",
                (p["language"], p["name"], p["summary"], p["aliases"], p["entityId"]),
            )


def test_exactly_five_bilingual_studies_preserve_catalogue_and_review_hashes():
    bundle = Bundle.model_validate(read(CATALOG))
    assert bundle == build()
    validate_references(bundle)
    assert {s.entityId for s in bundle.themeStudies} == THEMES
    assert sum(len(s.presentations["en"].readings) for s in bundle.themeStudies) == 25
    assert sum(len(s.presentations["en"].comparisons) for s in bundle.themeStudies) == 10
    assert not bundle.content.entities and not bundle.content.details
    require_approval(bundle, ROOT / "review/theme-studies-2026-10-21.json")
    for path, review in [
        ("themes/catalog.json", "themes-2026-10-08.json"),
        ("events/catalog.json", "events-2026-10-08.json"),
        ("sequences/exodus.json", "exodus-sequence-2026-10-12.json"),
        ("chapter-studies/exodus.json", "chapter-studies-2026-10-21.json"),
        ("entity-profiles/exodus.json", "entity-profiles-2026-10-21.json"),
    ]:
        require_approval(
            Bundle.model_validate(read(ROOT / "sources" / path)), ROOT / "review" / review
        )


@pytest.mark.parametrize(
    "mutation",
    [
        "language",
        "evidence",
        "verse",
        "source",
        "identity",
        "connection",
        "legacy",
        "reading-id",
    ],
)
def test_invalid_or_incomplete_guides_are_rejected(mutation):
    value = read(CATALOG)
    study = value["themeStudies"][0]
    if mutation == "language":
        del study["presentations"]["pt-BR"]
    elif mutation == "identity":
        study["entityId"] = "editorial.theme.unreviewed"
    elif mutation == "evidence":
        study["presentations"]["pt-BR"]["overview"]["references"][0]["chapter"] = 99
    elif mutation == "connection":
        value["content"]["relationships"].pop()
    else:
        for p in study["presentations"].values():
            if mutation == "verse":
                p["readings"][0]["reference"]["verseEnd"] = 999
            elif mutation == "source":
                p["sourceReferenceIds"].pop()
            elif mutation == "legacy":
                p["readings"].pop()
            else:
                p["readings"][1]["id"] = p["readings"][0]["id"]
    with pytest.raises(ValueError):
        validate_references(Bundle.model_validate(value))


def snapshot(conn):
    return {
        table: conn.execute(f"SELECT * FROM {table} ORDER BY 1,2").fetchall()
        for table in (
            "entities",
            "entity_key_passages",
            "theme_categories",
            "entity_profiles",
            "entity_source_records",
            "timeline_events",
        )
    }


def test_publication_is_idempotent_preserves_content_and_requires_exact_revision(
    database, tmp_path
):
    seed_catalogues(database, tmp_path)
    with psycopg.connect(database) as conn:
        before = snapshot(conn)
        links = conn.execute("SELECT * FROM relationships ORDER BY id").fetchall()
    result, decisions = approve_publish(CATALOG, tmp_path, "guides", database)
    assert result["status"] == "published"
    assert publish(CATALOG, decisions, database)["status"] == "already_published"
    with psycopg.connect(database) as conn:
        assert snapshot(conn) == before
        assert conn.execute("SELECT count(*) FROM theme_studies").fetchone()[0] == 5
        assert conn.execute("SELECT count(*) FROM theme_study_passages").fetchone()[0] == 25
        after = conn.execute("SELECT * FROM relationships ORDER BY id").fetchall()
        assert all(edge in after for edge in links)
    value = read(CATALOG)
    first = value["themeStudies"][0]
    previous = copy.deepcopy(first["presentations"])
    first["presentations"]["en"]["overview"]["text"] += " Test revision."
    stale = tmp_path / "stale.json"
    write(stale, value)
    with pytest.raises(ValueError, match="changed since review"):
        approve_publish(stale, tmp_path, "stale", database)
    first["previousHash"] = digest(previous)
    updated = tmp_path / "updated.json"
    write(updated, value)
    assert approve_publish(updated, tmp_path, "updated", database)[0]["status"] == "published"

    # A reviewed prose revision is supported. Replacing connection targets is not:
    # reject it instead of silently retaining an obsolete edge in the graph.
    topology = read(updated)
    changed = topology["themeStudies"][0]
    changed["previousHash"] = digest(first["presentations"])
    for p in changed["presentations"].values():
        p["connections"][0]["entityId"] = "step.person.H0175"
    for edge in topology["content"]["relationships"]:
        if edge["targetId"] == changed["entityId"] and edge["sourceId"] == "fixture.person.moses":
            edge["sourceId"] = "step.person.H0175"
            edge["id"] = "editorial.theme-study.step.person.H0175.fixture.theme.faith"
    rejected = tmp_path / "changed-topology.json"
    write(rejected, topology)
    with pytest.raises(ValueError, match="connection identities cannot change"):
        approve_publish(rejected, tmp_path, "topology", database)
    with psycopg.connect(database) as conn:
        assert conn.execute("SELECT * FROM relationships ORDER BY id").fetchall() == after
        assert (
            conn.execute(
                "SELECT presentations FROM theme_studies WHERE entity_id=%s", (first["entityId"],)
            ).fetchone()[0]
            == first["presentations"]
        )


def test_identity_mismatch_and_late_failure_rollback_everything(database, tmp_path):
    seed_catalogues(database, tmp_path)
    with psycopg.connect(database) as conn:
        conn.execute("UPDATE entities SET type='event' WHERE id='fixture.theme.faith'")
    with pytest.raises(ValueError, match="identity missing or incompatible"):
        approve_publish(CATALOG, tmp_path, "identity", database)
    with psycopg.connect(database) as conn:
        assert conn.execute("SELECT count(*) FROM theme_studies").fetchone()[0] == 0
        assert not conn.execute("SELECT 1 FROM sources WHERE id=%s", (AUTHOR,)).fetchone()
        conn.execute("UPDATE entities SET type='theme' WHERE id='fixture.theme.faith'")
        conn.execute(
            "ALTER TABLE theme_studies ADD CONSTRAINT fail_test "
            "CHECK (entity_id<>'editorial.theme.holiness')"
        )
        receipts = conn.execute("SELECT count(*) FROM content_publications").fetchone()[0]
    with pytest.raises(psycopg.errors.CheckViolation):
        approve_publish(CATALOG, tmp_path, "failure", database)
    with psycopg.connect(database) as conn:
        for table in ("theme_studies", "theme_study_sources", "theme_study_passages"):
            assert conn.execute(f"SELECT count(*) FROM {table}").fetchone()[0] == 0
        assert not conn.execute("SELECT 1 FROM sources WHERE id=%s", (AUTHOR,)).fetchone()
        assert conn.execute("SELECT count(*) FROM content_publications").fetchone()[0] == receipts


def test_every_published_guide_is_navigable_and_localized_through_real_api(database, tmp_path):
    binary = os.environ.get("VERBUM_TEST_API_BINARY")
    if not binary:
        pytest.skip("VERBUM_TEST_API_BINARY required for publication -> HTTP")
    seed_catalogues(database, tmp_path)
    approve_publish(CATALOG, tmp_path, "guides", database)
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        port = sock.getsockname()[1]
    process = subprocess.Popen(
        [binary],
        env={**os.environ, "VERBUM_DATABASE_URL": database, "VERBUM_ADDR": f"127.0.0.1:{port}"},
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    base = f"http://127.0.0.1:{port}"

    def get(path):
        with urllib.request.urlopen(base + path, timeout=10) as response:
            return json.load(response)

    try:
        for _ in range(100):
            try:
                with urllib.request.urlopen(base + "/healthz", timeout=1):
                    break
            except urllib.error.URLError:
                time.sleep(0.05)
        else:
            pytest.fail("API did not start")
        for study in build().themeStudies:
            for language, suffix in [("en", "en"), ("pt-BR", "pt")]:
                page = get(f"/v1/entities/{study.entityId}?lang={suffix}")
                p = study.presentations[language]
                guide = page["themeStudy"]
                expected = p.model_dump(exclude_none=True)
                assert {k: v for k, v in guide.items() if k != "connections"} == {
                    k: v for k, v in expected.items() if k != "connections"
                }
                assert page["keyPassages"] == [
                    r.reference.model_dump(exclude_none=True) for r in p.readings
                ]
                assert len(guide["connections"]) == len(p.connections)
                assert set(guide["sourceReferenceIds"]) <= {s["id"] for s in page["sources"]}
                for connection, proposed in zip(guide["connections"], p.connections, strict=True):
                    entity = get(f"/v1/entities/{proposed.entityId}?lang={suffix}")["entity"]
                    assert connection["entity"] == entity
                    assert connection["explanation"] == proposed.explanation
                    assert connection["reference"] == proposed.reference.model_dump(
                        exclude_none=True
                    )
                    assert entity.get("nameLanguage") == language
                for reading in p.readings:
                    ref = reading.reference
                    context = get(f"/v1/passages/{ref.bookId}.{ref.chapter}/context?lang={suffix}")
                    assert study.entityId in {e["id"] for e in context["entities"]}
                    assert set(guide["sourceReferenceIds"]) <= {s["id"] for s in context["sources"]}
                graph = get(f"/v1/entities/{study.entityId}/graph?lang={suffix}&limit=48")
                assert {c.entityId for c in p.connections} <= {n["id"] for n in graph["nodes"]}
                if study.entityId == "fixture.theme.faith":
                    example = read(ROOT.parent / f"api/examples/entities/faith-study.{suffix}.json")
                    assert page["themeStudy"] == example["themeStudy"]
                    assert page["entity"] == example["entity"]
        # A newer API preserves old details for themes outside this edition.
        assert "themeStudy" not in get("/v1/entities/editorial.theme.hope?lang=pt")
    finally:
        process.terminate()
        process.wait(timeout=10)
