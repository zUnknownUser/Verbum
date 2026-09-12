#!/usr/bin/env python3
"""Regenerates api/examples/ from the apps' fixture tables (the Swift files are the source; the
Kotlin ones are ports of them). Run from the repo root: python3 api/scripts/gen-examples.py"""
import datetime, json, os, re

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
FIX = open(f"{ROOT}/VerbumKit/Sources/Clients/Fixtures/EntityFixtureData.swift").read()
TL = open(f"{ROOT}/VerbumKit/Sources/Clients/TimelineClient.swift").read()
DV = open(f"{ROOT}/VerbumKit/Sources/Models/DailyVerse.swift").read()
OUT = f"{ROOT}/api/examples"
PREFIX = {"P": "fixture.person.", "L": "fixture.place.", "T": "fixture.theme.", "E": "fixture.event."}
BOOK_NAMES = {"1Sam": "1 Samuel", "2Sam": "2 Samuel", "Ps": "Psalms", "Matt": "Matthew", "John": "John", "Rom": "Romans", "Gen": "Genesis", "Exod": "Exodus"}
SOURCES = [
    {"id": "fixture.source.web", "citation": "World English Bible (public domain) — the passages cited", "url": "https://worldenglish.bible"},
    {"id": "fixture.source.editorial", "citation": "Verbum editorial notes (fixture; to be replaced by sourced content)", "url": None},
]

def unescape(s): return s.replace('\\"', '"')

def dump(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2); f.write("\n")

# ---- entities and edges
entities, order = {}, []
for kind, key, name, summary in re.findall(r'\b(person|place|theme|event)\("([^"]+)", "((?:[^"\\]|\\.)*)", "((?:[^"\\]|\\.)*)"\)', FIX):
    e = {"id": f"fixture.{kind}.{key}", "type": kind, "name": unescape(name), "summary": unescape(summary)}
    entities[e["id"]] = e; order.append(e["id"])

def resolve(token):
    token = token.strip()
    m = re.match(r'([PLTE]) \+ "([^"]+)"', token)
    if m: return PREFIX[m[1]] + m[2]
    return re.match(r'"([^"]+)"', token)[1]

edges = []
for a, t, b, editorial in re.findall(r'edge\(([^,]+), \.(\w+), ([^,)]+)(, scripture: false)?\)', FIX):
    s, o = resolve(a), resolve(b)
    for x in (s, o):
        if x.startswith("passage.") and x not in entities:
            _, book, ch = x.split(".")
            entities[x] = {"id": x, "type": "passage", "name": f"{BOOK_NAMES.get(book, book)} {ch}", "summary": None}
            order.append(x)
    edges.append({"id": f"fixture.edge.{s}.{t}.{o}", "sourceId": s, "targetId": o, "type": t,
                  "confidence": 0.8 if editorial else 1.0,
                  "sourceReferenceIds": ["fixture.source.editorial" if editorial else "fixture.source.web"]})

# ---- details
details = {}
for m in re.finditer(r'detail\(([PLTE]) \+ "([^"]+)"(.*?)\),\n', FIX, re.S):
    eid, body = PREFIX[m[1]] + m[2], m[3]
    def opt(name):
        mm = re.search(name + r': "((?:[^"\\]|\\.)*)"', body); return unescape(mm[1]) if mm else None
    aliases = re.search(r"aliases: \[([^\]]*)\]", body)
    details[eid] = {
        "entity": entities[eid],
        "aliases": re.findall(r'"([^"]+)"', aliases[1]) if aliases else [],
        "approximateDates": opt("dates"), "role": opt("role"), "modernGeography": opt("modern"),
        "keyPassages": [{"bookId": b, "chapter": int(c)} for b, c in re.findall(r'bookId: "(\w+)", chapter: (\d+)', body)],
        "sources": SOURCES,
    }

david = "fixture.person.david"
dump(f"{OUT}/entities/david.json", details[david])
dump(f"{OUT}/entities/people.json", {"entities": sorted([e for e in entities.values() if e["type"] == "person"], key=lambda e: e["name"])})

# ---- graph: undirected one hop, limit 24, by confidence desc then name
neighbours = []
for e in sorted([e for e in edges if david in (e["sourceId"], e["targetId"])],
                key=lambda e: (-e["confidence"], entities[e["targetId"] if e["sourceId"] == david else e["sourceId"]]["name"])):
    other = e["targetId"] if e["sourceId"] == david else e["sourceId"]
    if other not in neighbours: neighbours.append(other)
neighbours = neighbours[:24]
visible = set(neighbours) | {david}
dump(f"{OUT}/graph/david.json", {"root": entities[david], "nodes": [entities[i] for i in neighbours],
                                  "edges": [e for e in edges if e["sourceId"] in visible and e["targetId"] in visible]})

# ---- context for 1 Samuel 17, the ContextClient.fixtures algorithm
chapter = "passage.1Sam.17"
touching = [e for e in edges if chapter in (e["sourceId"], e["targetId"])]
citing = [d for d in details.values() if any(p["bookId"] == "1Sam" and p["chapter"] == 17 for p in d["keyPassages"])]
ids = {e["sourceId"] if e["targetId"] == chapter else e["targetId"] for e in touching} | {d["entity"]["id"] for d in citing}
source_ids = {s for e in touching for s in e["sourceReferenceIds"]} | ({"fixture.source.web", "fixture.source.editorial"} if citing else set())
related = []
for e in edges:
    other = e["targetId"] if e["sourceId"] in ids else (e["sourceId"] if e["targetId"] in ids else None)
    if other and other.startswith("passage.") and other != chapter:
        _, book, ch = other.split(".")
        ref = {"bookId": book, "chapter": int(ch)}
        if ref not in related: related.append(ref)
        source_ids |= set(e["sourceReferenceIds"])
dump(f"{OUT}/context/1Sam.17.json", {"reference": {"bookId": "1Sam", "chapter": 17},
                                      "entities": [entities[i] for i in order if i in ids and entities[i]["type"] != "passage"],
                                      "relatedPassages": related, "sources": [s for s in SOURCES if s["id"] in source_ids]})

# ---- timeline
events = []
for i, t, a, b, p, s, ids_ in re.findall(r'event\("([^"]+)", "((?:[^"\\]|\\.)*)", (nil|-?\d+), (nil|-?\d+), \.(\w+), "((?:[^"\\]|\\.)*)", \[([^\]]*)\]\)', TL):
    events.append({"id": f"fixture.timeline.{i}", "title": unescape(t),
                   "startYear": None if a == "nil" else int(a), "endYear": None if b == "nil" else int(b),
                   "datePrecision": p, "summary": unescape(s), "entityIds": re.findall(r'"([^"]+)"', ids_),
                   "sourceReferenceIds": ["fixture.source.editorial"]})
events.sort(key=lambda e: (e["startYear"] is None, e["startYear"] or 0, -(e["endYear"] if e["endYear"] is not None else (e["startYear"] or 0))))
dump(f"{OUT}/timeline/all.json", {"events": events, "entityNames": {i: entities[i]["name"] for e in events for i in e["entityIds"]}})

# ---- search: the entity group (references and books are matched on device too)
dump(f"{OUT}/search/david.json", {"query": "David", "passages": [], "books": [],
                                   "entities": [e for e in entities.values() if e["type"] != "passage" and "david" in e["name"].lower()]})

# ---- daily verse: DailyVerses' algorithm (splitmix64 + Fisher–Yates per 101-day cycle)
pool = re.findall(r'bookId: "(\w+)", chapter: (\d+), verses: (\d+)\.\.\.', DV)
MASK = (1 << 64) - 1
def permutation(seed):
    idx, s, i = list(range(len(pool))), seed, len(pool) - 1
    while i > 0:
        s = (s + 0x9E3779B97F4A7C15) & MASK; z = s
        z = ((z ^ (z >> 30)) * 0xBF58476D1CE4E5B9) & MASK
        z = ((z ^ (z >> 27)) * 0x94D049BB133111EB) & MASK
        z ^= z >> 31
        j = z % (i + 1); idx[i], idx[j] = idx[j], idx[i]; i -= 1
    return idx
week = []
for d in range(7):
    day = datetime.date(2026, 9, 13) + datetime.timedelta(d)
    epoch = (day - datetime.date(1970, 1, 1)).days
    book, ch, v = pool[permutation((epoch // len(pool)) & MASK)[epoch % len(pool)]]
    week.append({"date": day.isoformat(), "reference": {"bookId": book, "chapter": int(ch), "verseStart": int(v), "verseEnd": int(v)}})
dump(f"{OUT}/daily-verse/week.json", {"verses": week})
print("examples regenerated")
