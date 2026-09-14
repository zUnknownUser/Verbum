"""Scripture text + embeddings for retrieval (§27-29), not for the reading feature.

This is deliberately outside the editorial Bundle/review flow in normalize.py/review.py/
publish.py. That flow exists because entities, relationships and details are *interpretive
claims* that need a human to check evidence (§32-34). Loading this table is not that: it copies
a translation's verse text byte-for-byte from a file already checked into this repository
(`VerbumKit/Sources/Clients/Resources/web.tsv`, the World English Bible, public domain) and
attaches a mechanical, deterministic embedding of that exact text. There is no claim to review.
The same reasoning already applies to `cmd/seed` (Go) importing the development fixtures.

License note (§34): only load a translation you have already confirmed is clear to use this
way; `--translation` is not a promise of anything, it is just a label.
"""

import csv
import os
from dataclasses import dataclass
from pathlib import Path
from typing import Protocol

import psycopg
from psycopg import sql

DEFAULT_MODEL = "text-embedding-3-large"
# pgvector's hnsw/ivfflat indexes reject vectors over 2000 dimensions; 3-large's native output is
# 3072. Requesting 1536 via OpenAI's `dimensions` parameter (Matryoshka truncation) keeps it
# indexable while OpenAI's own evaluations show it still beats 3-small at the same width. This
# width is baked into the `scripture_verses.embedding` column (db/migrations/0004); changing it
# here without a matching migration will fail every insert.
DEFAULT_DIMENSIONS = 1536


@dataclass(frozen=True)
class Verse:
    book_id: str
    chapter: int
    verse: int
    text: str


def read_tsv(path: Path, *, limit: int | None = None) -> list[Verse]:
    verses = []
    with path.open(encoding="utf-8", newline="") as stream:
        for row in csv.reader(stream, delimiter="\t"):
            if not row:
                continue
            book_id, chapter, verse, text = row
            verses.append(Verse(book_id, int(chapter), int(verse), text))
            if limit is not None and len(verses) >= limit:
                break
    if not verses:
        raise ValueError(f"no verses read from {path}")
    return verses


class Embedder(Protocol):
    def embed(self, *, model: str, texts: list[str], dimensions: int) -> list[list[float]]: ...


class OpenAIEmbedder:
    """Thin wrapper so the OpenAI SDK is only imported/used here, never elsewhere."""

    def __init__(self, api_key: str) -> None:
        import openai

        self._client = openai.OpenAI(api_key=api_key)

    def embed(self, *, model: str, texts: list[str], dimensions: int) -> list[list[float]]:
        try:
            response = self._client.embeddings.create(
                model=model, input=texts, dimensions=dimensions
            )
        except Exception as exc:  # SDK/network/auth errors; never leak the key
            raise ValueError(f"OpenAI embeddings request failed: {exc}") from exc
        by_index = sorted(response.data, key=lambda item: item.index)
        return [item.embedding for item in by_index]


def default_embedder() -> OpenAIEmbedder:
    api_key = os.environ.get("OPENAI_API_KEY")
    if not api_key:
        raise ValueError(
            "OPENAI_API_KEY is required for embed-scripture; run.ps1 sets it transiently from "
            "the DPAPI secret for that command only"
        )
    return OpenAIEmbedder(api_key)


def vector_literal(values: list[float]) -> str:
    return "[" + ",".join(repr(v) for v in values) + "]"


def upsert_batch(
    cur, translation: str, batch: list[Verse], vectors: list[list[float]]
) -> None:
    # One executemany per batch: psycopg pipelines it into a single round trip, which is
    # what makes a remote database (Railway from a laptop) take seconds per batch, not
    # minutes — the per-row execute it replaced cost one network round trip per verse.
    cur.executemany(
        sql.SQL(
            # public.vector, not vector: isolated test schemas don't put `public` on their
            # search_path, and the extension/type only exists there (see migration 0004).
            "INSERT INTO scripture_verses (translation,book_id,chapter,verse,text,embedding) "
            "VALUES (%s,%s,%s,%s,%s,%s::public.vector) "
            "ON CONFLICT (translation,book_id,chapter,verse) "
            "DO UPDATE SET text=EXCLUDED.text, embedding=EXCLUDED.embedding"
        ),
        [
            (translation, verse.book_id, verse.chapter, verse.verse, verse.text,
             vector_literal(vector))
            for verse, vector in zip(batch, vectors, strict=True)
        ],
    )


def batched(items: list, size: int):
    for start in range(0, len(items), size):
        yield items[start : start + size]


def load(
    tsv: Path,
    database_url: str,
    *,
    embedder: Embedder,
    model: str = DEFAULT_MODEL,
    dimensions: int = DEFAULT_DIMENSIONS,
    translation: str = "WEB",
    batch_size: int = 200,
    limit: int | None = None,
) -> dict:
    if batch_size < 1:
        raise ValueError("batch_size must be positive")
    verses = read_tsv(tsv, limit=limit)
    embedded = 0
    with psycopg.connect(database_url, connect_timeout=10) as conn:
        for batch in batched(verses, batch_size):
            vectors = embedder.embed(
                model=model, texts=[v.text for v in batch], dimensions=dimensions
            )
            if len(vectors) != len(batch):
                raise ValueError("embedding response size did not match the batch")
            if any(len(v) != dimensions for v in vectors):
                raise ValueError(f"embedding response did not have {dimensions} dimensions")
            with conn.cursor() as cur:
                cur.execute("SET LOCAL statement_timeout='60s'")
                upsert_batch(cur, translation, batch, vectors)
            conn.commit()
            embedded += len(batch)
    return {
        "translation": translation,
        "model": model,
        "dimensions": dimensions,
        "versesEmbedded": embedded,
    }
