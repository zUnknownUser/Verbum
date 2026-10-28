"""Bounded book introductions, authored in EN/PT-BR; never generated at read time."""

import json
import re
from pathlib import Path

from psycopg.types.json import Jsonb

from .files import digest

ROOT = Path(__file__).resolve().parents[2]
DIRECTORY = ROOT / "pipeline/sources/book-orientations"
AUTHOR = "editorial.book-orientations.2026-10"
FILES = (
    "GEN EXO LEV NUM DEU JOS JDG RUT 1SA 2SA 1KI 2KI 1CH 2CH EZR NEH EST JOB "
    "PSA PRO ECC SNG ISA JER LAM EZK DAN HOS JOL AMO OBA JON MIC NAM HAB ZEP "
    "HAG ZEC MAL MAT MRK LUK JHN ACT ROM 1CO 2CO GAL EPH PHP COL 1TH 2TH 1TI "
    "2TI TIT PHM HEB JAS 1PE 2PE 1JN 2JN 3JN JUD REV"
).split()


def canon():
    source = (ROOT / "VerbumKit/Sources/Models/BibleBook.swift").read_text()
    rows = re.findall(r'BibleBook\(id: "([^"]+)", name: "([^"]+)".*?chapterCount: (\d+)\)', source)
    if len(rows) != 66:
        raise ValueError("expected the existing 66-book canon")
    return {
        id_: (name, int(count), code) for (id_, name, count), code in zip(rows, FILES, strict=True)
    }


def validate_catalog(bundle):
    books = canon()
    if bundle.kind != "editorial" or any(
        (
            bundle.content.entities,
            bundle.content.relationships,
            bundle.content.details,
            bundle.content.timeline,
            bundle.content.dailyVersePool,
            bundle.content.referencedEntityIds,
            bundle.entitySources,
        )
    ):
        raise ValueError("book orientations cannot change other catalogues")
    orientations = bundle.bookOrientations
    if len(orientations) != 66 or {b.bookId for b in orientations} != set(books):
        raise ValueError("book orientations must cover exactly the 66 books")
    source_map = {s.id: s for s in bundle.content.sources}
    expected_sources = {AUTHOR, *(f"orientation.scripture.{id_}" for id_ in books)}
    if set(source_map) != expected_sources:
        raise ValueError("book orientations require exact Scripture and editorial sources")
    for book in orientations:
        name, count, code = books[book.bookId]
        if book.chapterCount != count or set(book.presentations) != {"en", "pt-BR"}:
            raise ValueError("book chapter count or languages mismatch")
        for p in book.presentations.values():
            chapters = [
                ch for part in p.outline for ch in range(part.chapterStart, part.chapterEnd + 1)
            ]
            if any(part.chapterEnd < part.chapterStart for part in p.outline) or chapters != list(
                range(1, count + 1)
            ):
                raise ValueError("book outline must cover every chapter exactly once in order")
            if len(p.readings) != len(set(str(r.model_dump()) for r in p.readings)):
                raise ValueError("duplicate introductory readings")
            if any(
                r.bookId != book.bookId or not 1 <= r.chapter <= count or r.verseStart is not None
                for r in p.readings
            ):
                raise ValueError("introductory readings must be chapters of this book")
            if p.sourceReferenceIds != [AUTHOR, f"orientation.scripture.{book.bookId}"]:
                raise ValueError("book sources must match its evidence")
        en, pt = book.presentations["en"], book.presentations["pt-BR"]
        if en.readings != pt.readings or [(s.chapterStart, s.chapterEnd) for s in en.outline] != [
            (s.chapterStart, s.chapterEnd) for s in pt.outline
        ]:
            raise ValueError("book navigation differs between languages")
        if (
            source_map[f"orientation.scripture.{book.bookId}"].url
            != f"https://ebible.org/engwebp/{code}.htm"
        ):
            raise ValueError("book source must link to its Scripture contents")


def build():
    from .models import Bundle

    books = canon()
    sources = [
        dict(
            id=AUTHOR,
            citation="Verbum · Book orientations / Orientações dos livros · 2026-10",
            url=None,
        )
    ]
    provenance = {
        AUTHOR: dict(
            sourceId=AUTHOR,
            license="Original Verbum editorial prose",
            section=(
                "AI-authored bilingual literary reading guides; not independent human review. "
                "No composition dates or disputed author identifications asserted."
            ),
        )
    }
    orientations = []
    for line in (DIRECTORY / "notes.psv").read_text().splitlines():
        id_, genre, context, purpose, outline, readings = line.split("|")
        name, count, code = books[id_]
        sid = f"orientation.scripture.{id_}"
        sources.append(
            dict(
                id=sid,
                citation=f"{name} 1–{count} · World English Bible",
                url=f"https://ebible.org/engwebp/{code}.htm",
            )
        )
        provenance[sid] = dict(
            sourceId="https://ebible.org/engwebp/",
            license="Public Domain",
            section=(
                f"{id_} 1–{count}; literary summary of the biblical text, "
                "not an external historical commentary. https://ebible.org/engwebp/copyright.htm"
            ),
        )
        presentations = {}
        for i, language in enumerate(("en", "pt-BR")):
            sections = []
            for part in outline.split(";"):
                span, title = part.split(":")
                start, end = map(int, span.split("-"))
                sections.append(dict(title=title.split("~")[i], chapterStart=start, chapterEnd=end))
            presentations[language] = dict(
                genre=genre.split("~")[i],
                context=context.split("~")[i],
                purpose=purpose.split("~")[i],
                outline=sections,
                readings=[dict(bookId=id_, chapter=int(ch)) for ch in readings.split(",")],
                sourceReferenceIds=[AUTHOR, sid],
            )
        orientations.append(dict(bookId=id_, chapterCount=count, presentations=presentations))
    return Bundle.model_validate(
        dict(
            version=11,
            kind="editorial",
            bookOrientations=orientations,
            content=dict(
                sources=sources,
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


def check_existing(cur, bundle):
    validate_catalog(bundle)
    for book in bundle.bookOrientations:
        cur.execute("SELECT presentations FROM book_orientations WHERE book_id=%s", (book.bookId,))
        existing = cur.fetchone()
        proposed = {lang: p.model_dump() for lang, p in book.presentations.items()}
        if existing and existing[0] != proposed and digest(existing[0]) != book.previousHash:
            raise ValueError("book orientation changed since review; refresh its previousHash")
        if not existing and book.previousHash is not None:
            raise ValueError("book orientation to update is missing")


def write_books(cur, bundle):
    for book in bundle.bookOrientations:
        cur.execute(
            """INSERT INTO book_orientations(book_id,chapter_count,presentations) VALUES(%s,%s,%s)
            ON CONFLICT(book_id) DO UPDATE SET chapter_count=EXCLUDED.chapter_count,
            presentations=EXCLUDED.presentations""",
            (
                book.bookId,
                book.chapterCount,
                Jsonb({lang: p.model_dump() for lang, p in book.presentations.items()}),
            ),
        )
        cur.execute("DELETE FROM book_orientation_sources WHERE book_id=%s", (book.bookId,))
        cur.executemany(
            "INSERT INTO book_orientation_sources(book_id,source_id,position) VALUES(%s,%s,%s)",
            [
                (book.bookId, sid, i)
                for i, sid in enumerate(book.presentations["en"].sourceReferenceIds)
            ],
        )


if __name__ == "__main__":
    bundle = build()
    (DIRECTORY / "catalog.json").write_text(
        json.dumps(bundle.model_dump(), indent=2, ensure_ascii=False) + "\n"
    )
    print("66 bilingual book orientations built; no publication performed")
