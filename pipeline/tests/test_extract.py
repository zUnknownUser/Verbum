import pytest
from pydantic import ValidationError

from verbum_pipeline import extract
from verbum_pipeline.cli import main
from verbum_pipeline.files import read, write
from verbum_pipeline.models import Provenance, Source

SOURCE = Source(id="editorial.source.test-1", citation="Test Encyclopedia, 2026")
PROVENANCE = Provenance(sourceId="test-encyclopedia", license="CC-BY-4.0", page="12")


class FakeClient:
    def __init__(self, payload: dict):
        self.payload = payload
        self.calls = []

    def complete(self, *, model, instructions, text):
        self.calls.append({"model": model, "instructions": instructions, "text": text})
        return self.payload


WELL_FORMED = {
    "entities": [
        {
            "key": "e1",
            "type": "person",
            "name": "David",
            "summary": "King of Israel.",
            "aliases": ["Son of Jesse"],
            "keyPassages": [{"bookId": "1SA", "chapter": 17, "verseStart": 50, "verseEnd": 50}],
        },
        {"key": "e2", "type": "person", "name": "Golias", "summary": "Philistine champion."},
    ],
    "relationships": [
        {"sourceKey": "e1", "targetKey": "e2", "type": "relatedTo", "confidence": 0.9}
    ],
}


def test_propose_builds_valid_editorial_bundle():
    bundle = extract.propose(
        "David defeated Golias in single combat.",
        SOURCE,
        PROVENANCE,
        client=FakeClient(WELL_FORMED),
    )
    assert bundle.kind == "editorial"
    ids = {e.id for e in bundle.content.entities}
    assert ids == {"editorial.source.test-1.person.david", "editorial.source.test-1.person.golias"}
    assert bundle.entitySources.keys() == ids
    assert all(v == [SOURCE.id] for v in bundle.entitySources.values())
    assert len(bundle.content.relationships) == 1
    edge = bundle.content.relationships[0]
    assert edge.sourceId.endswith("david") and edge.targetId.endswith("golias")
    assert edge.sourceReferenceIds == [SOURCE.id]
    # Only David carries extra fields, so only David gets a Detail.
    assert [d.entity.name for d in bundle.content.details] == ["David"]
    assert bundle.provenance == {SOURCE.id: PROVENANCE}


def test_propose_rejects_relationship_to_unknown_key():
    payload = {
        "entities": [{"key": "e1", "type": "person", "name": "David"}],
        "relationships": [{"sourceKey": "e1", "targetKey": "ghost", "type": "relatedTo"}],
    }
    with pytest.raises(ValueError, match="absent from this response"):
        extract.propose("text", SOURCE, PROVENANCE, client=FakeClient(payload))


def test_propose_rejects_duplicate_local_keys():
    payload = {
        "entities": [
            {"key": "e1", "type": "person", "name": "David"},
            {"key": "e1", "type": "person", "name": "Golias"},
        ],
        "relationships": [],
    }
    with pytest.raises(ValueError, match="duplicate entity key"):
        extract.propose("text", SOURCE, PROVENANCE, client=FakeClient(payload))


def test_propose_rejects_invalid_entity_type():
    payload = {"entities": [{"key": "e1", "type": "alien", "name": "David"}], "relationships": []}
    with pytest.raises(ValidationError):
        extract.propose("text", SOURCE, PROVENANCE, client=FakeClient(payload))


def test_propose_rejects_malformed_json_response():
    class BrokenClient:
        def complete(self, **kwargs):
            raise ValueError("OpenAI response was not valid JSON")

    with pytest.raises(ValueError, match="not valid JSON"):
        extract.propose("text", SOURCE, PROVENANCE, client=BrokenClient())


def test_default_client_requires_api_key(monkeypatch):
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)
    with pytest.raises(ValueError, match="OPENAI_API_KEY"):
        extract.default_client()


def test_slugify_rejects_unusable_names():
    with pytest.raises(ValueError):
        extract.slugify("!!!")


def test_extract_cli_feeds_the_existing_review_gate(tmp_path, monkeypatch):
    source_input = tmp_path / "source.json"
    write(source_input, {"source": SOURCE.model_dump(), "provenance": PROVENANCE.model_dump()})
    bundle_path = tmp_path / "bundle.json"
    raw_text = tmp_path / "raw.txt"
    raw_text.write_text("David defeated Golias in single combat.", encoding="utf-8")

    monkeypatch.setattr(extract, "default_client", lambda: FakeClient(WELL_FORMED))
    exit_code = main(
        ["extract", str(raw_text), "--source", str(source_input), "--output", str(bundle_path)]
    )
    assert exit_code == 0
    produced = read(bundle_path)
    assert produced["kind"] == "editorial"

    normalized = tmp_path / "normalized.json"
    assert main(["normalize", str(bundle_path), "--output", str(normalized)]) == 0
    queue = tmp_path / "queue.json"
    decisions = tmp_path / "decisions.json"
    review_args = ["review", str(normalized), "--queue", str(queue), "--decisions", str(decisions)]
    assert main(review_args) == 0
    assert len(read(queue)["items"]) > 0
    assert all(d["status"] == "pending" for d in read(decisions)["items"])
