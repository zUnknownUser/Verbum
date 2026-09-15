"""Human decisions bound to the exact content and provenance under review."""

from datetime import datetime
from pathlib import Path
from typing import Literal

from .files import digest, read, write
from .models import Bundle, Model, Text, unique


class Decision(Model):
    key: Text
    status: Literal["pending", "approved", "rejected"] = "pending"
    reviewer: Text | None = None
    reviewedAt: Text | None = None
    note: str = ""


class Review(Model):
    version: Literal[1] = 1
    bundleHash: Text
    items: list[Decision]


def items(bundle: Bundle) -> list[dict]:
    result = []
    for group in ("sources", "entities", "relationships", "details", "timeline"):
        for value in getattr(bundle.content, group):
            item_id = value.entity.id if group == "details" else value.id
            if group == "entities":
                evidence = bundle.entitySources[item_id]
            elif group in ("relationships", "timeline"):
                evidence = value.sourceReferenceIds
            elif group == "details":
                evidence = [s.id for s in value.sources]
            else:
                evidence = [item_id]
            result.append(
                {
                    "key": f"{group}:{item_id}",
                    "proposal": value.model_dump(),
                    "sourceReferenceIds": evidence,
                }
            )
    if bundle.enrichment is not None:
        for dataset in bundle.enrichment.datasets:
            result.append(
                {
                    "key": f"dataset:{dataset.sourceId}",
                    "proposal": dataset.model_dump(),
                    "sourceReferenceIds": [dataset.sourceId],
                }
            )
        for record in bundle.enrichment.records:
            result.append(
                {
                    "key": f"record:{record.id}",
                    "proposal": record.model_dump(),
                    "sourceReferenceIds": [record.sourceId],
                }
            )
    for translation in bundle.translations or []:
        result.append(
            {
                "key": f"translation:{translation.target}:{translation.id}:{translation.language}",
                "proposal": translation.model_dump(),
                "sourceReferenceIds": [translation.sourceId],
            }
        )
    result.append(
        {
            "key": "dailyVersePool",
            "proposal": [p.model_dump(exclude_none=True) for p in bundle.content.dailyVersePool],
            "sourceReferenceIds": [],
        }
    )
    return result


def prepare(source: Path, queue: Path, decisions: Path) -> None:
    bundle = Bundle.model_validate(read(source))
    proposed = items(bundle)
    bundle_hash = digest(bundle.model_dump())
    if queue.resolve() == decisions.resolve() or queue.exists() or decisions.exists():
        raise ValueError("queue and decisions need distinct, unused paths")
    write(
        queue,
        {
            "bundleHash": bundle_hash,
            "kind": bundle.kind,
            "sources": [s.model_dump() for s in bundle.content.sources],
            "provenance": bundle.model_dump()["provenance"],
            "items": proposed,
        },
    )
    write(
        decisions,
        Review(
            bundleHash=bundle_hash, items=[Decision(key=item["key"]) for item in proposed]
        ).model_dump(),
    )


def require_approval(bundle: Bundle, path: Path) -> Review:
    review = Review.model_validate(read(path))
    if review.bundleHash != digest(bundle.model_dump()):
        raise ValueError("content or provenance changed after review; prepare a new review")
    unique([d.key for d in review.items], "review decisions")
    expected = {item["key"] for item in items(bundle)}
    if {d.key for d in review.items} != expected:
        raise ValueError("review must cover exactly every proposed item")
    for decision in review.items:
        if decision.status != "approved" or not decision.reviewer or not decision.reviewedAt:
            raise ValueError(f"human approval missing: {decision.key}")
        stamp = datetime.fromisoformat(decision.reviewedAt.replace("Z", "+00:00"))
        if stamp.tzinfo is None:
            raise ValueError("reviewedAt must include a timezone")
        if stamp > datetime.now(stamp.tzinfo):
            raise ValueError("reviewedAt cannot be in the future")
    return review
