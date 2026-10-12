import json
import psycopg
import pytest
from verbum_pipeline.models import Bundle
from verbum_pipeline.files import write
from verbum_pipeline.review import require_approval
from .test_events import ROOT, CATALOG, TIMELINE, approve_publish

SEQUENCE=ROOT/'sources/sequences/exodus.json'

def test_sequence_is_bounded_and_reuses_canonical_events():
    b=Bundle.model_validate_json(SEQUENCE.read_text())
    old=Bundle.model_validate_json(TIMELINE.read_text())
    events=Bundle.model_validate_json(CATALOG.read_text())
    s=b.eventSequences[0]
    assert len(s.items)==16 and len(b.content.timeline)==98
    assert {i.entityId for i in s.items}=={id for id,era in events.eventCatalog.eras.items() if era=='exodus'}
    assert s.items[0].entityId=='editorial.event.moses-born'
    assert s.items[-1].entityId=='editorial.event.moses-farewell'
    assert not b.content.entities and not b.content.details
    existing={e.id:e for e in b.content.timeline}
    assert all(existing[e.id]==e for e in old.content.timeline)
    assert [e.id for e in b.content.timeline if b.timelineDiscovery.presentations[e.id]['en'].eraId=='exodus']==[i.timelineId for i in s.items]
    require_approval(events,ROOT/'review/events-2026-10-08.json')

@pytest.mark.parametrize('mutation',['duplicate','era','translation'])
def test_invalid_sequence_rejected(mutation):
    b=json.loads(SEQUENCE.read_text());s=b['eventSequences'][0]
    if mutation=='duplicate':s['items'].append(s['items'][0])
    elif mutation=='era':s['eraId']='jesus'
    else:del s['presentations']['pt-BR']
    with pytest.raises(ValueError):Bundle.model_validate(b)

def test_sequence_publish_is_atomic_idempotent_and_preserves_events(database,tmp_path,artifacts):
    approve_publish(artifacts[0],tmp_path,'fixture',database,True)
    approve_publish(TIMELINE,tmp_path,'timeline',database)
    approve_publish(CATALOG,tmp_path,'events',database)
    with psycopg.connect(database) as c:
        before=c.execute('SELECT id,type,name,summary FROM entities ORDER BY id').fetchall()
    result,review=approve_publish(SEQUENCE,tmp_path,'sequence',database)
    assert result['status']=='published'
    from verbum_pipeline.publish import publish
    assert publish(SEQUENCE,review,database)['status']=='already_published'
    with psycopg.connect(database) as c:
        assert c.execute('SELECT id,type,name,summary FROM entities ORDER BY id').fetchall()==before
        assert c.execute('SELECT count(*) FROM timeline_events').fetchone()[0]==98
        assert c.execute('SELECT count(*) FROM timeline_event_catalog').fetchone()[0]==93
        assert c.execute('SELECT count(*) FROM event_sequence_items').fetchone()[0]==16
    # A partial sequence must fail before writing any content.
    bad=json.loads(SEQUENCE.read_text());bad['eventSequences'][0]['items'].pop();bad['content']['timeline']=[e for e in bad['content']['timeline'] if e['id']!='editorial.timeline.moses-farewell'];del bad['timelineDiscovery']['presentations']['editorial.timeline.moses-farewell']
    source=tmp_path/'partial.json';write(source,bad)
    with pytest.raises(ValueError,match='snapshot|exactly'):
        approve_publish(source,tmp_path,'partial',database)
    with psycopg.connect(database) as c:
        assert c.execute('SELECT count(*) FROM event_sequence_items').fetchone()[0]==16

    with psycopg.connect(database) as c:
        c.execute("UPDATE timeline_events SET title='Changed by another editor' WHERE id='editorial.timeline.moses-born'")
    changed=json.loads(SEQUENCE.read_text());changed['eventSequences'][0]['presentations']['en']['context']+=' Reviewed.'
    source=tmp_path/'stale.json';write(source,changed)
    with pytest.raises(ValueError,match='snapshot'):
        approve_publish(source,tmp_path,'stale',database)
    with psycopg.connect(database) as c:
        assert c.execute("SELECT title FROM timeline_events WHERE id='editorial.timeline.moses-born'").fetchone()[0]=='Changed by another editor'
