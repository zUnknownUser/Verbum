"""Strict input models for the current content contract, not new API wire types."""

from typing import Annotated, Literal, Self

from pydantic import BaseModel, ConfigDict, Field, StringConstraints, model_validator

Text = Annotated[str, StringConstraints(min_length=1, pattern=r"\S")]
Positive = Annotated[int, Field(gt=0)]
EntityType = Literal[
    "person",
    "place",
    "event",
    "theme",
    "passage",
    "book",
    "prophecy",
    "originalTerm",
    "historicalPeriod",
]
RelationshipType = Literal[
    "appearsIn",
    "participatesIn",
    "occursAt",
    "occursDuring",
    "references",
    "relatedToTheme",
    "relatedTo",
    "precedes",
    "follows",
    "fulfills",
    "quotes",
]


class Model(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True, allow_inf_nan=False)


class Reference(Model):
    bookId: Text
    chapter: Positive
    verseStart: Positive | None = None
    verseEnd: Positive | None = None

    @model_validator(mode="after")
    def verse_range(self) -> Self:
        if (self.verseStart is None) != (self.verseEnd is None):
            raise ValueError("verseStart and verseEnd must occur together")
        if self.verseStart is not None and self.verseEnd < self.verseStart:
            raise ValueError("reversed verse range")
        return self


class Source(Model):
    id: Text
    citation: Text
    url: Text | None = None


class Entity(Model):
    id: Text
    type: EntityType
    name: Text
    summary: Text | None = None


class Relationship(Model):
    id: Text
    sourceId: Text
    targetId: Text
    type: RelationshipType
    confidence: Annotated[float, Field(ge=0, le=1)] | None = None
    sourceReferenceIds: Annotated[list[Text], Field(min_length=1)]


class Detail(Model):
    entity: Entity
    aliases: list[Text]
    approximateDates: Text | None = None
    role: Text | None = None
    modernGeography: Text | None = None
    keyPassages: list[Reference]
    sources: Annotated[list[Source], Field(min_length=1)]


class Event(Model):
    id: Text
    title: Text
    startYear: int | None = None
    endYear: int | None = None
    datePrecision: Literal["exact", "approximate", "debated", "unknown"]
    summary: Text | None = None
    entityIds: list[Text]
    sourceReferenceIds: Annotated[list[Text], Field(min_length=1)]

    @model_validator(mode="after")
    def chronology(self) -> Self:
        if self.endYear is not None and (self.startYear is None or self.endYear < self.startYear):
            raise ValueError("invalid timeline range")
        if self.datePrecision != "unknown" and self.startYear is None:
            raise ValueError("dated events require startYear")
        return self


def unique(values: list[str], label: str) -> None:
    if len(values) != len(set(values)):
        raise ValueError(f"duplicate {label}")


class Content(Model):
    sources: list[Source]
    entities: list[Entity]
    relationships: list[Relationship]
    details: list[Detail]
    timeline: list[Event]
    dailyVersePool: list[Reference]

    @model_validator(mode="after")
    def integrity(self) -> Self:
        for name in ("sources", "entities", "relationships", "timeline"):
            unique([v.id for v in getattr(self, name)], name)
        unique([d.entity.id for d in self.details], "details")
        sources = {s.id: s for s in self.sources}
        entities = {e.id: e for e in self.entities}
        for item in [*self.relationships, *self.timeline]:
            unique(item.sourceReferenceIds, "source references")
            if not set(item.sourceReferenceIds) <= sources.keys():
                raise ValueError(f"unknown source in {item.id}")
        for r in self.relationships:
            if r.sourceId not in entities or r.targetId not in entities:
                raise ValueError(f"unknown relationship endpoint in {r.id}")
        for event in self.timeline:
            unique(event.entityIds, "timeline entities")
            if not set(event.entityIds) <= entities.keys():
                raise ValueError(f"unknown entity in {event.id}")
        for detail in self.details:
            if entities.get(detail.entity.id) != detail.entity:
                raise ValueError(f"detail disagrees with entity {detail.entity.id}")
            unique(detail.aliases, "aliases")
            unique([s.id for s in detail.sources], "detail sources")
            if any(sources.get(s.id) != s for s in detail.sources):
                raise ValueError(f"detail has conflicting source in {detail.entity.id}")
        for ref in self.dailyVersePool:
            if ref.verseStart is None or ref.verseStart != ref.verseEnd:
                raise ValueError("daily pool requires single-verse references")
        return self


class Provenance(Model):
    sourceId: Text
    license: Text
    page: Text | None = None
    section: Text | None = None


class Bundle(Model):
    version: Literal[1] = 1
    kind: Literal["fixture", "editorial"]
    content: Content
    # Key = SourceReference.id, value = bibliographic metadata (§33).
    provenance: dict[str, Provenance]
    # Every entity claim has evidence, including entities without a detail page.
    entitySources: dict[str, list[Text]]

    @model_validator(mode="after")
    def evidence(self) -> Self:
        source_ids = {s.id for s in self.content.sources}
        entity_ids = {e.id for e in self.content.entities}
        if self.entitySources.keys() != entity_ids:
            raise ValueError("entitySources must cover exactly every entity")
        for entity_id, ids in self.entitySources.items():
            unique(ids, "entity sources")
            if not ids or not set(ids) <= source_ids:
                raise ValueError(f"missing/unknown evidence for {entity_id}")
        if not self.provenance.keys() <= source_ids:
            raise ValueError("provenance references an unknown source")
        if self.kind == "editorial" and self.provenance.keys() != source_ids:
            raise ValueError("editorial sources require provenance and license metadata")
        if self.kind == "editorial" and any(s.startswith("fixture.") for s in source_ids):
            raise ValueError("fixture sources cannot support editorial publication")
        return self
