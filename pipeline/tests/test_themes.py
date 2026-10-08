import json
from pathlib import Path

import psycopg
import pytest

from verbum_pipeline.models import Bundle
from verbum_pipeline.files import write
from verbum_pipeline.review import prepare
from verbum_pipeline.publish import publish
from .conftest import approve_for_test

CATALOG=Path(__file__).resolve().parents[1]/"sources/themes/catalog.json"

def test_theme_catalog_is_bilingual_and_preserves_existing_ids():
    bundle=Bundle.model_validate_json(CATALOG.read_text())
    assert len(bundle.content.entities)==80
    assert len(bundle.themes.presentations)==160
    assert len({c for cs in bundle.themes.categories.values() for c in cs})==8
    assert len([e for e in bundle.content.entities if e.id.startswith("fixture.")])==10
    assert all(len(d.keyPassages)==3 for d in bundle.content.details)
    assert any("worry" in p.aliases for p in bundle.themes.presentations if p.language=="en")
    assert any("preocupação" in p.aliases for p in bundle.themes.presentations if p.language=="pt-BR")

def test_theme_metadata_cannot_omit_language_or_use_unknown_category():
    value=json.loads(CATALOG.read_text());value["themes"]["presentations"].pop()
    with pytest.raises(ValueError,match="both languages"):
        Bundle.model_validate(value)
    value=json.loads(CATALOG.read_text());value["themes"]["categories"]["fixture.theme.faith"]=["unknown"]
    with pytest.raises(ValueError,match="invalid theme categories"):
        Bundle.model_validate(value)

def test_theme_publication_is_atomic_and_idempotent(database,tmp_path):
    queue,decisions,approved=[tmp_path/f"{name}.json" for name in ("queue","decisions","approved")]
    prepare(CATALOG,queue,decisions);approve_for_test(decisions,approved)
    assert publish(CATALOG,approved,database)["status"]=="published"
    assert publish(CATALOG,approved,database)["status"]=="already_published"
    with psycopg.connect(database) as conn:
        assert conn.execute("SELECT count(*) FROM entities WHERE type='theme'").fetchone()[0]==80
        assert conn.execute("SELECT count(*) FROM entity_localizations").fetchone()[0]==160
        assert conn.execute("SELECT count(*) FROM entity_key_passages").fetchone()[0]==240
        assert conn.execute("SELECT count(DISTINCT category_id) FROM theme_categories").fetchone()[0]==8
        assert conn.execute("SELECT name FROM entity_localizations WHERE entity_id='fixture.theme.faith' AND language='pt-BR'").fetchone()[0]=="Fé"
