"""Build a bilingual study timeline from reviewed, original editorial prose.

The order follows the biblical narrative within eras, not a reconstructed calendar.
No absolute years are inferred from passages. Historical dating is a separate future
editorial layer; this edition deliberately makes no unsupported year claims.
"""
import json
import re
from pathlib import Path
from .models import Bundle

ERAS = [
 ('origins', 'As origens', 'Origins', 'Criação, ruptura, preservação e dispersão nos primeiros capítulos de Gênesis.', 'Creation, rupture, preservation and scattering in the opening chapters of Genesis.'),
 ('patriarchs', 'Os patriarcas', 'The patriarchs', 'A promessa acompanha uma família, de Abraão à chegada ao Egito.', 'The promise follows a family, from Abraham to the arrival in Egypt.'),
 ('exodus', 'Êxodo e deserto', 'Exodus and wilderness', 'Libertação, aliança e a formação de um povo durante a jornada.', 'Deliverance, covenant and the formation of a people during the journey.'),
 ('judges', 'A terra e os juízes', 'The land and the judges', 'Entrada na terra, crises de fidelidade e diferentes formas de liderança.', 'Entry into the land, crises of faithfulness and different forms of leadership.'),
 ('monarchy', 'A formação da monarquia', 'The rise of the monarchy', 'Saul, Davi e Salomão: liderança, promessas e os limites do poder.', 'Saul, David and Solomon: leadership, promises and the limits of power.'),
 ('kingdoms', 'Reinos e profetas', 'Kingdoms and prophets', 'Reinos divididos, mensagens proféticas e o caminho até a queda de Samaria.', 'Divided kingdoms, prophetic messages and the path through Samaria’s fall.'),
 ('exile', 'A crise e o exílio', 'Crisis and exile', 'Queda de Jerusalém, vida no exílio, lamento e esperança. Leituras agrupadas por contexto, sem ordem exata entre os livros.', 'Jerusalem’s fall, life in exile, lament and hope. Readings grouped by context, without an exact order between books.'),
 ('return', 'Retorno e reconstrução', 'Return and rebuilding', 'A vida sob o domínio persa, o retorno e a renovação da comunidade.', 'Life under Persian rule, return and the renewal of the community.'),
 ('jesus', 'A vida de Jesus', 'The life of Jesus', 'Uma leitura dos Evangelhos, do anúncio do nascimento à ressurreição. A sequência dos episódios varia entre os relatos.', 'A reading of the Gospels, from the announcement of the birth to the resurrection. Episode order varies between accounts.'),
 ('church', 'A igreja em Atos', 'The church in Acts', 'Do testemunho em Jerusalém às viagens missionárias e à chegada a Roma.', 'From witness in Jerusalem to missionary journeys and the arrival in Rome.'),
]


def build(source: Path, inventory: Path, legacy: Path) -> Bundle:
    previous = json.loads(legacy.read_text())
    old = {e['id']: e for e in previous['events']}
    names = previous['entityNames']
    eras = {e[0]: e for e in ERAS}
    events, presentations, sources = [], {}, {}
    editorial = 'editorial.timeline.2026-10'
    sources[editorial] = dict(id=editorial, citation='Verbum — original bilingual study notes; biblical narrative order, not an absolute chronology.', url=None)
    used_legacy = set()
    for line in source.read_text().splitlines():
        if not line or line.startswith('#'):
            continue
        era, suffix, references, pt_title, en_title, pt_summary, en_summary, pt_context, en_context = line.split('|')
        event_id = ('fixture.timeline.' + suffix[1:]) if suffix.startswith('*') else ('editorial.timeline.' + suffix)
        prior = old.get(event_id)
        if suffix.startswith('*'):
            if prior is None:
                raise ValueError(f'missing original event: {event_id}')
            used_legacy.add(event_id)
        passages, source_ids = [], [editorial]
        for value in references.split(';'):
            book, chapter = value.split()
            ref = dict(bookId=book, chapter=int(chapter))
            chapter_file = inventory / f'pt-BR-{book}-{chapter}.json'
            text = json.loads(chapter_file.read_text())
            if text['bookId'] != book or text['chapter'] != int(chapter) or not text['verses']:
                raise ValueError(f'invalid passage: {value}')
            passages.append(ref)
            sid = f'scripture.timeline.{book}.{chapter}'
            sources[sid] = dict(id=sid, citation=value, url=f'https://www.biblegateway.com/passage/?search={book}+{chapter}')
            source_ids.append(sid)
        links = list(prior['entityIds']) if prior else []
        # Conservative named links only; ambiguous John/Saul identities need explicit curation.
        for entity_id, name in names.items():
            if not entity_id.startswith(('fixture.person.', 'fixture.place.')) or name in ('John', 'Saul'):
                continue
            if re.search(r'\b' + re.escape(name) + r'\b', en_title + ' ' + en_summary) and entity_id not in links:
                links.append(entity_id)
        events.append(dict(id=event_id, title=en_title, summary=en_summary, startYear=None, endYear=None,
                           datePrecision='unknown', entityIds=links, sourceReferenceIds=source_ids))
        e = eras[era]
        presentations[event_id] = {
            lang: dict(eraId=era, eraTitle=e[1 if lang == 'pt-BR' else 2], eraSummary=e[3 if lang == 'pt-BR' else 4],
                       kind='period' if suffix in ('*judges', '*early-church', '*pauline-missions') else 'event',
                       context=context, keyPassages=passages, title=title, summary=summary)
            for lang, title, summary, context in [('pt-BR',pt_title,pt_summary,pt_context),('en',en_title,en_summary,en_context)]
        }
    if used_legacy != set(old):
        raise ValueError(f'original events omitted: {set(old) - used_legacy}')
    return Bundle.model_validate(dict(version=5, kind='editorial', content=dict(
        sources=list(sources.values()), entities=[], relationships=[], details=[], timeline=events, dailyVersePool=[],
        referencedEntityIds=sorted({id for e in events for id in e['entityIds']})),
        provenance={sid:dict(sourceId=sid,license='Original editorial prose and biblical references only; no translation text reproduced.',section=s['citation']) for sid,s in sources.items()},
        entitySources={}, timelineDiscovery=dict(presentations=presentations)))


if __name__ == '__main__':
    import argparse
    p = argparse.ArgumentParser()
    p.add_argument('--inventory', type=Path, required=True)
    p.add_argument('--legacy', type=Path, required=True)
    p.add_argument('--source', type=Path, default=Path('sources/timeline/events.psv'))
    p.add_argument('--output', type=Path, default=Path('sources/timeline/catalog.json'))
    args = p.parse_args()
    bundle = build(args.source, args.inventory, args.legacy)
    args.output.write_text(json.dumps(bundle.model_dump(),ensure_ascii=False,indent=2)+'\n')
    print(f'{len(bundle.content.timeline)} events; {len(ERAS)} eras; bilingual; all chapter references verified')
