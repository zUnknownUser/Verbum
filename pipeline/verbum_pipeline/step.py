"""Pinned STEP TSV adapter into the existing review/publish Bundle; never writes a DB.

Excludes AI prose, restricted BDB Meaning, genealogy and ambiguous forms. Keeps Unicode/IDs.
"""

import hashlib
import re
from collections import Counter
from pathlib import Path
from urllib.parse import quote

from .files import read
from .models import Bundle
from .scripture import read_tsv

OFFICIAL = "https://github.com/STEPBible/STEPBible-Data"
STRONG = r"[HG]\d{4,5}[A-Za-z]*"
BOOKS = dict(
    zip(
        "Gen Exo Lev Num Deu Jos Jdg Rut 1Sa 2Sa 1Ki 2Ki 1Ch 2Ch Ezr Neh Est Job Psa Pro Ecc Sng "
        "Isa Jer Lam Ezk Dan Hos Jol Amo Oba Jon Mic Nam Hab Zep Hag Zec Mal Mat Mrk Luk Jhn Act "
        "Rom 1Co 2Co Gal Eph Php Col 1Th 2Th 1Ti 2Ti Tit Phm Heb Jas "
        "1Pe 2Pe 1Jn 2Jn 3Jn Jud Rev".split(),
        "Gen Exod Lev Num Deut Josh Judg Ruth 1Sam 2Sam 1Kgs 2Kgs 1Chr 2Chr Ezra Neh Esth Job Ps "
        "Prov Eccl Song Isa Jer Lam Ezek Dan Hos Joel Amos Obad Jonah Mic Nah "
        "Hab Zeph Hag Zech Mal "
        "Matt Mark Luke John Acts Rom 1Cor 2Cor Gal Eph Phil Col 1Thess 2Thess "
        "1Tim 2Tim Titus Phlm "
        "Heb Jas 1Pet 2Pet 1John 2John 3John Jude Rev".split(),
        strict=True,
    )
)
NAMED = {"– Named", "– Greek", "– Aramaic", "– Spelled"}


def refs(value: str, corpus: set[tuple[str, int, int]]) -> tuple[list[dict], list[dict]]:
    accepted, excluded = [], []
    previous = None
    for token in re.split(r"[;,]", value):
        token = token.strip()
        if not token:
            continue
        if token.isdigit() and previous:
            token = f"{previous[0]}.{previous[1]}.{token}"
        match = re.fullmatch(r"([1-3]?[A-Za-z]{2,3})\.(\d+)\.(\d+)[a-z]?", token)
        if not match:
            if token.startswith("LXX."):
                excluded.append({"locator": token, "reason": "unsupported_versification"})
                continue
            raise ValueError(f"unrecognized STEP reference: {token}")
        book, chapter, verse = match.groups()
        previous = (book, chapter)
        if book not in BOOKS:
            raise ValueError(f"unknown STEP book: {book}")
        key = (BOOKS[book], int(chapter), int(verse))
        if key not in corpus:
            excluded.append({"locator": token, "reason": "absent_from_WEB_versification"})
            continue
        accepted.append(
            {
                "reference": {
                    "bookId": key[0],
                    "chapter": key[1],
                    "verseStart": key[2],
                    "verseEnd": key[2],
                },
                "locator": token,
            }
        )
    return accepted, excluded


def lexicon(text: str, code: str) -> tuple[list[dict], list[dict]]:
    records, excluded = [], []
    started = False
    for line, raw in enumerate(text.splitlines(), 1):
        columns = raw.split("\t")
        if (
            len(columns) >= 7
            and columns[:3]
            in (["eStrong#", "dStrong", "uStrong"], ["eStrong", "dStrong", "uStrong"])
            and columns[4:7] == ["Transliteration", "Morph", "Gloss"]
        ):
            started = True
            continue
        if not re.match(r"^[HG]\d{4}", raw):
            continue
        if not started or len(columns) != 8:
            raise ValueError(f"unexpected {code} schema at line {line}")
        extended, disambiguated, unified, original, transliteration, morph, gloss = columns[:7]
        external = disambiguated.split()[0]
        if not re.fullmatch(STRONG, external) or not re.fullmatch(STRONG, extended):
            raise ValueError(f"invalid {code} identifier at line {line}")
        if not original.strip():
            excluded.append({"line": line, "externalId": external, "reason": "missing_original"})
            continue
        language = "grc" if code == "TBESG" else ("arc" if morph.startswith("A:") else "he")
        records.append(
            {
                "id": f"step.{code}.{external}",
                "entityId": f"step.lexeme.{external}",
                "sourceId": f"step.{code}",
                "externalId": external,
                "sourceLine": line,
                "identifiers": {
                    "eStrong": [extended],
                    "dStrong": [external],
                    "uStrong": sorted(set(re.findall(STRONG, unified))),
                },
                "lexical": {
                    "language": language,
                    "original": original,
                    "transliteration": transliteration,
                    "morphology": morph,
                    "gloss": gloss,
                    "glossLanguage": "en",
                    "extendedStrong": extended,
                    "disambiguatedStrong": disambiguated,
                    "unifiedStrong": unified,
                },
                "localizations": [
                    {
                        "language": "en",
                        "name": original,
                        "aliases": sorted(
                            {v for v in (transliteration, gloss) if v.strip() and v != original}
                        ),
                        "description": gloss or None,
                    }
                ],
                "occurrences": [],
            }
        )
    if not started or not records:
        raise ValueError(f"missing {code} data/header")
    return records, excluded


def proper_names(text: str, mapping: dict, corpus: set, lexemes: dict) -> tuple[list, list, dict]:
    records, entities, edges, exclusions = [], [], {}, []
    counts = Counter()
    used_mapping = set()
    current, category = None, ""
    for line, raw in enumerate(text.splitlines(), 1):
        row = raw.split("\t")
        if raw.startswith("$"):
            category = row[0].replace("$", "").replace("=", "").strip()
            current = None
            continue
        match = re.fullmatch(rf"(.+@.+)=({STRONG})", row[0])
        if match:
            current = None
            if len(row) < 9:
                raise ValueError(f"short TIPNR record at line {line}")
            unique_name, uid = match.groups()
            kind = {"Male": "person", "Female": "person", "Place": "place"}.get(row[8].strip())
            if (
                not kind
                or category not in {"PERSON(s)", "PLACE"}
                or "(?)" in unique_name
                or unique_name.startswith("Unnamed")
            ):
                counts["excluded_record_type_or_uncertainty"] += 1
                continue
            name = unique_name.split("@", 1)[0].replace("_", " ")
            entity_id = f"step.{kind}.{uid}"
            if uid in mapping:
                bound = mapping[uid]
                if bound["uniqueName"] != unique_name or bound["type"] != kind:
                    raise ValueError(f"STEP identity changed for mapping {uid}")
                entity_id, name = bound["entityId"], bound["name"]
                used_mapping.add(uid)
            entities.append({"id": entity_id, "type": kind, "name": name})
            current = {
                "id": f"step.TIPNR.{uid}",
                "entityId": entity_id,
                "sourceId": "step.TIPNR",
                "externalId": uid,
                "sourceLine": line,
                "identifiers": {
                    "uStrong": [uid],
                    "uniqueName": [unique_name],
                    "dStrong": [],
                    "eStrong": [],
                    "originalForm": [],
                },
                "localizations": [{"language": "en", "name": name, "aliases": []}],
                "occurrences": [],
            }
            records.append(current)
            continue
        if current is None or not row[0].startswith("– ") or row[0] == "– Total":
            continue
        if len(row) < 5:
            raise ValueError(f"short TIPNR form at line {line}")
        if row[0].strip() not in NAMED or "(?)" in row[1]:
            counts["excluded_form_type_or_uncertainty"] += 1
            continue
        form = re.fullmatch(rf"({STRONG})«({STRONG})=(.+)", row[2])
        if not form:
            raise ValueError(f"unexpected TIPNR identifier/form at line {line}")
        ds, es, original = form.groups()
        for scheme, value in (("dStrong", ds), ("eStrong", es), ("originalForm", original)):
            current["identifiers"][scheme].append(value)
        alias = row[1].split("@", 1)[0].split("|", 1)[0].replace("_", " ")
        current["localizations"][0]["aliases"].append(alias)
        occurrences, excluded = refs(row[4], corpus)
        current["occurrences"].extend(occurrences)
        exclusions.extend({"line": line, "recordId": current["id"], **x} for x in excluded)
        if ds in lexemes:
            target = lexemes[ds]["entityId"]
            edge_id = f"step.name-lexeme.{current['externalId']}.{ds}"
            edges[edge_id] = {
                "id": edge_id,
                "sourceId": current["entityId"],
                "targetId": target,
                "type": "relatedTo",
                "sourceReferenceIds": ["step.TIPNR", lexemes[ds]["sourceId"]],
            }
        else:
            exclusions.append(
                {
                    "line": line,
                    "recordId": current["id"],
                    "identifier": ds,
                    "reason": "lexeme_not_in_selected_datasets",
                }
            )
    if used_mapping != mapping.keys():
        raise ValueError(f"unresolved entity mappings: {sorted(mapping.keys() - used_mapping)}")
    for r in records:
        r["identifiers"] = {k: sorted(set(v)) for k, v in r["identifiers"].items()}
        loc = r["localizations"][0]
        loc["aliases"] = sorted(set(loc["aliases"]) - {loc["name"]})
        r["occurrences"] = list({o["locator"]: o for o in r["occurrences"]}.values())
    return (
        records,
        entities,
        {"relationships": list(edges.values()), "excluded": exclusions, "counts": dict(counts)},
    )


def build(source_dir: Path, manifest_path: Path, mapping_path: Path, corpus_path: Path):
    manifest, mapping = read(manifest_path), read(mapping_path)
    if manifest.get("version") != 1 or manifest.get("repository") != OFFICIAL:
        raise ValueError("STEP manifest must pin the official upstream repository")
    revision = manifest["revision"]
    if not re.fullmatch(r"[0-9a-f]{40}", revision):
        raise ValueError("STEP revision must be a full commit SHA")
    if set(manifest["datasets"]) != {"TBESH", "TBESG", "TIPNR"}:
        raise ValueError("unexpected STEP dataset selection")
    texts, sources, datasets, provenance = {}, [], [], {}
    for code, entry in sorted(manifest["datasets"].items()):
        path = (source_dir / entry["path"]).resolve()
        if not path.is_relative_to(source_dir.resolve()):
            raise ValueError("dataset path escapes the source directory")
        raw = path.read_bytes()
        if hashlib.sha256(raw).hexdigest() != entry["sha256"]:
            raise ValueError(f"SHA-256 mismatch for {code}; review the source revision")
        texts[code] = raw.decode("utf-8-sig")
        sid = f"step.{code}"
        attribution = f"STEP Bible / Tyndale House, Cambridge — {code} (CC BY 4.0)"
        modifications = (
            "Selected structured fields; normalized references to Verbum OSIS/WEB; "
            "omitted Meaning definitions, AI prose, genealogy and ambiguous forms. "
            "No translation of original forms or English glosses."
        )
        sources.append(
            {
                "id": sid,
                "citation": attribution,
                "url": f"{OFFICIAL}/blob/{revision}/{quote(entry['path'])}",
            }
        )
        datasets.append(
            {
                "sourceId": sid,
                "repository": OFFICIAL,
                "revision": revision,
                **entry,
                "licenseUrl": "https://creativecommons.org/licenses/by/4.0/",
                "attribution": attribution,
                "modifications": modifications,
            }
        )
        provenance[sid] = {
            "sourceId": sid,
            "license": "CC-BY-4.0 (selected fields only)",
            "section": f"{revision}; {entry['path']}; {modifications}",
        }
    corpus = {(v.book_id, v.chapter, v.verse) for v in read_tsv(corpus_path)}
    records, entities, excluded = [], [], []
    for code in ("TBESH", "TBESG"):
        parsed, skipped = lexicon(texts[code], code)
        records.extend(parsed)
        excluded.extend({"dataset": code, **x} for x in skipped)
        entities.extend(
            {"id": r["entityId"], "type": "originalTerm", "name": r["lexical"]["original"]}
            for r in parsed
        )
    lexemes = {r["externalId"]: r for r in records}
    names, named_entities, report = proper_names(texts["TIPNR"], mapping, corpus, lexemes)
    records.extend(names)
    entities.extend(named_entities)
    bundle = Bundle.model_validate(
        {
            "version": 2,
            "kind": "editorial",
            "content": {
                "sources": sources,
                "entities": entities,
                "relationships": report.pop("relationships"),
                "details": [],
                "timeline": [],
                "dailyVersePool": [],
            },
            "provenance": provenance,
            "entitySources": {r["entityId"]: [r["sourceId"]] for r in records},
            "enrichment": {"datasets": datasets, "records": records},
        }
    )
    report.update(
        {
            "revision": revision,
            "mappedEntities": len(mapping),
            "records": dict(Counter(r["sourceId"] for r in records)),
            "relationships": len(bundle.content.relationships),
            "occurrences": sum(len(r["occurrences"]) for r in records),
            "corpusSHA256": hashlib.sha256(corpus_path.read_bytes()).hexdigest(),
        }
    )
    report["excluded"].extend(excluded)
    return bundle, report
