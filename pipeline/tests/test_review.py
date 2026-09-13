import copy

import pytest
from pydantic import ValidationError

from verbum_pipeline.files import read, write
from verbum_pipeline.models import Bundle
from verbum_pipeline.normalize import normalize
from verbum_pipeline.publish import publish
from verbum_pipeline.review import prepare, require_approval

from .conftest import ROOT, approve_for_test


def test_fixture_import_preserves_content(artifacts):
    source, queue, decisions = artifacts
    assert read(source)["content"] == read(ROOT / "backend/db/seed/fixtures.json")
    assert len(read(queue)["items"]) == len(read(decisions)["items"])
    assert all(d["status"] == "pending" for d in read(decisions)["items"])
    assert all(d["reviewer"] is None for d in read(decisions)["items"])


def test_pending_never_connects(artifacts, monkeypatch):
    def forbidden(*args, **kwargs):
        pytest.fail("database touched before human review")

    monkeypatch.setattr("verbum_pipeline.publish.psycopg.connect", forbidden)
    source, _, decisions = artifacts
    with pytest.raises(ValueError, match="approval missing"):
        publish(source, decisions, "unused", allow_fixtures=True)


@pytest.mark.parametrize("change", ["content", "evidence", "license"])
def test_edit_invalidates_approval(artifacts, tmp_path, change):
    source, _, decisions = artifacts
    approved = approve_for_test(decisions, tmp_path / "approved.json")
    raw = read(source)
    if change == "content":
        raw["content"]["timeline"][0]["title"] += " changed"
    elif change == "evidence":
        entity = raw["content"]["entities"][0]["id"]
        raw["entitySources"][entity] = list(reversed(raw["entitySources"][entity]))
        if len(raw["entitySources"][entity]) == 1:
            raw["entitySources"][entity] = [raw["content"]["sources"][0]["id"]]
    else:
        raw["provenance"] = {
            raw["content"]["sources"][0]["id"]: {"sourceId": "test", "license": "test only"}
        }
    with pytest.raises(ValueError, match="changed after review"):
        require_approval(Bundle.model_validate(raw), approved)


@pytest.mark.parametrize(
    "mutation",
    [
        "duplicate",
        "endpoint",
        "source",
        "confidence",
        "range",
        "unknown_field",
        "detail_conflict",
        "unsourced",
    ],
)
def test_invalid_proposals_rejected(artifacts, mutation):
    raw = copy.deepcopy(read(artifacts[0]))
    content = raw["content"]
    if mutation == "duplicate":
        content["entities"].append(content["entities"][0])
    elif mutation == "endpoint":
        content["relationships"][0]["targetId"] = "nonexistent"
    elif mutation == "source":
        content["relationships"][0]["sourceReferenceIds"] = ["nonexistent"]
    elif mutation == "confidence":
        content["relationships"][0]["confidence"] = float("nan")
    elif mutation == "range":
        content["dailyVersePool"][0]["verseEnd"] = 0
    elif mutation == "unknown_field":
        content["entities"][0]["invented"] = True
    elif mutation == "detail_conflict":
        content["details"][0]["entity"]["name"] += " changed"
    else:
        raw["entitySources"][content["entities"][0]["id"]] = []
    with pytest.raises(ValidationError):
        Bundle.model_validate(raw)


def test_rejected_missing_and_duplicate_decisions_block(artifacts, tmp_path):
    source, _, decisions = artifacts
    bundle = Bundle.model_validate(read(source))
    approved = approve_for_test(decisions, tmp_path / "approved.json")
    for name in ("rejected", "missing", "duplicate", "anonymous", "future"):
        raw = read(approved)
        if name == "rejected":
            raw["items"][0]["status"] = "rejected"
        elif name == "missing":
            raw["items"].pop()
        elif name == "duplicate":
            raw["items"].append(raw["items"][0])
        elif name == "anonymous":
            raw["items"][0]["reviewer"] = None
        else:
            raw["items"][0]["reviewedAt"] = "2999-01-01T00:00:00Z"
        output = tmp_path / f"{name}.json"
        write(output, raw)
        with pytest.raises(ValueError):
            require_approval(bundle, output)


def test_artifacts_not_overwritten(artifacts):
    source, queue, decisions = artifacts
    before = decisions.read_bytes()
    with pytest.raises(ValueError):
        prepare(source, queue, decisions)
    with pytest.raises(FileExistsError):
        normalize(source, source)
    assert decisions.read_bytes() == before


def test_editorial_requires_real_provenance(artifacts):
    raw = read(artifacts[0])
    raw["kind"] = "editorial"
    with pytest.raises(ValidationError):
        Bundle.model_validate(raw)
