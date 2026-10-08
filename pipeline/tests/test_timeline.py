import json
from pathlib import Path
import psycopg
import pytest
from verbum_pipeline.models import Bundle
from verbum_pipeline.review import prepare, require_approval
from verbum_pipeline.publish import publish
from verbum_pipeline.files import write
from .conftest import approve_for_test, ROOT

CATALOG = Path(__file__).resolve().parents[1] / 'sources/timeline/catalog.json'


def test_catalog_preserves_identity_and_has_bilingual_reading_paths():
    bundle = Bundle.model_validate_json(CATALOG.read_text())
    events = bundle.content.timeline
    original = json.loads((CATALOG.parent / 'legacy.json').read_text())['events']
    assert len(events) == 91
    assert {e['id'] for e in original} <= {e.id for e in events}
    assert len({p['en'].eraId for p in bundle.timelineDiscovery.presentations.values()}) == 10
    assert all(p['en'].keyPassages and p['pt-BR'].context for p in bundle.timelineDiscovery.presentations.values())
    ids = [e.id for e in events]
    assert ids.index('fixture.timeline.crucifixion') < ids.index('editorial.timeline.resurrection') < ids.index('fixture.timeline.early-church')
    assert all(e.startYear is None and e.datePrecision == 'unknown' for e in events)


def test_metadata_cannot_omit_language_or_disagree_with_reference():
    value = json.loads(CATALOG.read_text())
    first = next(iter(value['timelineDiscovery']['presentations'].values()))
    first.pop('pt-BR')
    with pytest.raises(ValueError, match='both languages'):
        Bundle.model_validate(value)
    value = json.loads(CATALOG.read_text())
    first = next(iter(value['timelineDiscovery']['presentations'].values()))
    first['pt-BR']['keyPassages'][0]['chapter'] = 2
    with pytest.raises(ValueError, match='disagree structurally'):
        Bundle.model_validate(value)


def test_publication_preserves_entities_and_is_idempotent(database, tmp_path, artifacts):
    normalized, _, decisions = artifacts
    publish(normalized, approve_for_test(decisions, tmp_path/'fixtures-approved.json'), database, allow_fixtures=True)
    with psycopg.connect(database) as conn:
        before = conn.execute('SELECT id,name,summary FROM entities ORDER BY id').fetchall()
    queue, decisions, approved = [tmp_path/f'{name}.json' for name in ('q','d','a')]
    prepare(CATALOG,queue,decisions)
    approve_for_test(decisions,approved)
    assert publish(CATALOG,approved,database)['status'] == 'published'
    assert publish(CATALOG,approved,database)['status'] == 'already_published'
    with psycopg.connect(database) as conn:
        assert conn.execute('SELECT id,name,summary FROM entities ORDER BY id').fetchall() == before
        assert conn.execute('SELECT count(*) FROM timeline_events').fetchone()[0] == 91
        assert conn.execute("SELECT discovery->'pt-BR'->>'title' FROM timeline_events WHERE id='fixture.timeline.exodus'").fetchone()[0] == 'A saída do Egito e a travessia do mar'
        assert conn.execute('SELECT count(*) FROM timeline_localizations').fetchone()[0] == 182


def test_missing_graph_links_roll_back_publication(database,tmp_path):
    q,d,a = [tmp_path/f'{name}.json' for name in ('q','d','a')]
    prepare(CATALOG,q,d);approve_for_test(d,a)
    with pytest.raises(ValueError,match='missing from this database'):
        publish(CATALOG,a,database)
    with psycopg.connect(database) as conn:
        assert conn.execute('SELECT count(*) FROM sources').fetchone()[0] == 0
        assert conn.execute('SELECT count(*) FROM content_publications').fetchone()[0] == 0
