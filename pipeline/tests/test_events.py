import json
from pathlib import Path
import psycopg
import pytest
from verbum_pipeline.files import write
from verbum_pipeline.models import Bundle
from verbum_pipeline.publish import publish
from verbum_pipeline.review import prepare, require_approval
from .conftest import approve_for_test

ROOT=Path(__file__).resolve().parents[1]
CATALOG=ROOT/'sources/events/catalog.json'
TIMELINE=ROOT/'sources/timeline/catalog.json'


def test_catalog_has_unique_bilingual_events_and_preserves_legacy():
    b=Bundle.model_validate_json(CATALOG.read_text())
    assert len(b.content.entities)==137
    assert len(b.eventCatalog.presentations)==274
    assert len(b.eventCatalog.timelineBindings)==86
    assert len(b.eventCatalog.eras)==137 and len(set(b.eventCatalog.eras.values()))==10
    assert len({e.name for e in b.content.entities})==137
    assert {e.id for e in b.content.entities if e.id.startswith('fixture.')}=={'fixture.event.exodus','fixture.event.david-goliath','fixture.event.david-takes-jerusalem','fixture.event.david-anointed'}
    assert sum(len(d.keyPassages) for d in b.content.details)==171
    assert not b.content.timeline
    assert all('\n\n' in p.summary for p in b.eventCatalog.presentations)


def test_missing_translation_or_duplicate_binding_is_rejected():
    value=json.loads(CATALOG.read_text());value['eventCatalog']['presentations'].pop()
    with pytest.raises(ValueError,match='both languages'): Bundle.model_validate(value)
    value=json.loads(CATALOG.read_text());bindings=value['eventCatalog']['timelineBindings'];keys=list(bindings)
    bindings[keys[1]]=bindings[keys[0]]
    with pytest.raises(ValueError,match='duplicate timeline event bindings'): Bundle.model_validate(value)


def test_existing_reviews_still_match_serialization():
    for source, review in [('timeline/catalog.json','timeline-2026-10-08.json'),('themes/catalog.json','themes-2026-10-08.json')]:
        require_approval(Bundle.model_validate_json((ROOT/'sources'/source).read_text()),ROOT/'review'/review)


def approve_publish(source,tmp_path,name,database,fixtures=False):
    q,d,a=[tmp_path/f'{name}-{x}.json' for x in ['q','d','a']]
    prepare(source,q,d);approve_for_test(d,a)
    return publish(source,a,database,allow_fixtures=fixtures),a


def test_event_publication_is_atomic_and_preserves_timeline(database,tmp_path,artifacts):
    approve_publish(artifacts[0],tmp_path,'fixture',database,True)
    approve_publish(TIMELINE,tmp_path,'timeline',database)
    with psycopg.connect(database) as c:
        before=c.execute('SELECT id,title,discovery FROM timeline_events ORDER BY id').fetchall()
        people=c.execute("SELECT id,name,summary FROM entities WHERE type<>'event' ORDER BY id").fetchall()
    result,approved=approve_publish(CATALOG,tmp_path,'events',database)
    assert result['status']=='published'
    assert publish(CATALOG,approved,database)['status']=='already_published'
    with psycopg.connect(database) as c:
        assert c.execute("SELECT count(*) FROM entities WHERE type='event'").fetchone()[0]==137
        assert c.execute('SELECT count(*) FROM timeline_event_catalog').fetchone()[0]==86
        assert c.execute('SELECT count(*) FROM event_eras').fetchone()[0]==137
        assert c.execute('SELECT id,title,discovery FROM timeline_events ORDER BY id').fetchall()==before
        assert c.execute("SELECT id,name,summary FROM entities WHERE type<>'event' ORDER BY id").fetchall()==people
        assert c.execute("SELECT count(*) FROM entity_localizations WHERE source_id='editorial.events.2026-10'").fetchone()[0]==274
        assert c.execute("SELECT entity_id FROM timeline_event_catalog WHERE event_id='editorial.timeline.david-goliath'").fetchone()[0]=='fixture.event.david-goliath'


def test_missing_timeline_target_does_not_write_events(database,tmp_path,artifacts):
    approve_publish(artifacts[0],tmp_path,'fixture',database,True)
    with pytest.raises(ValueError,match='missing timeline entries'):
        approve_publish(CATALOG,tmp_path,'events',database)
    with psycopg.connect(database) as c:
        assert c.execute("SELECT count(*) FROM entities WHERE type='event'").fetchone()[0]==4
        assert c.execute("SELECT count(*) FROM sources WHERE id='editorial.events.2026-10'").fetchone()[0]==0


def test_mismatched_era_rolls_back_without_new_entities(database,tmp_path,artifacts):
    approve_publish(artifacts[0],tmp_path,'fixture',database,True)
    approve_publish(TIMELINE,tmp_path,'timeline',database)
    value=json.loads(CATALOG.read_text())
    value['eventCatalog']['eras']['fixture.event.exodus']='jesus'
    source=tmp_path/'wrong-era.json';write(source,value)
    with pytest.raises(ValueError,match='disagree on era'):
        approve_publish(source,tmp_path,'wrong',database)
    with psycopg.connect(database) as c:
        assert c.execute("SELECT count(*) FROM entities WHERE type='event'").fetchone()[0]==4
