"""Five bilingual theme guides, published through the existing editorial gate.

Narrative context, observation and editorial interpretation remain distinct.
Original entities, categories and readings are retained; no generation at runtime.
"""

import json
from collections import defaultdict

from psycopg.types.json import Jsonb

from .chapter_studies import BOOK_FILES as CHAPTER_FILES
from .chapter_studies import BOOK_NAMES as CHAPTER_NAMES
from .chapter_studies import ROOT, reference, source_id
from .files import digest, read
from .models import Bundle, Reference

AUTHOR = "editorial.theme-studies.2026-10"
FOLDER = ROOT / "sources/theme-studies"
THEMES = {
    "fixture.theme.faith",
    "editorial.theme.covenant",
    "editorial.theme.worship",
    "editorial.theme.obedience",
    "editorial.theme.holiness",
}
BOOK_NAMES = CHAPTER_NAMES | {
    "Lev": "Leviticus",
    "Jer": "Jeremiah",
    "Luke": "Luke",
    "Mark": "Mark",
    "Jas": "James",
    "Rom": "Romans",
    "1Pet": "1 Peter",
}
BOOK_FILES = CHAPTER_FILES | {
    "Lev": "LEV",
    "Jer": "JER",
    "Luke": "LUK",
    "Mark": "MRK",
    "Jas": "JAS",
    "Rom": "ROM",
    "1Pet": "1PE",
}


def source_url(reference):
    chapter = str(reference.chapter).zfill(3 if reference.bookId == "Ps" else 2)
    return f"https://ebible.org/engwebp/{BOOK_FILES[reference.bookId]}{chapter}.htm"


def study_references(presentation):
    yield from presentation.overview.references
    yield from (r.reference for r in presentation.readings)
    for comparison in presentation.comparisons:
        yield from comparison.references
    yield from (c.reference for c in presentation.connections)


def contains(reading, reference):
    return (reading.bookId, reading.chapter) == (reference.bookId, reference.chapter) and (
        reading.verseStart <= reference.verseStart <= reference.verseEnd <= reading.verseEnd
    )


def validate_references(bundle):
    verses = defaultdict(set)
    for line in (
        (ROOT.parent / "VerbumKit/Sources/Clients/Resources/web.tsv").read_text().splitlines()
    ):
        book, chapter, verse, _ = line.split("\t", 3)
        verses[book, int(chapter)].add(int(verse))
    original = Bundle.model_validate(read(ROOT / "sources/themes/catalog.json"))
    legacy = {d.entity.id: d.keyPassages for d in original.content.details}
    if {s.entityId for s in bundle.themeStudies} != THEMES:
        raise ValueError("this bounded edition covers exactly the five agreed themes")
    sources = {s.id: s for s in bundle.content.sources}
    expected_edges = {}
    for study in bundle.themeStudies:
        p = study.presentations["en"]
        readings = [r.reference for r in p.readings]
        if len({str(r.model_dump()) for r in readings}) != len(readings):
            raise ValueError("theme readings cannot duplicate a passage")
        if not all(r in readings for r in legacy[study.entityId]):
            raise ValueError("theme study must retain the original reading selections")
        if len(p.readings) != 5 or len(p.comparisons) != 2 or len(p.connections) != 3:
            raise ValueError(
                "this edition needs five readings, two comparisons and three connections"
            )
        refs = list(study_references(p))
        if set(p.sourceReferenceIds) != {AUTHOR, *(source_id(r) for r in refs)}:
            raise ValueError(
                "theme study must attribute exactly its editorial and Scripture sources"
            )
        for ref in refs:
            if (
                ref.verseStart is None
                or not set(range(ref.verseStart, ref.verseEnd + 1))
                <= verses[ref.bookId, ref.chapter]
            ):
                raise ValueError("theme study references missing Scripture or an unbounded chapter")
            if not any(contains(r, ref) for r in readings):
                raise ValueError("theme evidence must be within its commented readings")
            expected_url = source_url(ref)
            if sources[source_id(ref)].url != expected_url:
                raise ValueError("theme source must open the cited chapter")
        for connection in p.connections:
            expected_edges[connection.entityId, study.entityId] = {
                AUTHOR,
                source_id(connection.reference),
            }
    actual_edges = {
        (r.sourceId, r.targetId): set(r.sourceReferenceIds) for r in bundle.content.relationships
    }
    if actual_edges != expected_edges:
        raise ValueError("theme graph connections must match explained, sourced connections")
    referenced = THEMES | {a for a, _ in expected_edges}
    if set(bundle.content.referencedEntityIds) != referenced:
        raise ValueError(
            "theme edition must reference exactly its themes and explained connections"
        )


def build() -> Bundle:
    rows = json.loads((FOLDER / "notes.json").read_text())
    sources = {
        AUTHOR: dict(
            id=AUTHOR,
            citation=(
                "Verbum · Five theme study guides / Cinco guias de estudo temático · 2026-10"
            ),
            url=None,
        )
    }
    provenance = {
        AUTHOR: dict(
            sourceId=AUTHOR,
            license="Original Verbum editorial prose",
            section=(
                "Bilingual contextual commentary; delegated AI review, not independent human review"
            ),
        )
    }
    studies, edges = [], []
    for row in rows:
        presentations = {}
        for language in ("en", "pt-BR"):
            p = dict(
                overview=dict(
                    text=row["overview"][language],
                    references=[reference(r) for r in row["overview"]["references"]],
                ),
                readings=[
                    dict(
                        id=r["id"],
                        reference=reference(r["reference"]),
                        **{
                            field: r[field][language]
                            for field in (
                                "title",
                                "context",
                                "observation",
                                "significance",
                                "question",
                            )
                        },
                    )
                    for r in row["readings"]
                ],
                comparisons=[
                    dict(
                        id=c["id"],
                        title=c["title"][language],
                        references=[reference(r) for r in c["references"]],
                        explanation=c["explanation"][language],
                        difference=c["difference"][language],
                    )
                    for c in row["comparisons"]
                ],
                connections=[
                    dict(
                        entityId=c["entityId"],
                        reference=reference(c["reference"]),
                        explanation=c["explanation"][language],
                    )
                    for c in row["connections"]
                ],
            )
            presentations[language] = p
        en = presentations["en"]
        refs = [
            *en["overview"]["references"],
            *(r["reference"] for r in en["readings"]),
            *(r for c in en["comparisons"] for r in c["references"]),
            *(c["reference"] for c in en["connections"]),
        ]
        ids = {AUTHOR}
        for value in refs:
            ref = Reference.model_validate(value)
            sid = source_id(ref)
            ids.add(sid)
            sources[sid] = dict(
                id=sid,
                citation=f"{BOOK_NAMES[ref.bookId]} {ref.chapter} · World English Bible",
                url=source_url(ref),
            )
            provenance[sid] = dict(
                sourceId="https://ebible.org/engwebp/",
                license="Public Domain",
                section=f"{ref.bookId} {ref.chapter}; https://ebible.org/engwebp/copyright.htm",
            )
        for p in presentations.values():
            p["sourceReferenceIds"] = sorted(ids)
        studies.append(dict(entityId=row["entityId"], presentations=presentations))
        for c in en["connections"]:
            edges.append(
                dict(
                    id=f"editorial.theme-study.{c['entityId']}.{row['entityId']}",
                    sourceId=c["entityId"],
                    targetId=row["entityId"],
                    type="relatedToTheme",
                    sourceReferenceIds=sorted(
                        [AUTHOR, source_id(Reference.model_validate(c["reference"]))]
                    ),
                )
            )
    bundle = Bundle.model_validate(
        dict(
            version=10,
            kind="editorial",
            themeStudies=studies,
            content=dict(
                entities=[],
                details=[],
                timeline=[],
                dailyVersePool=[],
                sources=list(sources.values()),
                relationships=edges,
                referencedEntityIds=sorted(THEMES | {r["sourceId"] for r in edges}),
            ),
            provenance=provenance,
            entitySources={},
        )
    )
    validate_references(bundle)
    return bundle


def validate_publication(cur, bundle):
    validate_references(bundle)
    cur.execute(
        "SELECT id,type FROM entities WHERE id=ANY(%s)", (bundle.content.referencedEntityIds,)
    )
    types = dict(cur.fetchall())
    if any(types.get(id_) != "theme" for id_ in THEMES) or any(
        types.get(id_) not in ("person", "place", "event")
        for id_ in set(bundle.content.referencedEntityIds) - THEMES
    ):
        raise ValueError("theme study identity missing or incompatible")
    for study in bundle.themeStudies:
        cur.execute("SELECT presentations FROM theme_studies WHERE entity_id=%s", (study.entityId,))
        current = cur.fetchone()
        proposed = {k: p.model_dump() for k, p in study.presentations.items()}
        if current and current[0] != proposed and digest(current[0]) != study.previousHash:
            raise ValueError("theme study changed since review; refresh its previousHash")
        if not current and study.previousHash is not None:
            raise ValueError("theme study to update is missing")
        if current and {c["entityId"] for c in current[0]["en"]["connections"]} != {
            c.entityId for c in study.presentations["en"].connections
        }:
            raise ValueError("theme study connection identities cannot change in this edition")
    pending = []
    for edge in bundle.content.relationships:
        cur.execute(
            """SELECT source_entity_id,target_entity_id,relationship_type,
            ARRAY(SELECT source_id FROM relationship_sources WHERE relationship_id=r.id
                  ORDER BY source_id) FROM relationships r WHERE id=%s""",
            (edge.id,),
        )
        current = cur.fetchone()
        if current and tuple(current) != (
            edge.sourceId,
            edge.targetId,
            edge.type,
            sorted(edge.sourceReferenceIds),
        ):
            raise ValueError("theme study relationship changed since review")
        cur.execute(
            """SELECT 1 FROM relationships WHERE source_entity_id=%s
            AND target_entity_id=%s AND relationship_type=%s LIMIT 1""",
            (edge.sourceId, edge.targetId, edge.type),
        )
        if not cur.fetchone():
            pending.append(edge)
    return pending


def write_studies(cur, bundle, pending_links):
    from .publish import reference as passage_columns
    from .publish import replace_links, upsert, write_relationships

    for study in bundle.themeStudies:
        upsert(
            cur,
            "theme_studies",
            dict(
                entity_id=study.entityId,
                presentations=Jsonb({k: p.model_dump() for k, p in study.presentations.items()}),
            ),
            "entity_id",
        )
        p = study.presentations["en"]
        replace_links(
            cur,
            "theme_study_sources",
            "entity_id",
            study.entityId,
            [
                dict(entity_id=study.entityId, source_id=sid, position=i)
                for i, sid in enumerate(p.sourceReferenceIds)
            ],
        )
        replace_links(
            cur,
            "theme_study_passages",
            "entity_id",
            study.entityId,
            [
                dict(entity_id=study.entityId, position=i, **passage_columns(r.reference))
                for i, r in enumerate(p.readings)
            ],
        )
    write_relationships(cur, pending_links)


if __name__ == "__main__":
    bundle = build()
    (FOLDER / "catalog.json").write_text(
        json.dumps(bundle.model_dump(), ensure_ascii=False, indent=2) + "\n"
    )
    print("Five bilingual theme studies; no publication performed")
