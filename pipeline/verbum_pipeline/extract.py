"""AI-assisted entity/relationship extraction — proposals only, never publication.

Position in the §32 pipeline: source import -> normalization -> **entity extraction ->
relationship extraction** -> human/editorial review -> embeddings -> indexing -> publish.

This module turns raw source text plus editor-supplied source/provenance metadata into an
ordinary `editorial` Bundle — the exact artifact shape a human author would otherwise write by
hand. It adds no new trust path: the result still goes through `normalize`, `review.prepare`,
`review.require_approval` and `publish.publish` unchanged, and is rejected by the same strict
Pydantic validation as any other editorial submission. Extraction never writes to the database
and never marks anything approved.

License verification (§34) is a human responsibility and is not inferred here: the caller
supplies `Provenance` explicitly, the same as any other editorial batch.
"""

import json
import os
import re
import unicodedata
from typing import Annotated, Protocol

from pydantic import Field

from .models import (
    Bundle,
    Content,
    Detail,
    Entity,
    EntityType,
    Model,
    Provenance,
    Reference,
    Relationship,
    RelationshipType,
    Source,
    Text,
)

INSTRUCTIONS = """You extract structured facts about people, places, events and themes from a \
single supplied Bible-related source text, for a human-reviewed editorial pipeline.

Rules:
- Use only what the supplied text states or directly entails. Do not add outside knowledge,
  speculation, or facts absent from the text.
- Every entity must be clearly named in the text. Do not invent entities to fill categories.
- Only propose a relationship between two entities you also proposed, and only when the text
  directly supports it.
- Only include a keyPassage reference if the exact book/chapter/verse is stated in the text.
- Distinguish Scripture narrative from later interpretation or commentary; do not present the
  latter as narrative fact in a summary.
- Respond with a single JSON object and nothing else: no prose, no markdown fences.

JSON shape:
{
  "entities": [
    {
      "key": "short local id, unique in this response, e.g. e1",
      "type": "person | place | event | theme | passage | book | prophecy |
               originalTerm | historicalPeriod",
      "name": "string",
      "summary": "one or two sentences, or null",
      "aliases": ["string", ...],
      "approximateDates": "string or null",
      "role": "string or null",
      "modernGeography": "string or null",
      "keyPassages": [{"bookId": "string", "chapter": 1, "verseStart": 1, "verseEnd": 1}, ...]
    }
  ],
  "relationships": [
    {
      "sourceKey": "an entities[].key from this response",
      "targetKey": "an entities[].key from this response",
      "type": "appearsIn | participatesIn | occursAt | occursDuring | references |
               relatedToTheme | relatedTo | precedes | follows | fulfills | quotes",
      "confidence": 0.0 to 1.0, or null
    }
  ]
}
"""

DEFAULT_MODEL = "gpt-4o-mini"


class ProposedEntity(Model):
    key: Text
    type: EntityType
    name: Text
    summary: Text | None = None
    aliases: list[Text] = Field(default_factory=list)
    approximateDates: Text | None = None
    role: Text | None = None
    modernGeography: Text | None = None
    keyPassages: list[Reference] = Field(default_factory=list)


class ProposedRelationship(Model):
    sourceKey: Text
    targetKey: Text
    type: RelationshipType
    confidence: Annotated[float, Field(ge=0, le=1)] | None = None


class Proposal(Model):
    entities: Annotated[list[ProposedEntity], Field(min_length=1)]
    relationships: list[ProposedRelationship] = Field(default_factory=list)


class Client(Protocol):
    def complete(self, *, model: str, instructions: str, text: str) -> dict: ...


class OpenAIClient:
    """Thin wrapper so the OpenAI SDK is only imported/used here, never elsewhere."""

    def __init__(self, api_key: str) -> None:
        import openai

        self._client = openai.OpenAI(api_key=api_key)

    def complete(self, *, model: str, instructions: str, text: str) -> dict:
        try:
            response = self._client.chat.completions.create(
                model=model,
                temperature=0,
                response_format={"type": "json_object"},
                messages=[
                    {"role": "system", "content": instructions},
                    {"role": "user", "content": text},
                ],
            )
            content = response.choices[0].message.content
        except Exception as exc:  # SDK/network/auth errors; never leak the key
            raise ValueError(f"OpenAI request failed: {exc}") from exc
        try:
            return json.loads(content)
        except json.JSONDecodeError as exc:
            raise ValueError("OpenAI response was not valid JSON") from exc


def default_client() -> OpenAIClient:
    api_key = os.environ.get("OPENAI_API_KEY")
    if not api_key:
        raise ValueError(
            "OPENAI_API_KEY is required for extraction; run.ps1 sets it transiently from the "
            "DPAPI secret for the 'extract' command only"
        )
    return OpenAIClient(api_key)


def slugify(name: str) -> str:
    ascii_name = unicodedata.normalize("NFKD", name).encode("ascii", "ignore").decode("ascii")
    slug = re.sub(r"[^a-z0-9]+", "-", ascii_name.lower()).strip("-")
    if not slug:
        raise ValueError(f"entity name has no usable identifier characters: {name!r}")
    return slug


def has_detail_fields(entity: ProposedEntity) -> bool:
    return bool(
        entity.aliases
        or entity.approximateDates
        or entity.role
        or entity.modernGeography
        or entity.keyPassages
    )


def propose(
    text: str,
    source: Source,
    provenance: Provenance,
    *,
    client: Client,
    model: str = DEFAULT_MODEL,
) -> Bundle:
    """Call `client` once and turn its answer into a validated editorial Bundle proposal."""
    raw = client.complete(model=model, instructions=INSTRUCTIONS, text=text)
    proposal = Proposal.model_validate(raw)

    entity_ids: dict[str, str] = {}
    entities: list[Entity] = []
    details: list[Detail] = []
    for proposed in proposal.entities:
        entity_id = f"{source.id}.{proposed.type}.{slugify(proposed.name)}"
        if proposed.key in entity_ids:
            raise ValueError(f"duplicate entity key in model response: {proposed.key}")
        entity_ids[proposed.key] = entity_id
        entity = Entity(
            id=entity_id, type=proposed.type, name=proposed.name, summary=proposed.summary
        )
        entities.append(entity)
        if has_detail_fields(proposed):
            details.append(
                Detail(
                    entity=entity,
                    aliases=proposed.aliases,
                    approximateDates=proposed.approximateDates,
                    role=proposed.role,
                    modernGeography=proposed.modernGeography,
                    keyPassages=proposed.keyPassages,
                    sources=[source],
                )
            )

    relationships: list[Relationship] = []
    for index, edge in enumerate(proposal.relationships, start=1):
        if edge.sourceKey not in entity_ids or edge.targetKey not in entity_ids:
            raise ValueError(
                f"relationship references an entity key absent from this response: "
                f"{edge.sourceKey} -> {edge.targetKey}"
            )
        relationships.append(
            Relationship(
                id=f"{source.id}.rel.{index}",
                sourceId=entity_ids[edge.sourceKey],
                targetId=entity_ids[edge.targetKey],
                type=edge.type,
                confidence=edge.confidence,
                sourceReferenceIds=[source.id],
            )
        )

    content = Content(
        sources=[source],
        entities=entities,
        relationships=relationships,
        details=details,
        timeline=[],
        dailyVersePool=[],
    )
    return Bundle(
        kind="editorial",
        content=content,
        provenance={source.id: provenance},
        entitySources={entity.id: [source.id] for entity in entities},
    )
