"""Build the sourced theme catalog; publication still uses the reviewed bundle path."""
import csv
import json
import re
from pathlib import Path

from .models import Bundle

EXISTING = {"love", "anxiety", "money", "faith", "grace", "justice", "prayer", "forgiveness", "wisdom", "suffering"}
EXTRA_CATEGORIES = {
    "faith": ["foundations"], "prayer": ["emotions"], "forgiveness": ["foundations"],
    "hope": ["eternity"], "peace": ["relationships"], "love": ["with-god"],
    "wisdom": ["daily-life"], "generosity": ["community"], "justice": ["community"],
    "rest": ["emotions"], "perseverance": ["character"], "holy-spirit": ["with-god"],
    "mutual-care": ["relationships"], "gratitude": ["character"],
}
ALIASES = {
    "anxiety": ["preocupação", "preocupações", "inquietação"], "fear": ["insegurança", "temor"],
    "grief": ["perda", "saudade"], "anger": ["raiva", "irritação"],
    "weariness": ["exaustão", "fadiga", "desânimo"], "loneliness": ["abandono", "isolamento"],
    "money": ["riqueza", "finanças", "bens"], "generosity": ["doação", "partilha"],
    "repentance": ["conversão", "mudança de vida"], "speech": ["língua", "fofoca", "falar", "ouvir"],
    "self-control": ["autocontrole", "temperança"], "stewardship": ["mordomia", "recursos"],
    "lords-supper": ["ceia", "eucaristia"], "return-of-christ": ["segunda vinda", "retorno de Jesus"],
    "eternal-life": ["eternidade"], "new-creation": ["novos céus", "nova terra"],
    "seeking-god": ["busca", "aproximar-se de Deus"], "word-of-god": ["Bíblia", "Escrituras"],
    "temptation": ["resistir ao mal"], "contentment": ["satisfação"],
    "mission": ["evangelização", "testemunho"], "spiritual-gifts": ["dons", "carismas"],
    "guidance": ["orientação", "discernimento"], "poverty": ["pobres", "necessitados"],
}

ALIASES_EN = {
    "anxiety": ["worry", "worries", "concern"], "fear": ["insecurity", "afraid"],
    "grief": ["loss", "mourning"], "anger": ["rage", "irritation"],
    "weariness": ["exhaustion", "fatigue", "discouragement"], "loneliness": ["abandonment", "isolation"],
    "money": ["wealth", "finances", "possessions"], "generosity": ["giving", "sharing"],
    "repentance": ["conversion", "turning back"], "speech": ["tongue", "gossip", "speaking", "listening"],
    "self-control": ["temperance"], "stewardship": ["resources"], "lords-supper": ["communion", "eucharist"],
    "return-of-christ": ["second coming", "Jesus returns"], "eternal-life": ["eternity"],
    "new-creation": ["new heavens", "new earth"], "word-of-god": ["Bible", "Scripture"],
    "mission": ["evangelism", "witness"], "spiritual-gifts": ["gifts", "charisms"],
    "guidance": ["direction", "discernment"], "poverty": ["poor", "needy"],
}


def entity_id(slug):
    return f"fixture.theme.{slug}" if slug in EXISTING else f"editorial.theme.{slug}"

def build(directory: Path, chapters: Path) -> Bundle:
    rows = list(csv.DictReader((directory / "catalog-pt.tsv").open(), delimiter="\t"))
    english = {r["slug"]: r for r in csv.DictReader((directory / "catalog-en.tsv").open(), delimiter="\t")}
    if len(rows) != len({r["slug"] for r in rows}) or set(english) != {r["slug"] for r in rows}:
        raise ValueError("duplicate or untranslated themes")
    editorial = {"id": "editorial.themes.2026-10", "citation": "Verbum — seleção temática e resumos editoriais / thematic selection and editorial summaries (2026-10).", "url": None}
    sources = {editorial["id"]: editorial}
    provenance = {editorial["id"]: {"sourceId": "verbum.editorial", "license": "Original Verbum editorial content; biblical references are citations, not reproduced translation text."}}
    entities, details, presentations, categories, evidence = [], [], [], {}, {}
    refs_by_id = {}
    for row in rows:
        slug = row["slug"]; id_ = entity_id(slug); en = english[slug]
        refs, source_ids = [], [editorial["id"]]
        for value in row["passages"].split(";"):
            match = re.fullmatch(r"(\w+) (\d+):(\d+)-(\d+)", value)
            if not match: raise ValueError(f"invalid reference: {value}")
            book, chapter, start, end = match.groups(); chapter, start, end = map(int, (chapter, start, end))
            canonical = json.loads((chapters / f"pt-BR-{book}-{chapter}.json").read_text())
            available = {v["number"] for v in canonical["verses"]}
            if canonical["bookId"] != book or canonical["chapter"] != chapter or end < start or not set(range(start, end + 1)) <= available:
                raise ValueError(f"nonexistent passage: {value}")
            refs.append(dict(bookId=book, chapter=chapter, verseStart=start, verseEnd=end))
            sid = f"scripture.theme.{book}.{chapter}.{start}-{end}"
            sources[sid] = dict(id=sid, citation=value, url=f"https://www.biblegateway.com/passage/?search={book}+{chapter}%3A{start}-{end}")
            provenance[sid] = dict(sourceId="scripture.references", license="Bibliographic reference only; no Bible translation text reproduced.", section=value)
            source_ids.append(sid)
        entity = dict(id=id_, type="theme", name=en["name"], summary=en["summary"])
        entities.append(entity); evidence[id_] = source_ids; refs_by_id[id_] = refs
        details.append(dict(entity=entity, aliases=[], keyPassages=refs, sources=[sources[s] for s in source_ids]))
        categories[id_] = [row["category"], *EXTRA_CATEGORIES.get(slug, [])]
        for lang, text, aliases in [("pt-BR", row, ALIASES.get(slug, [])), ("en", en, ALIASES_EN.get(slug, []))]:
            presentations.append(dict(entityId=id_, language=lang, name=text["name"], summary=text["summary"], aliases=aliases))
    # Link overlapping reading selections, with the cited shared passage as evidence.
    # Editorial navigation links are not claims that the Bible defines this taxonomy.
    relationships, seen = [], set()
    for a, refs in refs_by_id.items():
        candidates = []
        for b, other in refs_by_id.items():
            if a == b: continue
            shared = next((r for r in refs for q in other if r["bookId"] == q["bookId"] and r["chapter"] == q["chapter"] and max(r["verseStart"], q["verseStart"]) <= min(r["verseEnd"], q["verseEnd"])), None)
            if shared: candidates.append((b, shared))
        for b, ref in candidates[:3]:
            pair = tuple(sorted((a,b)))
            if pair in seen: continue
            seen.add(pair)
            sid = f"scripture.theme.{ref['bookId']}.{ref['chapter']}.{ref['verseStart']}-{ref['verseEnd']}"
            relationships.append(dict(id=f"editorial.theme-link.{pair[0]}.{pair[1]}", sourceId=pair[0], targetId=pair[1], type="relatedToTheme", sourceReferenceIds=[editorial["id"], sid]))
    return Bundle.model_validate(dict(version=4, kind="editorial", content=dict(sources=list(sources.values()), entities=entities, details=details, relationships=relationships, timeline=[], dailyVersePool=[]), provenance=provenance, entitySources=evidence, themes=dict(categories=categories, presentations=presentations)))

if __name__ == "__main__":
    import argparse
    parser=argparse.ArgumentParser();parser.add_argument("--chapters",type=Path,required=True);parser.add_argument("--output",type=Path,required=True)
    args=parser.parse_args();bundle=build(Path(__file__).resolve().parents[1]/"sources"/"themes",args.chapters)
    args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(json.dumps(bundle.model_dump(),ensure_ascii=False,indent=2)+"\n")
    print(f"{len(bundle.content.entities)} themes, {sum(len(d.keyPassages) for d in bundle.content.details)} reading selections, {len(bundle.content.relationships)} related-theme links")
