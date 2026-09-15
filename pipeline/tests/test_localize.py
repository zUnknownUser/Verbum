import copy

import psycopg
import pytest
from pydantic import ValidationError

from verbum_pipeline import localize
from verbum_pipeline.files import digest, read, write
from verbum_pipeline.models import Bundle
from verbum_pipeline.publish import publish
from verbum_pipeline.review import prepare

from .conftest import approve_for_test


def row():
    return dict(
        target="entity",
        id="lexeme",
        kind="originalTerm",
        transliteration="agapē",
        fields=dict(
            name="ἀγάπη",
            description="love",
            aliases=["agapē", "love"],
            approximateDates=None,
            role=None,
            modernGeography=None,
        ),
    )


def test_original_script_preserved_and_gloss_translated():
    source = row()
    batch = localize.build([source], dict(model="test", values={"love": "amor"}))
    t = batch.translations[0]
    assert t.fields["name"] == "ἀγάπη"
    assert t.fields["description"] == "amor"
    assert t.fields["aliases"] == ["agapē", "amor"]
    assert t.inputHash == digest(source)
    assert source == row()
    assert Bundle.model_validate(batch.model_dump()) == batch


def test_translation_cannot_rewrite_canonical_content_or_omit_fields():
    raw = localize.build([row()], dict(model="test", values={"love": "amor"})).model_dump()
    del raw["translations"][0]["fields"]["description"]
    with pytest.raises(ValidationError):
        Bundle.model_validate(raw)


def test_rejected_batch_can_resume_without_repeating_success(tmp_path):
    class Client:
        calls = 0

        def complete(self, **kwargs):
            self.calls += 1
            return {"0": "amor"}

    client = Client()
    path = tmp_path / "cache.json"
    localize.translate([row()], path, client, "test")
    localize.translate([row()], path, client, "test")
    assert client.calls == 1
    assert read(path)["values"] == {"love": "amor"}


@pytest.mark.postgres
def test_publication_overlays_every_field_and_preserves_source(database, artifacts, tmp_path):
    source, _, decisions = artifacts
    approved = approve_for_test(decisions, tmp_path / "fixture-approved.json")
    publish(source, approved, database, allow_fixtures=True)
    with psycopg.connect(database) as conn:
        with conn.cursor() as cur:
            rows = localize.snapshot(cur)
    values = {v: "PT " + v for v in localize.translation_strings(rows)}
    bundle = localize.build(rows, dict(model="test", values=values))
    path = tmp_path / "localized.json"
    write(path, bundle.model_dump())
    prepare(path, tmp_path / "translation-queue.json", tmp_path / "translation-decisions.json")
    approval = approve_for_test(tmp_path / "translation-decisions.json", tmp_path / "approved.json")
    assert publish(path, approval, database)["status"] == "published"
    assert publish(path, approval, database)["status"] == "already_published"
    with psycopg.connect(database) as conn:
        with conn.cursor() as cur:
            assert localize.snapshot(cur) == rows
        assert conn.execute("SELECT count(*) FROM entity_localizations").fetchone()[0] == len(
            [r for r in rows if r["target"] == "entity"]
        )
        assert conn.execute("SELECT count(*) FROM timeline_localizations").fetchone()[0] > 0
        assert conn.execute("SELECT count(*) FROM source_localizations").fetchone()[0] > 0
        conn.execute(
            "UPDATE entities SET summary='Changed after translation' WHERE id=%s", (rows[0]["id"],)
        )
    changed = copy.deepcopy(bundle)
    changed.translations[0].fields["description"] = "Outra descrição"
    path2 = tmp_path / "changed.json"
    write(path2, changed.model_dump())
    prepare(path2, tmp_path / "queue2.json", tmp_path / "decisions2.json")
    approval2 = approve_for_test(tmp_path / "decisions2.json", tmp_path / "approved2.json")
    with pytest.raises(ValueError, match="source changed"):
        publish(path2, approval2, database)
    with psycopg.connect(database) as conn:
        assert conn.execute("SELECT count(*) FROM content_publications").fetchone()[0] == 2
