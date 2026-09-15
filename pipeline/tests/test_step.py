"""Synthetic miniature STEP layouts, with no copied restricted definitions."""

import copy
import hashlib
import json

import psycopg
import pytest
from pydantic import ValidationError

from verbum_pipeline.files import digest, read, write
from verbum_pipeline.models import Bundle
from verbum_pipeline.publish import publish
from verbum_pipeline.review import items, prepare
from verbum_pipeline.step import OFFICIAL, build, lexicon, refs

from .conftest import approve_for_test


@pytest.fixture
def step_input(tmp_path):
    texts = {
        "TBESH": "eStrong#\tdStrong\tuStrong\tHebrew\tTransliteration\tMorph\tGloss\tMeaning\n"
        "H1732\tH1732 =\tH1732\tדָּוִד\tdawid\tN:N-M-P\tDavid\tRESTRICTED_MARKER\n"
        "H0002\tH0002 = in Aramaic of\tH0001G\tאַב\tav\tA:N-M\tfather\tRESTRICTED_MARKER\n",
        "TBESG": "eStrong\tdStrong\tuStrong\tGreek\tTransliteration\tMorph\tGloss\tMeaning\n"
        "G1138\tG1138 = the Greek of\tH1732\tΔαυίδ\tDauid\tN:N-M-P\tDavid\tOMITTED_MARKER\n"
        "G0001\tG0001G =\tG0001G\tα\talpha\tG:N-LI\talpha\tOMITTED_MARKER\n"
        "G0001\tG0001H =\tG0001H\tἆ\ta\tG:INJ\tah!\tOMITTED_MARKER\n",
        "TIPNR": "$========== PERSON(s)\n"
        "David@Rut.4.17-Rev=H1732\tOMITTED\t\t\t\t\t\tAI_MARKER\tMale\n"
        "– Named\tDavid@Rut.4.17-Rev\tH1732«H1732=דָּוִד\tDavid\t1Sa.17.49a; 1Sa.17.49b; 1Sa.99.1\n"
        "– Greek\tDavid@Rut.4.17-Rev\tG1138«G1138=Δαυίδ\tDavid\tMat.1.1\n"
        "– (same form with Variant)\tDavid@Rut.4.17-Rev\tH1732«H1732=דָּוִד\tDavid\t1Sa.17.50\n"
        "@Article= AI_MARKER\n"
        "$========== PERSON(s)\n"
        "David@Gen.1.1=H1732Z\t\t\t\t\t\t\t\tMale\n"
        "– Named\tDavid@Gen.1.1\tH1732Z«H1732=דָּוִד\tDavid\tGen.1.1\n",
    }
    manifest = {"version": 1, "repository": OFFICIAL, "revision": "a" * 40, "datasets": {}}
    for code, text in texts.items():
        p = tmp_path / (code + ".tsv")
        p.write_text(text, encoding="utf-8")
        manifest["datasets"][code] = {
            "path": p.name,
            "sha256": hashlib.sha256(p.read_bytes()).hexdigest(),
        }
    write(tmp_path / "manifest.json", manifest)
    write(
        tmp_path / "map.json",
        {
            "H1732": {
                "entityId": "fixture.person.david",
                "type": "person",
                "uniqueName": "David@Rut.4.17-Rev",
                "name": "David",
            }
        },
    )
    (tmp_path / "corpus.tsv").write_text(
        "1Sam\t17\t49\tSynthetic verse\nMatt\t1\t1\tSynthetic verse\nGen\t1\t1\tSynthetic verse\n"
    )
    return (tmp_path, tmp_path / "manifest.json", tmp_path / "map.json", tmp_path / "corpus.tsv")


def reviewed(bundle, directory, suffix=""):
    source, queue, decisions = [
        directory / ("step-" + name + suffix + ".json") for name in ("bundle", "queue", "decisions")
    ]
    write(source, bundle.model_dump())
    prepare(source, queue, decisions)
    return source, approve_for_test(decisions, directory / ("approved" + suffix + ".json"))


def test_normalization_ids_provenance_and_scope(step_input):
    bundle, report = build(*step_input)
    same, _ = build(*step_input)
    assert digest(bundle.model_dump()) == digest(same.model_dump())
    raw = json.dumps(bundle.model_dump(), ensure_ascii=False)
    assert "RESTRICTED_MARKER" not in raw and "AI_MARKER" not in raw
    assert "OMITTED_MARKER" not in raw
    by_id = {r.externalId: r for r in bundle.enrichment.records}
    assert by_id["G0001G"].entityId != by_id["G0001H"].entityId
    assert by_id["H1732Z"].entityId != by_id["H1732"].entityId
    assert by_id["H0002"].lexical.language == "arc"
    assert by_id["G1138"].lexical.original == "Δαυίδ"
    assert by_id["G1138"].lexical.unifiedStrong == "H1732"
    assert all(loc.language == "en" for r in bundle.enrichment.records for loc in r.localizations)
    named = next(r for r in bundle.enrichment.records if r.id == "step.TIPNR.H1732")
    assert named.entityId == "fixture.person.david"
    assert [o.locator for o in named.occurrences] == ["1Sa.17.49a", "1Sa.17.49b", "Mat.1.1"]
    assert named.occurrences[0].reference.bookId == "1Sam"
    assert len(bundle.content.relationships) == 2
    assert {x["reason"] for x in report["excluded"]} == {
        "absent_from_WEB_versification",
        "lexeme_not_in_selected_datasets",
    }
    assert any(i["key"] == "record:step.TIPNR.H1732" for i in items(bundle))
    assert all(d.revision == "a" * 40 for d in bundle.enrichment.datasets)


def test_hash_schema_and_identity_drift_fail(step_input):
    root, manifest, mapping, corpus = step_input
    original = (root / "TBESH.tsv").read_text()
    (root / "TBESH.tsv").write_text(original + "changed")
    with pytest.raises(ValueError, match="SHA-256 mismatch"):
        build(*step_input)
    with pytest.raises(ValueError, match="schema"):
        lexicon("H1732\tH1732 =\tH1732\tדוד\tdawid\tN\tDavid\tignored", "TBESH")
    (root / "TBESH.tsv").write_text(original)
    changed = read(mapping)
    changed["H1732"]["uniqueName"] = "A different David"
    mapping.write_text(json.dumps(changed))
    with pytest.raises(ValueError, match="identity changed"):
        build(root, manifest, mapping, corpus)


def test_reference_normalization_and_rejection():
    result, excluded = refs("Num.13.8,16; LXX.Est.3.1", {("Num", 13, 8), ("Num", 13, 16)})
    assert [o["reference"]["verseStart"] for o in result] == [8, 16]
    assert excluded[0]["reason"] == "unsupported_versification"
    for value in ("Nope.1.1", "Num.13.8ff", "16", "Gen.1.-1"):
        with pytest.raises(ValueError):
            refs(value, set())


def test_v1_hash_compatibility_and_v2_validation(artifacts, step_input):
    old = Bundle.model_validate(read(artifacts[0]))
    assert "enrichment" not in old.model_dump()
    assert digest(old.model_dump()) == read(artifacts[2])["bundleHash"]
    bundle, _ = build(*step_input)
    data = bundle.model_dump()
    data["enrichment"]["records"][0]["entityId"] = "unknown"
    with pytest.raises(ValidationError):
        Bundle.model_validate(data)
    data = bundle.model_dump()
    data["version"] = 1
    with pytest.raises(ValidationError):
        Bundle.model_validate(data)


@pytest.mark.postgres
def test_publish_preserves_editorial_data_and_other_languages(
    database, artifacts, step_input, tmp_path
):
    source, _, decisions = artifacts
    publish(
        source,
        approve_for_test(decisions, tmp_path / "fixture-ok.json"),
        database,
        allow_fixtures=True,
    )
    with psycopg.connect(database) as conn:
        before = conn.execute(
            "SELECT name,summary FROM entities WHERE id='fixture.person.david'"
        ).fetchone()
        conn.execute(
            "INSERT INTO entity_localizations VALUES"
            "('fixture.person.david','pt-BR','fixture.source.editorial',"
            "'Davi',ARRAY['Rei Davi'],NULL)"
        )
    bundle, _ = build(*step_input)
    source, approved = reviewed(bundle, tmp_path)
    assert publish(source, approved, database)["status"] == "published"
    assert publish(source, approved, database)["status"] == "already_published"
    with psycopg.connect(database) as conn:
        assert (
            conn.execute(
                "SELECT name,summary FROM entities WHERE id='fixture.person.david'"
            ).fetchone()
            == before
        )
        assert conn.execute("SELECT count(*) FROM entity_source_records").fetchone()[0] == 7
        assert conn.execute("SELECT count(*) FROM entity_occurrences").fetchone()[0] == 4
        assert (
            conn.execute(
                "SELECT count(*) FROM entity_passage_associations WHERE "
                "entity_id='fixture.person.david' AND book_id='1Sam' AND chapter=17 "
                "AND verse_start=49"
            ).fetchone()[0]
            == 1
        )
        assert (
            conn.execute("SELECT name FROM entity_localizations WHERE language='pt-BR'").fetchone()[
                0
            ]
            == "Davi"
        )
        assert (
            conn.execute(
                "SELECT count(*) FROM entity_key_passages WHERE entity_id='fixture.person.david'"
            ).fetchone()[0]
            > 0
        )
        assert (
            conn.execute("SELECT count(*) FROM relationships WHERE id LIKE 'step.%'").fetchone()[0]
            == 2
        )
        assert conn.execute("SELECT count(*) FROM daily_verse_pool").fetchone()[0] == 101
    # A new revision replaces this source's occurrences, keeps editorial and PT-BR content.
    updated = copy.deepcopy(bundle)
    for d in updated.enrichment.datasets:
        d.revision = "b" * 40
    named = next(r for r in updated.enrichment.records if r.id == "step.TIPNR.H1732")
    named.occurrences = named.occurrences[:1]
    omitted_edge = copy.deepcopy(updated.content.relationships[1])
    updated.content.relationships = updated.content.relationships[:1]
    changed_edge = updated.content.relationships[0]
    changed_edge.confidence = 0.5
    changed_edge.sourceReferenceIds = [d.sourceId for d in updated.enrichment.datasets][:2]
    source2, approved2 = reviewed(updated, tmp_path, "2")
    publish(source2, approved2, database)
    with psycopg.connect(database) as conn:
        assert conn.execute("SELECT count(*) FROM entity_occurrences").fetchone()[0] == 2
        assert conn.execute("SELECT count(*) FROM source_datasets").fetchone()[0] == 6
        assert (
            conn.execute(
                "SELECT confidence FROM relationships WHERE id=%s", (changed_edge.id,)
            ).fetchone()[0]
            == 0.5
        )
        assert [
            r[0]
            for r in conn.execute(
                "SELECT source_id FROM relationship_sources "
                "WHERE relationship_id=%s ORDER BY position",
                (changed_edge.id,),
            )
        ] == changed_edge.sourceReferenceIds
        assert [
            r[0]
            for r in conn.execute(
                "SELECT source_id FROM relationship_sources "
                "WHERE relationship_id=%s ORDER BY position",
                (omitted_edge.id,),
            )
        ] == omitted_edge.sourceReferenceIds
        assert conn.execute("SELECT count(*) FROM relationships").fetchone()[0] == 61
        assert (
            conn.execute("SELECT name FROM entity_localizations WHERE language='pt-BR'").fetchone()[
                0
            ]
            == "Davi"
        )


@pytest.mark.postgres
def test_failure_rolls_back_whole_enrichment(database, step_input, tmp_path):
    bundle, _ = build(*step_input)
    source, approved = reviewed(bundle, tmp_path)
    with psycopg.connect(database) as conn:
        conn.execute("ALTER TABLE entity_occurrences ADD CONSTRAINT fail_test CHECK(verse<0)")
    with pytest.raises(psycopg.errors.CheckViolation):
        publish(source, approved, database)
    with psycopg.connect(database) as conn:
        for table in (
            "sources",
            "entities",
            "source_datasets",
            "entity_source_records",
            "entity_localizations",
            "content_publications",
        ):
            assert conn.execute(f"SELECT count(*) FROM {table}").fetchone()[0] == 0


def test_pending_review_blocks_before_db(step_input, tmp_path):
    bundle, _ = build(*step_input)
    source = tmp_path / "bundle.json"
    write(source, bundle.model_dump())
    prepare(source, tmp_path / "queue.json", tmp_path / "decisions.json")
    with pytest.raises(ValueError, match="human approval missing"):
        publish(source, tmp_path / "decisions.json", "invalid-db-must-not-connect")


@pytest.mark.postgres
def test_full_official_snapshot(database, tmp_path):
    """Opt-in real-layout regression. Publishes only in the disposable test schema."""
    import os
    from pathlib import Path

    from .conftest import ROOT

    source_dir = os.environ.get("VERBUM_TEST_STEP_SOURCE_DIR")
    if not source_dir:
        pytest.skip("VERBUM_TEST_STEP_SOURCE_DIR enables the pinned full dataset test")
    bundle, report = build(
        Path(source_dir),
        ROOT / "pipeline/sources/step/manifest.json",
        ROOT / "pipeline/sources/step/entity-map.json",
        ROOT / "VerbumKit/Sources/Clients/Resources/web.tsv",
    )
    assert report["records"] == {"step.TBESH": 11682, "step.TBESG": 11034, "step.TIPNR": 4044}
    assert report["occurrences"] == 29763
    source, approved = reviewed(bundle, tmp_path)
    assert publish(source, approved, database)["status"] == "published"
    assert publish(source, approved, database)["status"] == "already_published"
    with psycopg.connect(database) as conn:
        assert conn.execute("SELECT count(*) FROM entity_source_records").fetchone()[0] == 26760
        assert conn.execute("SELECT count(*) FROM entity_occurrences").fetchone()[0] == 29763
        assert conn.execute("SELECT count(*) FROM relationships").fetchone()[0] == 4648
        assert (
            conn.execute(
                "SELECT count(*) FROM entity_passage_associations "
                "WHERE entity_id='fixture.person.david' AND book_id='1Sam' "
                "AND chapter=17 AND verse_start=49"
            ).fetchone()[0]
            == 1
        )


@pytest.mark.postgres
def test_existing_source_identity_and_file_revision_cannot_be_rebound(
    database, step_input, tmp_path
):
    bundle, _ = build(*step_input)
    source, approved = reviewed(bundle, tmp_path, "initial")
    publish(source, approved, database)
    changed = bundle.model_copy(deep=True)
    changed.enrichment.datasets[0].sha256 = "f" * 64
    source2, approved2 = reviewed(changed, tmp_path, "hash")
    with pytest.raises(ValueError, match="immutable dataset revision"):
        publish(source2, approved2, database)
    changed = bundle.model_copy(deep=True)
    old = "fixture.person.david"
    new = "other.person.david"
    next(e for e in changed.content.entities if e.id == old).id = new
    for edge in changed.content.relationships:
        if edge.sourceId == old:
            edge.sourceId = new
    changed.entitySources[new] = changed.entitySources.pop(old)
    next(r for r in changed.enrichment.records if r.entityId == old).entityId = new
    source3, approved3 = reviewed(changed, tmp_path, "identity")
    with pytest.raises(ValueError, match="identity binding changed"):
        publish(source3, approved3, database)
    with psycopg.connect(database) as conn:
        assert conn.execute("SELECT count(*) FROM entities WHERE id=%s", (new,)).fetchone()[0] == 0
        assert conn.execute("SELECT count(*) FROM content_publications").fetchone()[0] == 1
