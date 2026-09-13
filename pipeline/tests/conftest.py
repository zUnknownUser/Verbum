import os
import uuid
from pathlib import Path

import psycopg
import pytest
from psycopg import sql
from psycopg.conninfo import make_conninfo

from verbum_pipeline.files import read, write
from verbum_pipeline.normalize import import_source, normalize
from verbum_pipeline.review import prepare

ROOT = Path(__file__).resolve().parents[2]


@pytest.fixture
def artifacts(tmp_path):
    imported = tmp_path / "imported.json"
    normalized = tmp_path / "normalized.json"
    queue = tmp_path / "queue.json"
    decisions = tmp_path / "decisions.json"
    import_source(ROOT / "backend/db/seed/fixtures.json", imported, fixtures=True)
    normalize(imported, normalized)
    prepare(normalized, queue, decisions)
    return normalized, queue, decisions


def approve_for_test(decisions, output):
    """Synthetic decisions exclusively for isolated tests, never editorial approval."""
    value = read(decisions)
    for decision in value["items"]:
        decision.update(
            status="approved", reviewer="synthetic-test-reviewer", reviewedAt="2026-01-01T00:00:00Z"
        )
    write(output, value)
    return output


@pytest.fixture
def database():
    raw = os.environ.get("VERBUM_TEST_DATABASE_URL")
    if not raw:
        pytest.skip("VERBUM_TEST_DATABASE_URL is required for PostgreSQL integration tests")
    schema = "verbum_pipeline_test_" + uuid.uuid4().hex
    with psycopg.connect(raw, autocommit=True) as admin:
        admin.execute(sql.SQL("CREATE SCHEMA {}").format(sql.Identifier(schema)))
        try:
            dsn = make_conninfo(raw, options=f"-c search_path={schema}")
            with psycopg.connect(dsn) as conn:
                for _ in range(2):
                    for path in sorted((ROOT / "backend/db/migrations").glob("*.sql")):
                        conn.execute(path.read_text(encoding="utf-8"))
            yield dsn
        finally:
            admin.execute(sql.SQL("DROP SCHEMA {} CASCADE").format(sql.Identifier(schema)))
