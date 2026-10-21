import json
import os
import socket
import subprocess
import time
import urllib.error
import urllib.request

import psycopg
import pytest
from psycopg.types.json import Jsonb

from verbum_pipeline.entity_profiles import AUTHOR, FOLDER, build, profile_hash, validate_references
from verbum_pipeline.files import read, write
from verbum_pipeline.models import Bundle
from verbum_pipeline.publish import publish
from verbum_pipeline.review import require_approval

from .conftest import ROOT
from .test_events import approve_publish

CATALOG = FOLDER / "exodus.json"


def seed_existing_identities(database):
    """A minimal isolated import of real, pinned identities; no private production data."""
    bundle = build()
    manifest = read(ROOT / "pipeline/sources/step/manifest.json")
    dataset = manifest["datasets"]["TIPNR"]
    with psycopg.connect(database) as conn:
        conn.execute("INSERT INTO sources(id,citation) "
                     "VALUES('step.TIPNR','STEP TIPNR · CC BY 4.0')")
        conn.execute("""INSERT INTO source_datasets(source_id,repository,revision,path,sha256,
            license_url,attribution,modifications) VALUES('step.TIPNR',%s,%s,%s,%s,%s,%s,%s)""",
                     (manifest["repository"], manifest["revision"], dataset["path"],
                      dataset["sha256"], "https://creativecommons.org/licenses/by/4.0/",
                      "STEP Bible / Tyndale House, Cambridge", "Selected identity fields"))
        for profile in bundle.entityProfiles:
            conn.execute("INSERT INTO entities(id,type,name,summary) VALUES(%s,%s,%s,'original')",
                         (profile.entityId, profile.type, profile.presentations["en"].name))
            conn.execute("""INSERT INTO entity_source_records
                (id,entity_id,source_id,revision,external_id,source_line,identifiers)
                VALUES(%s,%s,'step.TIPNR',%s,%s,1,%s)""",
                         (profile.recordId, profile.entityId, manifest["revision"],
                          profile.externalId, Jsonb({"uniqueName": [profile.uniqueName]})))
        profiles = {p.entityId for p in bundle.entityProfiles}
        for entity_id in set(bundle.content.referencedEntityIds) - profiles:
            conn.execute("INSERT INTO entities(id,type,name) VALUES(%s,'event',%s)",
                         (entity_id, entity_id))


def test_bounded_bilingual_profiles_have_real_evidence_and_no_new_entities():
    bundle = Bundle.model_validate(read(CATALOG))
    assert bundle == build()
    validate_references(bundle)
    assert sum(p.type == "person" for p in bundle.entityProfiles) == 15
    assert sum(p.type == "place" for p in bundle.entityProfiles) == 15
    assert not bundle.content.entities and not bundle.content.timeline
    chapters = read(ROOT / "pipeline/sources/chapter-studies/exodus.json")
    scope = {(s["reference"]["bookId"], s["reference"]["chapter"])
             for s in chapters["chapterStudies"]}
    for profile in bundle.entityProfiles:
        assert any((r.bookId, r.chapter) in scope for r in profile.keyPassages)
        assert all("\n\n" in p.summary for p in profile.presentations.values())
        assert all(p.modernGeography is None for p in profile.presentations.values())
    for kind in ("people", "places"):
        rows = json.loads((FOLDER / f"{kind}-notes.json").read_text())
        assert all(p["references"] for r in rows for p in r["paragraphs"])
    require_approval(bundle, ROOT / "pipeline/review/entity-profiles-2026-10-21.json")
    require_approval(Bundle.model_validate(chapters),
                     ROOT / "pipeline/review/chapter-studies-2026-10-21.json")


@pytest.mark.parametrize("mutation", ["language", "source", "verse", "duplicate", "type"])
def test_incomplete_or_invalid_profile_batches_are_rejected(mutation):
    value = read(CATALOG)
    profile = value["entityProfiles"][0]
    if mutation == "language":
        del profile["presentations"]["pt-BR"]
    elif mutation == "source":
        profile["sourceReferenceIds"].pop()
    elif mutation == "verse":
        profile["keyPassages"][0]["verseEnd"] = 999
    elif mutation == "type":
        profile["type"] = "event"
    else:
        value["entityProfiles"].append(profile)
    with pytest.raises(ValueError):
        validate_references(Bundle.model_validate(value))


def test_publication_preserves_canonical_records_and_existing_connections(database, tmp_path):
    seed_existing_identities(database)
    bundle = build()
    with psycopg.connect(database) as conn:
        before = conn.execute("SELECT * FROM entities ORDER BY id").fetchall()
        records = conn.execute("SELECT * FROM entity_source_records ORDER BY id").fetchall()
        conn.execute("""INSERT INTO entity_key_passages VALUES
            ('fixture.person.moses',0,'Exod',2,1,25),
            ('fixture.person.moses',1,'Heb',11,23,29)""")
        edge = bundle.content.relationships[0]
        conn.execute("""INSERT INTO relationships
            (id,source_entity_id,target_entity_id,relationship_type)
            VALUES('existing.edge',%s,%s,%s)""", (edge.targetId, edge.sourceId, edge.type))
        conn.execute("INSERT INTO relationship_sources VALUES('existing.edge','step.TIPNR',0)")
    result, decisions = approve_publish(CATALOG, tmp_path, "profiles", database)
    assert result["status"] == "published"
    assert publish(CATALOG, decisions, database)["status"] == "already_published"
    with psycopg.connect(database) as conn:
        assert conn.execute("SELECT * FROM entities ORDER BY id").fetchall() == before
        assert conn.execute("SELECT * FROM entity_source_records ORDER BY id").fetchall() == records
        assert conn.execute("SELECT count(*) FROM entity_profiles").fetchone()[0] == 30
        assert conn.execute("SELECT count(*) FROM relationships").fetchone()[0] == 71
        assert conn.execute("SELECT count(*) FROM entity_key_passages").fetchone()[0] == 2
        assert conn.execute("SELECT source_id FROM relationship_sources WHERE relationship_id="
                            "'existing.edge'").fetchone()[0] == "step.TIPNR"
    changed = read(CATALOG)
    changed["entityProfiles"][0]["presentations"]["en"]["summary"] += " Revision."
    stale = tmp_path / "stale.json"
    write(stale, changed)
    with pytest.raises(ValueError, match="changed since review"):
        approve_publish(stale, tmp_path, "stale", database)
    changed["entityProfiles"][0]["previousHash"] = profile_hash(bundle.entityProfiles[0])
    fresh = tmp_path / "fresh.json"
    write(fresh, changed)
    assert approve_publish(fresh, tmp_path, "fresh", database)[0]["status"] == "published"


def test_wrong_homonym_or_missing_import_blocks_every_write(database, tmp_path):
    seed_existing_identities(database)
    with psycopg.connect(database) as conn:
        conn.execute("UPDATE entity_source_records SET identifiers=%s WHERE external_id='H3091G'",
                     (Jsonb({"uniqueName": ["Joshua@1Sa.6.14-"]}),))
    with pytest.raises(ValueError, match="identity missing or changed"):
        approve_publish(CATALOG, tmp_path, "wrong-identity", database)
    with psycopg.connect(database) as conn:
        assert conn.execute("SELECT count(*) FROM entity_profiles").fetchone()[0] == 0
        assert conn.execute("SELECT count(*) FROM content_publications").fetchone()[0] == 0
        assert not conn.execute("SELECT 1 FROM sources WHERE id=%s", (AUTHOR,)).fetchone()


def test_late_failure_rolls_back_sources_profiles_links_and_receipt(database, tmp_path):
    seed_existing_identities(database)
    with psycopg.connect(database) as conn:
        conn.execute("ALTER TABLE entity_profiles ADD CONSTRAINT fail_test "
                     "CHECK(entity_id<>'step.place.H1316')")
    with pytest.raises(psycopg.errors.CheckViolation):
        approve_publish(CATALOG, tmp_path, "failed", database)
    with psycopg.connect(database) as conn:
        for table in ("entity_profiles", "entity_profile_sources", "entity_profile_passages",
                      "relationships", "content_publications"):
            assert conn.execute(f"SELECT count(*) FROM {table}").fetchone()[0] == 0
        assert conn.execute("SELECT count(*) FROM sources").fetchone()[0] == 1


def test_all_profiles_and_mobile_examples_through_live_api(database, tmp_path):
    binary = os.environ.get("VERBUM_TEST_API_BINARY")
    if not binary:
        pytest.skip("VERBUM_TEST_API_BINARY required for publication -> HTTP test")
    seed_existing_identities(database)
    approve_publish(CATALOG, tmp_path, "profiles", database)
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        port = sock.getsockname()[1]
    process = subprocess.Popen([binary], env={**os.environ, "VERBUM_DATABASE_URL": database,
        "VERBUM_ADDR": f"127.0.0.1:{port}"}, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
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
        for profile in build().entityProfiles:
            for language, suffix in [("en", "en"), ("pt-BR", "pt")]:
                page = get(f"/v1/entities/{profile.entityId}?lang={suffix}")
                text = profile.presentations[language]
                assert page["entity"]["name"] == text.name
                assert page["entity"]["summary"] == text.summary
                assert page["role"] == text.role
                assert page["approximateDates"] is None
                assert page["modernGeography"] is None
                assert page["aliases"] == text.aliases
                assert page["keyPassages"] == [r.model_dump(exclude_none=True)
                                               for r in profile.keyPassages]
                assert set(profile.sourceReferenceIds) <= {s["id"] for s in page["sources"]}
                first = profile.keyPassages[0]
                context = get(f"/v1/passages/{first.bookId}.{first.chapter}/context?lang={suffix}")
                assert profile.entityId in {e["id"] for e in context["entities"]}
                assert set(profile.sourceReferenceIds) <= {s["id"] for s in context["sources"]}
                graph = get(f"/v1/entities/{profile.entityId}/graph?lang={suffix}&limit=48")
                assert graph["root"]["name"] == text.name
                assert graph["nodes"]
                assert len({n["id"] for n in graph["nodes"]}) == len(graph["nodes"])
                assert all(e["sourceReferenceIds"] for e in graph["edges"])
                if profile.externalId in ("H0175", "H5015H"):
                    slug = "aaron" if profile.externalId == "H0175" else "nebo"
                    example = read(ROOT / f"api/examples/entities/{slug}.{suffix}.json")
                    assert {key: page[key] for key in example} == example
        with psycopg.connect(database) as conn:
            conn.execute("""INSERT INTO entity_localizations
                (entity_id,language,source_id,name,aliases) VALUES
                ('step.place.H5514G','pt-BR','step.TIPNR','Sinai',ARRAY['Nome anterior'])""")
        # New disambiguated names and aliases remain searchable in both catalogues.
        for kind, query, expected in [("person", "Oseias", "step.person.H3091G"),
                                      ("place", "Horebe", "step.place.H5514G"),
                                      ("place", "anterior", "step.place.H5514G")]:
            page = get(f"/v1/entities?type={kind}&lang=pt&q={query}&limit=30&offset=0")
            assert expected in {e["id"] for e in page["entities"]}
        # Existing readings stay accessible; exact duplicates are displayed once.
        with psycopg.connect(database) as conn:
            conn.execute("""INSERT INTO entity_key_passages VALUES
                ('fixture.person.moses',0,'Exod',2,1,25),
                ('fixture.person.moses',1,'Heb',11,23,29)""")
        page = get("/v1/entities/fixture.person.moses?lang=pt")
        assert page["keyPassages"].count(dict(bookId="Exod", chapter=2,
                                            verseStart=1, verseEnd=25)) == 1
        assert page["keyPassages"][-1] == dict(bookId="Heb", chapter=11, verseStart=23, verseEnd=29)
    finally:
        process.terminate()
        process.wait(timeout=10)
