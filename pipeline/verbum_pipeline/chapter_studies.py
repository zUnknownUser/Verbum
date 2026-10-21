"""Build a bounded, bilingual study batch using the shipped Scripture inventory.

The notes are original editorial paraphrases of cited passages. No generation,
network call, guessed historical date or replacement of other catalogues occurs.
"""

import json
from collections import defaultdict
from pathlib import Path

from .models import Bundle, Reference

ROOT = Path(__file__).resolve().parents[1]
AUTHOR = "editorial.chapter-studies.exodus.2026-10"
BOOK_NAMES = {
    "Exod": "Exodus",
    "Num": "Numbers",
    "Deut": "Deuteronomy",
    "Gen": "Genesis",
    "John": "John",
    "Josh": "Joshua",
    "Ps": "Psalms",
    "1Cor": "1 Corinthians",
    "Heb": "Hebrews",
    "2Kgs": "2 Kings",
}
BOOK_FILES = {
    "Exod": "EXO",
    "Num": "NUM",
    "Deut": "DEU",
    "Gen": "GEN",
    "John": "JHN",
    "Josh": "JOS",
    "Ps": "PSA",
    "1Cor": "1CO",
    "Heb": "HEB",
    "2Kgs": "2KI",
}


def reference(value: str) -> dict:
    book, chapter, *verses = value.split(".")
    result = dict(bookId=book, chapter=int(chapter), verseStart=None, verseEnd=None)
    if verses:
        start, _, end = verses[0].partition("-")
        result.update(verseStart=int(start), verseEnd=int(end or start))
    return result


def source_id(ref: Reference) -> str:
    return f"study.scripture.{ref.bookId}.{ref.chapter}"


def study_references(presentation):
    for field in ("summary", "participants", "setting", "literaryContext"):
        yield from getattr(presentation, field).references
    yield from (item.reference for item in presentation.outline)
    yield from (item.reference for item in presentation.connections)


def validate_references(bundle: Bundle) -> None:
    verses = defaultdict(set)
    corpus = ROOT.parent / "VerbumKit/Sources/Clients/Resources/web.tsv"
    for line in corpus.read_text().splitlines():
        book, chapter, verse, _ = line.split("\t", 3)
        verses[book, int(chapter)].add(int(verse))
    sources = {s.id: s for s in bundle.content.sources}
    for study in bundle.chapterStudies:
        p = study.presentations["en"]
        refs = list(study_references(p))
        expected_sources = {AUTHOR, *(source_id(ref) for ref in refs)}
        if set(p.sourceReferenceIds) != expected_sources:
            raise ValueError("study must attribute exactly its editorial and Scripture sources")
        for ref in [study.reference, *refs]:
            available = verses[ref.bookId, ref.chapter]
            if not available or (
                ref.verseStart is not None
                and not set(range(ref.verseStart, ref.verseEnd + 1)) <= available
            ):
                raise ValueError(f"study references missing Scripture: {ref}")
        covered = []
        for part in p.outline:
            ref = part.reference
            if ref.verseStart is None:
                raise ValueError("study outline needs verse ranges")
            covered.extend(range(ref.verseStart, ref.verseEnd + 1))
        if covered != sorted(verses[study.reference.bookId, study.reference.chapter]):
            raise ValueError("outline must cover its chapter once in reading order")
        for ref in refs:
            source = sources[source_id(ref)]
            url = f"https://ebible.org/engwebp/{BOOK_FILES[ref.bookId]}{ref.chapter:02}.htm"
            if source.url != url:
                raise ValueError("Scripture source must open the cited chapter")


def build() -> Bundle:
    rows = json.loads((ROOT / "sources/chapter-studies/exodus-notes.json").read_text())
    sources = {
        AUTHOR: dict(
            id=AUTHOR,
            citation="Verbum · Exodus chapter studies / Estudos de capítulos do Êxodo · 2026-10",
            url=None,
        )
    }
    provenance = {
        AUTHOR: dict(
            sourceId=AUTHOR,
            license="Original Verbum editorial prose",
            section="Bilingual notes; delegated AI editorial review, not independent human review",
        )
    }
    studies = []
    for row in rows:
        presentations = {}
        refs = []
        for language in ("en", "pt-BR"):
            p = dict(title=row["title"][language])
            for field in ("summary", "participants", "setting", "literaryContext"):
                note = row[field]
                p[field] = dict(
                    text=note[language], references=[reference(r) for r in note["references"]]
                )
                refs.extend(p[field]["references"])
            p["outline"] = [
                dict(title=o[language], reference=reference(o["reference"])) for o in row["outline"]
            ]
            p["connections"] = [
                dict(explanation=c[language], reference=reference(c["reference"]))
                for c in row["connections"]
            ]
            refs.extend(item["reference"] for item in p["outline"] + p["connections"])
            presentations[language] = p
        ids = {AUTHOR}
        for value in refs:
            ref = Reference.model_validate(value)
            sid = source_id(ref)
            ids.add(sid)
            sources[sid] = dict(
                id=sid,
                citation=f"{BOOK_NAMES[ref.bookId]} {ref.chapter} · World English Bible",
                url=f"https://ebible.org/engwebp/{BOOK_FILES[ref.bookId]}{ref.chapter:02}.htm",
            )
            provenance[sid] = dict(
                sourceId="https://ebible.org/engwebp/",
                license="Public Domain",
                section=f"{ref.bookId} {ref.chapter}; https://ebible.org/engwebp/copyright.htm",
            )
        for p in presentations.values():
            p["sourceReferenceIds"] = sorted(ids)
        studies.append(dict(reference=reference(row["reference"]), presentations=presentations))
    bundle = Bundle.model_validate(
        dict(
            version=8,
            kind="editorial",
            chapterStudies=studies,
            content=dict(
                sources=list(sources.values()),
                entities=[],
                relationships=[],
                details=[],
                timeline=[],
                dailyVersePool=[],
            ),
            provenance=provenance,
            entitySources={},
        )
    )
    validate_references(bundle)
    return bundle


if __name__ == "__main__":
    bundle = build()
    output = ROOT / "sources/chapter-studies/exodus.json"
    output.write_text(json.dumps(bundle.model_dump(), ensure_ascii=False, indent=2) + "\n")
    print(f"{len(bundle.chapterStudies)} chapter studies, EN/PT-BR; no publication performed")
