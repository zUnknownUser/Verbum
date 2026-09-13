from pathlib import Path

import pytest

from verbum_pipeline import scripture


def write_tsv(path: Path, rows: list[tuple[str, int, int, str]]) -> Path:
    path.write_text(
        "\n".join(f"{b}\t{c}\t{v}\t{t}" for b, c, v, t in rows) + "\n", encoding="utf-8"
    )
    return path


class FakeEmbedder:
    def __init__(self, dim: int = scripture.DEFAULT_DIMENSIONS):
        self.dim = dim
        self.calls: list[list[str]] = []

    def embed(self, *, model, texts, dimensions):
        assert dimensions == self.dim
        self.calls.append(list(texts))
        # Deterministic, distinct-enough vectors: hash-derived, not all-identical.
        return [[float((hash((text, i)) % 1000) / 1000) for i in range(self.dim)] for text in texts]


def test_read_tsv_parses_rows(tmp_path):
    rows = [("Gen", 1, 1, "In the beginning."), ("Gen", 1, 2, "And so on.")]
    verses = scripture.read_tsv(write_tsv(tmp_path / "web.tsv", rows))
    assert verses == [
        scripture.Verse("Gen", 1, 1, "In the beginning."),
        scripture.Verse("Gen", 1, 2, "And so on."),
    ]


def test_read_tsv_respects_limit(tmp_path):
    path = write_tsv(tmp_path / "web.tsv", [("Gen", 1, n, f"verse {n}") for n in range(1, 6)])
    assert len(scripture.read_tsv(path, limit=2)) == 2


def test_read_tsv_rejects_empty_file(tmp_path):
    path = tmp_path / "empty.tsv"
    path.write_text("", encoding="utf-8")
    with pytest.raises(ValueError, match="no verses"):
        scripture.read_tsv(path)


def test_batched_splits_evenly_and_with_remainder():
    assert list(scripture.batched([1, 2, 3, 4, 5], 2)) == [[1, 2], [3, 4], [5]]
    assert list(scripture.batched([], 2)) == []


def test_vector_literal_is_bracketed_csv():
    assert scripture.vector_literal([1.0, -0.5, 0.25]) == "[1.0,-0.5,0.25]"


def test_default_embedder_requires_api_key(monkeypatch):
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)
    with pytest.raises(ValueError, match="OPENAI_API_KEY"):
        scripture.default_embedder()


@pytest.mark.postgres
def test_load_rejects_mismatched_embedding_count(tmp_path, database):
    path = write_tsv(tmp_path / "web.tsv", [("Gen", 1, 1, "text")])

    class ShortEmbedder:
        def embed(self, *, model, texts, dimensions):
            return []

    with pytest.raises(ValueError, match="did not match the batch"):
        scripture.load(path, database, embedder=ShortEmbedder())


@pytest.mark.postgres
def test_load_upserts_verses_with_embeddings(tmp_path, database):
    path = write_tsv(
        tmp_path / "web.tsv",
        [
            ("1Sam", 17, 48, "The Philistine drew near."),
            ("1Sam", 17, 49, "David struck the Philistine."),
            ("1Sam", 17, 50, "David prevailed."),
        ],
    )
    embedder = FakeEmbedder()
    result = scripture.load(path, database, embedder=embedder, batch_size=2)
    assert result == {
        "translation": "WEB",
        "model": scripture.DEFAULT_MODEL,
        "dimensions": scripture.DEFAULT_DIMENSIONS,
        "versesEmbedded": 3,
    }
    assert [len(call) for call in embedder.calls] == [2, 1]

    import psycopg

    with psycopg.connect(database) as conn:
        rows = conn.execute(
            "SELECT book_id, chapter, verse, text, embedding IS NOT NULL "
            "FROM scripture_verses ORDER BY verse"
        ).fetchall()
    assert rows == [
        ("1Sam", 17, 48, "The Philistine drew near.", True),
        ("1Sam", 17, 49, "David struck the Philistine.", True),
        ("1Sam", 17, 50, "David prevailed.", True),
    ]


@pytest.mark.postgres
def test_load_is_idempotent_on_repeat_run(tmp_path, database):
    path = write_tsv(tmp_path / "web.tsv", [("Gen", 1, 1, "In the beginning.")])
    scripture.load(path, database, embedder=FakeEmbedder())
    result = scripture.load(path, database, embedder=FakeEmbedder())
    assert result["versesEmbedded"] == 1

    import psycopg

    with psycopg.connect(database) as conn:
        count = conn.execute("SELECT count(*) FROM scripture_verses").fetchone()[0]
    assert count == 1
