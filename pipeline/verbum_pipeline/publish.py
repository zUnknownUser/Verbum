"""Atomic publication into the Go API's relational content schema."""

from pathlib import Path

import psycopg
from psycopg import sql
from psycopg.types.json import Jsonb

from .files import digest, read
from .models import Bundle
from .review import require_approval


def upsert(cursor, table: str, values: dict, key: str) -> None:
    columns = list(values)
    statement = sql.SQL("INSERT INTO {} ({}) VALUES ({}) ON CONFLICT ({}) DO UPDATE SET {}").format(
        sql.Identifier(table),
        sql.SQL(",").join(map(sql.Identifier, columns)),
        sql.SQL(",").join(sql.Placeholder() for _ in columns),
        sql.Identifier(key),
        sql.SQL(",").join(
            sql.SQL("{}=EXCLUDED.{}").format(sql.Identifier(c), sql.Identifier(c))
            for c in columns
            if c != key
        ),
    )
    cursor.execute(statement, list(values.values()))


def replace_links(cursor, table: str, key: str, value: str, rows: list[dict]) -> None:
    cursor.execute(
        sql.SQL("DELETE FROM {} WHERE {}=%s").format(sql.Identifier(table), sql.Identifier(key)),
        (value,),
    )
    for row in rows:
        cursor.execute(
            sql.SQL("INSERT INTO {} ({}) VALUES ({})").format(
                sql.Identifier(table),
                sql.SQL(",").join(map(sql.Identifier, row)),
                sql.SQL(",").join(sql.Placeholder() for _ in row),
            ),
            list(row.values()),
        )


def reference(ref) -> dict:
    return {
        "book_id": ref.bookId,
        "chapter": ref.chapter,
        "verse_start": ref.verseStart,
        "verse_end": ref.verseEnd,
    }


def publish(source: Path, decisions: Path, database_url: str, *, allow_fixtures=False) -> dict:
    bundle = Bundle.model_validate(read(source))
    review = require_approval(bundle, decisions)
    if bundle.kind == "fixture" and not allow_fixtures:
        raise ValueError("fixture publication requires --allow-fixtures on a development database")
    bundle_hash = digest(bundle.model_dump())
    # No database connection is attempted before every review check succeeds.
    with psycopg.connect(database_url, connect_timeout=10) as conn:
        with conn.cursor() as cur:
            cur.execute("SET LOCAL statement_timeout='30s'")
            cur.execute("SET LOCAL lock_timeout='10s'")
            cur.execute("SELECT pg_advisory_xact_lock(867530911)")
            cur.execute("SELECT 1 FROM content_publications WHERE bundle_hash=%s", (bundle_hash,))
            if cur.fetchone():
                return {"status": "already_published", "bundleHash": bundle_hash}
            write_content(cur, bundle)
            cur.execute(
                "INSERT INTO content_publications(bundle_hash,kind,review) VALUES(%s,%s,%s)",
                (bundle_hash, bundle.kind, Jsonb(review.model_dump())),
            )
    return {"status": "published", "bundleHash": bundle_hash}


def write_content(cur, bundle: Bundle) -> None:
    content = bundle.content
    for position, source in enumerate(content.sources):
        upsert(cur, "sources", {**source.model_dump(), "position": position}, "id")
        if source.id in bundle.provenance:
            p = bundle.provenance[source.id]
            upsert(
                cur,
                "source_provenance",
                {
                    "reference_id": source.id,
                    "source_id": p.sourceId,
                    "license": p.license,
                    "page": p.page,
                    "section": p.section,
                },
                "reference_id",
            )
    for position, entity in enumerate(content.entities):
        upsert(cur, "entities", {**entity.model_dump(), "position": position}, "id")
        replace_links(
            cur,
            "entity_sources",
            "entity_id",
            entity.id,
            [
                {"entity_id": entity.id, "source_id": sid, "position": i}
                for i, sid in enumerate(bundle.entitySources[entity.id])
            ],
        )
    for detail in content.details:
        entity_id = detail.entity.id
        upsert(
            cur,
            "entity_details",
            {
                "entity_id": entity_id,
                "approximate_dates": detail.approximateDates,
                "role": detail.role,
                "modern_geography": detail.modernGeography,
            },
            "entity_id",
        )
        replace_links(
            cur,
            "entity_aliases",
            "entity_id",
            entity_id,
            [
                {"entity_id": entity_id, "alias": alias, "position": i}
                for i, alias in enumerate(detail.aliases)
            ],
        )
        replace_links(
            cur,
            "entity_key_passages",
            "entity_id",
            entity_id,
            [
                {"entity_id": entity_id, "position": i, **reference(p)}
                for i, p in enumerate(detail.keyPassages)
            ],
        )
        replace_links(
            cur,
            "entity_detail_sources",
            "entity_id",
            entity_id,
            [
                {"entity_id": entity_id, "source_id": s.id, "position": i}
                for i, s in enumerate(detail.sources)
            ],
        )
    for position, edge in enumerate(content.relationships):
        upsert(
            cur,
            "relationships",
            {
                "id": edge.id,
                "source_entity_id": edge.sourceId,
                "target_entity_id": edge.targetId,
                "relationship_type": edge.type,
                "confidence": edge.confidence,
                "position": position,
            },
            "id",
        )
        replace_links(
            cur,
            "relationship_sources",
            "relationship_id",
            edge.id,
            [
                {"relationship_id": edge.id, "source_id": sid, "position": i}
                for i, sid in enumerate(edge.sourceReferenceIds)
            ],
        )
    for position, event in enumerate(content.timeline):
        upsert(
            cur,
            "timeline_events",
            {
                "id": event.id,
                "title": event.title,
                "start_year": event.startYear,
                "end_year": event.endYear,
                "date_precision": event.datePrecision,
                "summary": event.summary,
                "position": position,
            },
            "id",
        )
        for table, column, ids in (
            ("timeline_event_entities", "entity_id", event.entityIds),
            ("timeline_event_sources", "source_id", event.sourceReferenceIds),
        ):
            replace_links(
                cur,
                table,
                "event_id",
                event.id,
                [
                    {"event_id": event.id, column: item_id, "position": i}
                    for i, item_id in enumerate(ids)
                ],
            )
    # A non-empty daily pool is an explicitly reviewed replacement of this ordered list.
    # An empty pool means this batch does not modify the existing daily selection.
    if content.dailyVersePool:
        cur.execute("DELETE FROM daily_verse_pool")
        for position, ref in enumerate(content.dailyVersePool):
            upsert(cur, "daily_verse_pool", {"position": position, **reference(ref)}, "position")
