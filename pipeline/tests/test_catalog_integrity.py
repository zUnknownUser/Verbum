"""Release catalogue invariants, checked offline against the shipped Bible corpus.

These checks prove reference/link integrity, not theological or historical accuracy.
"""

from collections import defaultdict
from urllib.parse import parse_qs, urlsplit

from verbum_pipeline.files import read
from verbum_pipeline.models import Bundle

from .conftest import ROOT


def test_current_catalogue_references_sources_and_navigation():
    sources = ROOT / "pipeline/sources"
    themes, events, timeline = [
        Bundle.model_validate(read(sources / path))
        for path in ("themes/catalog.json", "events/catalog.json", "sequences/exodus.json")
    ]
    assert len(themes.content.entities) == 80
    assert len(events.content.entities) == 137
    assert len(timeline.content.timeline) == 98
    assert len(timeline.eventSequences[0].items) == 16

    known = {e["id"] for e in read(ROOT / "backend/db/seed/fixtures.json")["entities"]}
    known |= {e.id for bundle in (themes, events) for e in bundle.content.entities}
    verses = defaultdict(set)
    for line in (ROOT / "VerbumKit/Sources/Clients/Resources/web.tsv").read_text().splitlines():
        book, chapter, verse, _ = line.split("\t", 3)
        verses[book, int(chapter)].add(int(verse))

    for bundle in (themes, events, timeline):
        assert set(bundle.content.referencedEntityIds) <= known
        references = {s.id: s for s in bundle.content.sources}
        assert not any(s.startswith("fixture.") for s in references)
        for source in references.values():
            if source.url:
                url = urlsplit(source.url)
                assert url.scheme == "https" and url.hostname and not url.username
                if source.id.startswith("scripture."):
                    # A source must open the passage that its citation names.
                    assert parse_qs(url.query)["search"] == [source.citation]
        selections = [(d.keyPassages, [s.id for s in d.sources]) for d in bundle.content.details]
        if bundle.timelineDiscovery:
            for event in bundle.content.timeline:
                assert set(event.entityIds) <= known
                en, pt = (
                    bundle.timelineDiscovery.presentations[event.id][lang]
                    for lang in ("en", "pt-BR")
                )
                assert en.keyPassages == pt.keyPassages
                selections.append((en.keyPassages, event.sourceReferenceIds))
        for passages, source_ids in selections:
            for ref in passages:
                available = verses[ref.bookId, ref.chapter]
                assert available, ref
                citation = f"{ref.bookId} {ref.chapter}"
                if ref.verseStart is not None:
                    assert set(range(ref.verseStart, ref.verseEnd + 1)) <= available, ref
                    citation += f":{ref.verseStart}-{ref.verseEnd}"
                assert any(references[s].citation == citation for s in source_ids), ref

    timeline_ids = {e.id for e in timeline.content.timeline}
    event_ids = {e.id for e in events.content.entities}
    assert set(events.eventCatalog.timelineBindings) <= timeline_ids
    assert set(events.eventCatalog.timelineBindings.values()) <= event_ids
    for sequence in timeline.eventSequences:
        assert all(i.entityId in event_ids and i.timelineId in timeline_ids for i in sequence.items)
