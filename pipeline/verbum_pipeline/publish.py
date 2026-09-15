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
    if bundle.enrichment is not None:
        write_enrichment_entities(cur, bundle)
    for position, entity in enumerate(content.entities):
        if bundle.enrichment is not None:
            continue
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
    if bundle.enrichment is not None:
        write_enrichment(cur, bundle)
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
    write_relationships(cur, content.relationships)
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


def write_relationships(cur, edges) -> None:
    # executemany pipelines remote writes. Per-edge execute calls made the official
    # STEP graph spend minutes on round trips and risk connection timeouts.
    # Replace sources only for submitted edges; omitted editorial edges survive.
    if not edges:
        return
    cur.executemany(
        "INSERT INTO relationships(id,source_entity_id,target_entity_id,relationship_type,"
        "confidence,position) VALUES(%s,%s,%s,%s,%s,%s) ON CONFLICT(id) DO UPDATE SET "
        "source_entity_id=EXCLUDED.source_entity_id,target_entity_id=EXCLUDED.target_entity_id,"
        "relationship_type=EXCLUDED.relationship_type,confidence=EXCLUDED.confidence,"
        "position=EXCLUDED.position",
        [(e.id, e.sourceId, e.targetId, e.type, e.confidence, i) for i, e in enumerate(edges)],
    )
    cur.execute(
        "DELETE FROM relationship_sources WHERE relationship_id=ANY(%s)", ([e.id for e in edges],)
    )
    cur.executemany(
        "INSERT INTO relationship_sources(relationship_id,source_id,position) VALUES(%s,%s,%s)",
        [(e.id, sid, i) for e in edges for i, sid in enumerate(e.sourceReferenceIds)],
    )


def write_enrichment(cur, bundle: Bundle) -> None:
    enrichment = bundle.enrichment
    revisions = {d.sourceId: d.revision for d in enrichment.datasets}
    cur.execute(
        "SELECT source_id,revision,repository,path,sha256,license_url "
        "FROM source_datasets WHERE source_id=ANY(%s)",
        (list(revisions),),
    )
    pinned = {(row[0], row[1]): row[2:] for row in cur.fetchall()}
    for dataset in enrichment.datasets:
        existing = pinned.get((dataset.sourceId, dataset.revision))
        expected = (dataset.repository, dataset.path, dataset.sha256, dataset.licenseUrl)
        if existing and existing != expected:
            raise ValueError(f"immutable dataset revision changed: {dataset.sourceId}")
    cur.executemany(
        "INSERT INTO source_datasets(source_id,repository,revision,path,sha256,license_url,"
        "attribution,modifications) VALUES(%s,%s,%s,%s,%s,%s,%s,%s) "
        "ON CONFLICT(source_id,revision) DO NOTHING",
        [
            (
                d.sourceId,
                d.repository,
                d.revision,
                d.path,
                d.sha256,
                d.licenseUrl,
                d.attribution,
                d.modifications,
            )
            for d in enrichment.datasets
        ],
    )
    # Identity rebinding requires an explicit merge migration, never a silent source update.
    cur.execute(
        "SELECT id,entity_id,source_id,external_id FROM entity_source_records WHERE id=ANY(%s)",
        ([r.id for r in enrichment.records],),
    )
    bindings = {row[0]: row[1:] for row in cur.fetchall()}
    for record in enrichment.records:
        previous = bindings.get(record.id)
        if previous and previous != (record.entityId, record.sourceId, record.externalId):
            raise ValueError(f"source identity binding changed: {record.id}")
    cur.executemany(
        "INSERT INTO entity_source_records(id,entity_id,source_id,revision,external_id,source_line,"
        "identifiers,lexical) VALUES(%s,%s,%s,%s,%s,%s,%s,%s) ON CONFLICT(id) DO UPDATE SET "
        "revision=EXCLUDED.revision,source_line=EXCLUDED.source_line,"
        "identifiers=EXCLUDED.identifiers,lexical=EXCLUDED.lexical",
        [
            (
                r.id,
                r.entityId,
                r.sourceId,
                revisions[r.sourceId],
                r.externalId,
                r.sourceLine,
                Jsonb(r.identifiers),
                Jsonb(r.lexical.model_dump()) if r.lexical else None,
            )
            for r in enrichment.records
        ],
    )
    # Replace only submitted records' evidence and this source's localizations. Editorial
    # aliases, details, translations from other sources, and omitted records stay intact.
    cur.execute(
        "DELETE FROM entity_occurrences WHERE record_id=ANY(%s)",
        ([r.id for r in enrichment.records],),
    )
    cur.executemany(
        "DELETE FROM entity_localizations WHERE entity_id=%s AND source_id=%s",
        [(r.entityId, r.sourceId) for r in enrichment.records],
    )
    cur.executemany(
        "INSERT INTO entity_localizations(entity_id,language,source_id,name,aliases,description) "
        "VALUES(%s,%s,%s,%s,%s,%s)",
        [
            (r.entityId, loc.language, r.sourceId, loc.name, loc.aliases, loc.description)
            for r in enrichment.records
            for loc in r.localizations
        ],
    )
    cur.executemany(
        "INSERT INTO entity_occurrences(record_id,locator,book_id,chapter,verse) "
        "VALUES(%s,%s,%s,%s,%s)",
        [
            (r.id, o.locator, o.reference.bookId, o.reference.chapter, o.reference.verseStart)
            for r in enrichment.records
            for o in r.occurrences
        ],
    )


def write_enrichment_entities(cur, bundle: Bundle) -> None:
    # Batch writes use psycopg's pipeline mode, including on a remote PostgreSQL connection.
    # External sources cannot rewrite curated names, summaries or existing graph identities.
    cur.executemany(
        "INSERT INTO entities(id,type,name,summary,position) VALUES(%s,%s,%s,%s,%s) "
        "ON CONFLICT(id) DO NOTHING",
        [(e.id, e.type, e.name, e.summary, i) for i, e in enumerate(bundle.content.entities)],
    )
    cur.execute(
        "SELECT id,type FROM entities WHERE id=ANY(%s)", ([e.id for e in bundle.content.entities],)
    )
    types = dict(cur.fetchall())
    for entity in bundle.content.entities:
        if types[entity.id] != entity.type:
            raise ValueError(f"entity mapping type conflict: {entity.id}")
    cur.executemany(
        "INSERT INTO entity_sources(entity_id,source_id,position) VALUES(%s,%s,%s) "
        "ON CONFLICT(entity_id,source_id) DO NOTHING",
        [
            (e.id, sid, i)
            for e in bundle.content.entities
            for i, sid in enumerate(bundle.entitySources[e.id])
        ],
    )
