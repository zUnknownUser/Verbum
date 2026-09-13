"""Normalize structure and verify references; never rewrite editorial prose."""

from pathlib import Path

from .files import read, write
from .models import Bundle, Content


def import_source(source: Path, destination: Path, *, fixtures: bool = False) -> dict:
    raw = read(source)
    if fixtures:
        # The first source adapter is the existing, explicitly labelled fixtures.
        content = Content.model_validate(raw)
        sources = {s.id for s in content.sources}
        if "fixture.source.editorial" not in sources or any(
            not s.startswith("fixture.") for s in sources
        ):
            raise ValueError("fixture import requires the labelled development sources")
        evidence = {e.id: ["fixture.source.editorial"] for e in content.entities}
        for detail in content.details:
            evidence[detail.entity.id] = [s.id for s in detail.sources]
        raw = {
            "version": 1,
            "kind": "fixture",
            "content": raw,
            "provenance": {},
            "entitySources": evidence,
        }
    # Import preserves source values and keeps proposals separate from publication.
    write(destination, raw)
    return raw


def normalize(source: Path, destination: Path) -> dict:
    bundle = Bundle.model_validate(read(source))
    result = bundle.model_dump(exclude_unset=True)
    # Canonical key ordering is used for hashes; array order remains editorial.
    write(destination, result)
    return result
