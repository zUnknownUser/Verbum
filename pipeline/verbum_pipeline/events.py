"""Expand Events from the shared timeline corpus and original bilingual additions.

Four legacy event identities are reused. A timeline binding identifies the same
canonical event across the two entry points without creating another timeline row.
"""
import argparse
import json
import re
from pathlib import Path
from .models import Bundle

EXCLUDED = {'fixture.timeline.judges', 'fixture.timeline.early-church', 'fixture.timeline.pauline-missions', 'editorial.timeline.lament', 'editorial.timeline.amos-justice'}
LEGACY = {'fixture.timeline.exodus': 'fixture.event.exodus', 'editorial.timeline.david-goliath': 'fixture.event.david-goliath', 'fixture.timeline.david': 'fixture.event.david-takes-jerusalem', 'editorial.timeline.david-anointed': 'fixture.event.david-anointed'}
PARALLELS = {
 'fixture.timeline.crucifixion': ['Matt 27','Luke 23','John 19'],
 'fixture.timeline.birth-of-jesus': ['Matt 1','Matt 2'],
 'editorial.timeline.last-supper': ['Matt 26','Mark 14'],
 'editorial.timeline.transfiguration': ['Matt 17','Luke 9'],
 'editorial.timeline.resurrection': ['Matt 28','John 20'],
 'editorial.timeline.feeding': ['Matt 14','Luke 9','John 6'],
 'editorial.timeline.baptism': ['Matt 3','Luke 3'],
 'editorial.timeline.disciples': ['Matt 4','Mark 1'],
 'editorial.timeline.joseph-reconciles': ['Gen 46'],
}
ALIASES = {
 'fixture.event.exodus': {'pt-BR':['Êxodo','Saída do Egito','Travessia do mar'], 'en':['Exodus','Departure from Egypt','Crossing the sea']},
 'fixture.event.david-takes-jerusalem': {'pt-BR':['Davi toma Jerusalém'], 'en':['David takes Jerusalem']},
 'fixture.event.david-anointed': {'pt-BR':['Unção de Davi'], 'en':['Anointing of David']},
 'editorial.event.cana': {'pt-BR':['Bodas de Caná','Água em vinho'], 'en':['Wedding at Cana','Water into wine']},
 'editorial.event.daniel-lions': {'pt-BR':['Cova dos leões'], 'en':['Lions’ den']},
 'editorial.event.storm-calmed': {'pt-BR':['Tempestade acalmada'], 'en':['Calming the storm']},
 'editorial.event.jairus-daughter': {'pt-BR':['Filha de Jairo','Mulher com fluxo de sangue'], 'en':['Jairus’s daughter','Woman with a flow of blood']},
}


def build(directory: Path, inventory: Path) -> Bundle:
    timeline = Bundle.model_validate_json((directory.parent/'timeline/catalog.json').read_text())
    legacy = json.loads((directory.parent/'timeline/legacy.json').read_text())
    names = legacy['entityNames']
    records, bindings, eras = [], {}, {}
    for event in timeline.content.timeline:
        if event.id in EXCLUDED:
            continue
        entity_id = LEGACY.get(event.id, 'editorial.event.'+event.id.rsplit('.',1)[1])
        translations = timeline.timelineDiscovery.presentations[event.id]
        refs = [p.model_dump(exclude_none=True) for p in translations['en'].keyPassages]
        refs += [dict(bookId=p.split()[0],chapter=int(p.split()[1])) for p in PARALLELS.get(event.id,[])]
        records.append((entity_id, translations, refs, [id for id in event.entityIds if not id.startswith(('fixture.event.', 'editorial.event.'))]))
        bindings[event.id] = entity_id
        eras[entity_id] = translations["en"].eraId
    for line in (directory/'additions.psv').read_text().splitlines():
        if not line or line.startswith('#'): continue
        era, slug, passages, pt_title, en_title, pt_summary, en_summary, pt_context, en_context = line.split('|')
        if era not in {p['en'].eraId for p in timeline.timelineDiscovery.presentations.values()}:
            raise ValueError('unknown era')
        eras['editorial.event.'+slug] = era
        refs=[dict(bookId=p.split()[0],chapter=int(p.split()[1])) for p in passages.split(';')]
        if slug == 'bronze-serpent': refs.append(dict(bookId='John',chapter=3))
        links=[]
        for id_,name in names.items():
            # Mary/John/Saul can denote different people: never infer their identity from a name.
            if id_.startswith(('fixture.person.','fixture.place.')) and name not in ('Mary','John','Saul') and re.search(r'\b'+re.escape(name)+r'\b',en_title+' '+en_summary):
                links.append(id_)
        if slug == 'cana': links.append('fixture.person.mary')
        records.append(('editorial.event.'+slug,{
            'en':dict(title=en_title,summary=en_summary,context=en_context),
            'pt-BR':dict(title=pt_title,summary=pt_summary,context=pt_context)},refs,links))
    editorial='editorial.events.2026-10'
    sources={editorial:dict(id=editorial,citation='Verbum — estudos de acontecimentos bíblicos / biblical event studies. Original bilingual editorial notes with chapter references.',url=None)}
    entities,details,relationships,presentations,evidence=[],[],[],[],{}
    referenced=set()
    for id_,texts,refs,links in records:
        texts={lang:(t.model_dump() if hasattr(t,'model_dump') else t) for lang,t in texts.items()}
        source_ids=[editorial]
        for ref in refs:
            book,ch=ref['bookId'],ref['chapter']
            canonical=json.loads((inventory/f'pt-BR-{book}-{ch}.json').read_text())
            if canonical['bookId']!=book or canonical['chapter']!=ch or not canonical['verses']:
                raise ValueError('invalid canonical chapter')
            sid=f'scripture.events.{book}.{ch}'
            sources[sid]=dict(id=sid,citation=f'{book} {ch}',url=f'https://www.biblegateway.com/passage/?search={book}+{ch}')
            source_ids.append(sid)
        if len(refs)!=len({(p['bookId'],p['chapter']) for p in refs}): raise ValueError('duplicate reading')
        en=texts['en']
        summary=en['summary']+'\n\n'+en['context']
        entity=dict(id=id_,type='event',name=en['title'],summary=summary)
        entities.append(entity);evidence[id_]=source_ids
        details.append(dict(entity=entity,aliases=ALIASES.get(id_,{}).get('en',[]),keyPassages=refs,sources=[sources[s] for s in source_ids]))
        for lang,text in texts.items():
            presentations.append(dict(entityId=id_,language=lang,name=text['title'],summary=text['summary']+'\n\n'+text['context'],aliases=ALIASES.get(id_,{}).get(lang,[])))
        for linked in dict.fromkeys(links):
            referenced.add(linked)
            relationships.append(dict(id=f'editorial.event-link.{id_}.{linked}',sourceId=id_,targetId=linked,type='relatedTo',sourceReferenceIds=source_ids))
    return Bundle.model_validate(dict(version=6,kind='editorial',content=dict(sources=list(sources.values()),entities=entities,details=details,relationships=relationships,timeline=[],dailyVersePool=[],referencedEntityIds=sorted(referenced)),
        provenance={sid:dict(sourceId='verbum.editorial' if sid==editorial else 'scripture.references',license='Original editorial prose and bibliographic references only; no Bible translation text reproduced.',section=s['citation']) for sid,s in sources.items()},
        entitySources=evidence,eventCatalog=dict(eras=eras,presentations=presentations,timelineBindings=bindings)))

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--inventory',type=Path,required=True);p.add_argument('--output',type=Path,default=Path('sources/events/catalog.json'))
    args=p.parse_args();bundle=build(Path(__file__).resolve().parents[1]/'sources/events',args.inventory)
    args.output.write_text(json.dumps(bundle.model_dump(),ensure_ascii=False,indent=2)+'\n')
    print(f'{len(bundle.content.entities)} events; {len(bundle.eventCatalog.presentations)} presentations; {len(bundle.eventCatalog.timelineBindings)} stable timeline bindings; {sum(len(d.keyPassages) for d in bundle.content.details)} readings')
