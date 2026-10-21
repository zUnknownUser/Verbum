"""Sourced EN/PT-BR profiles of existing Exodus people and places.

No new entity, date, translation-service call or destructive canonical update.
The same EntityDetail contract is consumed by both native clients.
"""

import json
from collections import defaultdict

from psycopg.types.json import Jsonb

from .chapter_studies import BOOK_FILES, BOOK_NAMES, ROOT, reference, source_id
from .files import digest
from .models import Bundle, EntityProfile, Reference

AUTHOR = "editorial.profiles.exodus.2026-10"
FOLDER = ROOT / "sources/entity-profiles"


def profile_hash(profile):
    return digest(profile.model_dump(exclude={"previousHash"}))


def build() -> Bundle:
    identities = {r["entityId"]: r for r in json.loads((FOLDER / "identities.json").read_text())}
    rows = [row for kind in ("people", "places")
            for row in json.loads((FOLDER / f"{kind}-notes.json").read_text())]
    sources = {AUTHOR: dict(id=AUTHOR, citation=(
        "Verbum · Exodus people and places / Pessoas e lugares do Êxodo · 2026-10"
    ), url=None)}
    provenance = {AUTHOR: dict(
        sourceId=AUTHOR, license="Original Verbum editorial prose",
        section="Bilingual paraphrases; existing STEP identities retained (CC BY 4.0)",
    )}

    def evidence(refs):
        ids = {AUTHOR}
        for ref in refs:
            sid = source_id(ref)
            ids.add(sid)
            sources[sid] = dict(
                id=sid, citation=f"{BOOK_NAMES[ref.bookId]} {ref.chapter} · World English Bible",
                url=f"https://ebible.org/engwebp/{BOOK_FILES[ref.bookId]}{ref.chapter:02}.htm",
            )
            provenance[sid] = dict(
                sourceId="https://ebible.org/engwebp/", license="Public Domain",
                section=f"{ref.bookId} {ref.chapter}; https://ebible.org/engwebp/copyright.htm",
            )
        return sorted(ids)

    profiles = []
    for row in rows:
        refs = list(dict.fromkeys(r for p in row["paragraphs"] for r in p["references"]))
        passages = [Reference.model_validate(reference(r)) for r in refs]
        profiles.append(dict(
            **identities[row["entityId"]],
            presentations={lang: dict(
                name=row["name"][lang], aliases=row["aliases"][lang],
                role=row["role"][lang], modernGeography=row["modernGeography"][lang],
                summary="\n\n".join(p[lang] for p in row["paragraphs"]),
            ) for lang in ("en", "pt-BR")},
            keyPassages=[r.model_dump() for r in passages],
            sourceReferenceIds=evidence(passages),
        ))
    links = []
    for row in json.loads((FOLDER / "links.json").read_text()):
        a, b, kind = row["sourceId"], row["targetId"], row["type"]
        links.append(dict(
            id=f"editorial.profile.{a}.{kind}.{b}", sourceId=a, targetId=b, type=kind,
            sourceReferenceIds=evidence([
                Reference.model_validate(reference(r)) for r in row["references"]
            ]),
        ))
    referenced = set(identities) | {v for r in links for v in (r["sourceId"], r["targetId"])}
    bundle = Bundle.model_validate(dict(
        version=9, kind="editorial", entityProfiles=profiles,
        content=dict(sources=list(sources.values()), entities=[], details=[],
                     relationships=links, referencedEntityIds=sorted(referenced),
                     timeline=[], dailyVersePool=[]),
        provenance=provenance, entitySources={},
    ))
    validate_references(bundle)
    return bundle


def validate_references(bundle):
    verses = defaultdict(set)
    corpus = ROOT.parent / "VerbumKit/Sources/Clients/Resources/web.tsv"
    for line in corpus.read_text().splitlines():
        book, chapter, verse, _ = line.split("\t", 3)
        verses[book, int(chapter)].add(int(verse))
    sources = {s.id: s for s in bundle.content.sources}
    for profile in bundle.entityProfiles:
        expected = {AUTHOR, *(source_id(r) for r in profile.keyPassages)}
        if set(profile.sourceReferenceIds) != expected:
            raise ValueError("profile must attribute exactly its Scripture and editorial sources")
        for ref in profile.keyPassages:
            available = verses[ref.bookId, ref.chapter]
            if not available or (ref.verseStart is not None and
                not set(range(ref.verseStart, ref.verseEnd + 1)) <= available):
                raise ValueError("profile references missing Scripture")
    for sid, source in sources.items():
        if sid == AUTHOR:
            continue
        parts = sid.split(".")
        if len(parts) != 4 or parts[:2] != ["study", "scripture"]:
            raise ValueError("unrecognized profile source")
        book, chapter = parts[2], int(parts[3])
        if not verses[book, chapter]:
            raise ValueError("profile source references a missing chapter")
        if source.url != f"https://ebible.org/engwebp/{BOOK_FILES[book]}{chapter:02}.htm":
            raise ValueError("profile source must open the cited chapter")


def validate_publication(cur, bundle):
    validate_references(bundle)
    for profile in bundle.entityProfiles:
        cur.execute("""SELECT e.type,r.entity_id,r.external_id,r.identifiers->'uniqueName'
            FROM entity_source_records r JOIN entities e ON e.id=r.entity_id
            WHERE r.id=%s AND r.source_id='step.TIPNR'""", (profile.recordId,))
        row = cur.fetchone()
        if not row or tuple(row[:3]) != (profile.type, profile.entityId, profile.externalId) or \
                profile.uniqueName not in row[3]:
            raise ValueError(f"profile identity missing or changed: {profile.entityId}")
        cur.execute("SELECT presentations FROM entity_profiles WHERE entity_id=%s",
                    (profile.entityId,))
        existing = cur.fetchone()
        if existing:
            cur.execute("""SELECT book_id,chapter,verse_start,verse_end
                FROM entity_profile_passages WHERE entity_id=%s ORDER BY position""",
                        (profile.entityId,))
            passages = [Reference(bookId=b, chapter=c, verseStart=a, verseEnd=z)
                        for b, c, a, z in cur.fetchall()]
            cur.execute("""SELECT source_id FROM entity_profile_sources
                WHERE entity_id=%s ORDER BY position""", (profile.entityId,))
            current = profile.model_dump()
            current.update(presentations=existing[0],
                           keyPassages=[r.model_dump() for r in passages],
                           sourceReferenceIds=[r[0] for r in cur.fetchall()])
            if profile_hash(EntityProfile.model_validate(current)) not in (
                profile_hash(profile), profile.previousHash
            ):
                raise ValueError("profile changed since review; refresh its previousHash")
        if not existing and profile.previousHash is not None:
            raise ValueError("profile to update is missing")
    # Links are additive and immutable in this release. No silent replacement of evidence.
    pending_links = []
    for link in bundle.content.relationships:
        cur.execute("""SELECT source_entity_id,target_entity_id,relationship_type,
            ARRAY(SELECT source_id FROM relationship_sources WHERE relationship_id=r.id
                  ORDER BY source_id) FROM relationships r WHERE id=%s""", (link.id,))
        existing = cur.fetchone()
        if existing and tuple(existing) != (
            link.sourceId, link.targetId, link.type, sorted(link.sourceReferenceIds)
        ):
            raise ValueError("profile relationship changed since review")
        cur.execute("""SELECT 1 FROM relationships WHERE relationship_type=%s AND
            ((source_entity_id=%s AND target_entity_id=%s) OR
             (%s='relatedTo' AND source_entity_id=%s AND target_entity_id=%s)) LIMIT 1""",
                    (link.type, link.sourceId, link.targetId,
                     link.type, link.targetId, link.sourceId))
        if not cur.fetchone():
            pending_links.append(link)
    return pending_links


def write_profiles(cur, bundle, pending_links):
    from .publish import reference as passage_columns
    from .publish import replace_links, upsert, write_relationships

    for profile in bundle.entityProfiles:
        upsert(cur, "entity_profiles", dict(
            entity_id=profile.entityId,
            presentations=Jsonb({k: p.model_dump() for k, p in profile.presentations.items()}),
        ), "entity_id")
        replace_links(cur, "entity_profile_sources", "entity_id", profile.entityId, [
            dict(entity_id=profile.entityId, source_id=sid, position=i)
            for i, sid in enumerate(profile.sourceReferenceIds)
        ])
        replace_links(cur, "entity_profile_passages", "entity_id", profile.entityId, [
            dict(entity_id=profile.entityId, position=i, **passage_columns(ref))
            for i, ref in enumerate(profile.keyPassages)
        ])
    write_relationships(cur, pending_links)


if __name__ == "__main__":
    bundle = build()
    (FOLDER / "exodus.json").write_text(
        json.dumps(bundle.model_dump(), ensure_ascii=False, indent=2) + "\n"
    )
    print(f"{len(bundle.entityProfiles)} bilingual profiles; no publication performed")
