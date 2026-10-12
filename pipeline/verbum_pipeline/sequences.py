"""Build the first reading sequence from canonical Events and Timeline content.

Order follows chapter placement in Exodus, Numbers and Deuteronomy. This is a
selected reading path, not exhaustive coverage or a reconstructed chronology.
"""
import json
from pathlib import Path
from .models import Bundle

EXODUS = ['moses-born','burning-bush','plagues','passover','exodus','marah','manna','amalek','jethro','sinai','golden-calf','tabernacle','spies','bronze-serpent','balaam','moses-farewell']
CONTEXT = {
 'en': 'A selected reading path through Exodus, Numbers and Deuteronomy, following the placement of the episodes in these books rather than asserting exact historical dates. Jethro follows Exodus 18; compare Deuteronomy 1:9–18. John 3 is a later connection for the bronze serpent, not a step in this sequence.',
 'pt-BR': 'Um percurso de leitura selecionado por Êxodo, Números e Deuteronômio, seguindo a posição dos episódios nesses livros, sem afirmar datas históricas exatas. Jetro segue Êxodo 18; compare Deuteronômio 1:9–18. João 3 é uma conexão posterior para a serpente de bronze, não uma etapa desta sequência.'
}

def build(root: Path) -> Bundle:
    timeline = json.loads((root/'sources/timeline/catalog.json').read_text())
    events = json.loads((root/'sources/events/catalog.json').read_text())
    catalog = events['eventCatalog']
    details = {d['entity']['id']:d for d in events['content']['details']}
    translations = {(p['entityId'],p['language']):p for p in catalog['presentations']}
    inverse = {e:t for t,e in catalog['timelineBindings'].items()}
    old = {t['id']:t for t in timeline['content']['timeline']}
    source_ids = {s['id'] for s in timeline['content']['sources']}
    presentations = timeline['timelineDiscovery']['presentations']
    exemplar = next(p for p in presentations.values() if p['en']['eraId']=='exodus')
    sequence, ordered = [], []
    for slug in EXODUS:
        entity_id = 'fixture.event.exodus' if slug=='exodus' else 'editorial.event.'+slug
        timeline_id = inverse.get(entity_id, 'editorial.timeline.'+slug)
        sequence.append(dict(entityId=entity_id,timelineId=timeline_id))
        if timeline_id in old:
            ordered.append(old[timeline_id]);continue
        detail=details[entity_id]
        for source in detail['sources']:
            if source['id'] not in source_ids:
                timeline['content']['sources'].append(source);source_ids.add(source['id'])
                timeline['provenance'][source['id']]=events['provenance'][source['id']]
        texts={}
        for language in ('en','pt-BR'):
            p=translations[(entity_id,language)]
            summary,context=p['summary'].split('\n\n',1)
            texts[language]={**exemplar[language], 'title':p['name'], 'summary':summary,'context':context,'kind':'event','keyPassages':detail['keyPassages']}
        presentations[timeline_id]=texts
        ordered.append(dict(id=timeline_id,title=texts['en']['title'],summary=texts['en']['summary'],startYear=None,endYear=None,datePrecision='unknown',entityIds=[entity_id],sourceReferenceIds=[s['id'] for s in detail['sources']]))
    output=[];inserted=False
    for t in timeline['content']['timeline']:
        if presentations[t['id']]['en']['eraId']=='exodus':
            if not inserted:output.extend(ordered);inserted=True
        else:output.append(t)
    timeline['content']['timeline']=output
    timeline['content']['referencedEntityIds']=sorted(set(timeline['content']['referencedEntityIds']) | {i['entityId'] for i in sequence})
    timeline['version']=7
    timeline['eventSequences']=[dict(eraId='exodus',presentations={lang:dict(title=exemplar[lang]['eraTitle'],context=CONTEXT[lang]) for lang in ('en','pt-BR')},items=sequence)]
    return Bundle.model_validate(timeline)

if __name__=='__main__':
    root=Path(__file__).resolve().parents[1]
    output=root/'sources/sequences/exodus.json';output.parent.mkdir(exist_ok=True)
    bundle=build(root);output.write_text(json.dumps(bundle.model_dump(),ensure_ascii=False,indent=2)+'\n')
    print('16 canonical events; 7 additional timeline entries; 98 total timeline entries')
