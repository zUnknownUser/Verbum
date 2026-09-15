# English and Brazilian Portuguese

## Locale and scope

The app uses PT-BR for a Portuguese device language or Brazil region and English otherwise.
The same policy drives interface resources, biblical book/reference formatting, reader
translation selection, content requests, Ask and voice. No location permission or geolocation
is involved. Android installs a localized application/activity context; iOS resolves feature
and account resources from the matching bundle. OS-owned permission dialogs follow OS settings.

The PT-BR release covers the complete current structured content snapshot: **26,782 entities,
16 timeline events, 5 existing source citations, 14,439 distinct source strings**. This includes
names, aliases, descriptions/glosses, roles, date descriptions and modern geography. Fields
absent in the source remain absent: translation does not invent descriptions or commentary.
Hebrew, Aramaic and Greek forms, scientific transliterations and Strong identifiers remain
canonical. Detail screens show the original term, localized meaning, language, transliteration
and Strong code. Rare proper names without an established Portuguese form may stay unchanged.

Translations are machine-assisted (`gpt-4o-mini`) with structural validation and selected
lexical corrections. **Coverage is complete for this snapshot; individual linguistic accuracy
has not been certified by a human reviewer.** See `pipeline/sources/localization/pt-BR.json`
for the inspectable glossary and `release.json` for hashes, model and source revision.
Publication authorization is recorded separately from this qualification; no claim is made
that Lucas reviewed every generated translation.

## Architecture

- Existing `entity_localizations` owns entity overlays; migration 0006 adds detail fields and
  input fingerprints. `timeline_localizations` and `source_localizations` reference existing
  timeline/source identities. No parallel entity graph or replacement of STEP records.
- Existing import/review/publish pipeline supports a translation-only **bundle v3**. V1/V2
  serialization and review hashes are preserved. V3 cannot write canonical entities,
  relationships, Scripture, timeline events or daily selections.
- Publication reuses the transaction, advisory lock, provenance tables and publication ledger.
  Every overlay is bound to the exact canonical input fingerprint; changed/missing source
  content rejects the transaction. Replaying the same release is idempotent.
- Localization uses the independent `verbum.localization.pt-BR` attribution. STEP-derived
  translations retain CC BY 4.0 attribution; original licenses, source lines, revisions and
  lexical metadata remain in source records. Canonical metadata may remain English in the API
  for provenance; mobile presentation reads localized fields.
- GET `lang` takes precedence over `Accept-Language`; absent/unsupported language defaults to
  English. Responses expose `Content-Language` and `Vary: Accept-Language`. Clients send the
  locale on Ask POSTs too. Updated cache keys discard old cached English presentation results.
- Search indexes the existing localized names/aliases, including translated glosses. Existing
  entity↔passage links feed RAG; no duplicate embedding corpus is created. Ask explicitly
  answers in the app language; missing-evidence and support notes are localized too.

## Scripture and offline behavior

Bible verses are **not machine-translated by this pipeline**. The reader already selects its
Portuguese edition through the existing helloao adapter and caches read chapters. For PT-BR,
unread chapters need a connection; it never substitutes the bundled English WEB silently.
Ask's retrieval corpus remains WEB, with citation validation unchanged. Portuguese renderings
of those excerpts are paraphrases, not quotes attributed to a Portuguese edition. Voice uses
the selected reader text and no longer labels all reader text as WEB.

## Updating content and translations

From `pipeline/`, with credentials supplied through environment (never committed):

```sh
uv run --locked verbum-pipeline localize \
  --snapshot work/localization/next/snapshot.json \
  --cache sources/localization/pt-BR.json \
  --output work/localization/next/bundle.json
uv run --locked verbum-pipeline review work/localization/next/bundle.json \
  --queue work/localization/next/queue.json \
  --decisions work/localization/next/decisions.json
uv run --locked verbum-pipeline validate work/localization/next/bundle.json \
  --decisions work/localization/next/decisions.json
uv run --locked verbum-pipeline publish work/localization/next/bundle.json \
  --decisions work/localization/next/decisions.json
```

`VERBUM_DATABASE_URL` is required to create the canonical snapshot/publish; `OPENAI_API_KEY`
is required for generating missing translations. Snapshot generation is read-only. The cache
reuses exact source strings and persists successful batches atomically; failed batches can be
rerun. Complete the release decisions truthfully before validation/publication; generation does
not approve anything. Use a new snapshot/output for a new source revision. Review changes to
ambiguous glosses against their original lexeme; a single English word can have several senses.

Deploy migration/API first, then publish the translation release, without concurrent schema
changes. Update the checked-in glossary and release receipt together. STEP reimports preserve
translations owned by this independent source; after changing canonical data, regenerate and
publish the corresponding translations so old descriptions do not remain stale.

## Functional acceptance

On a PT-BR/Brazil device with an updated app:

1. Explore → People/Places/Themes/Events: inspect names and summaries; open Moses/Moisés.
2. Open entity details: aliases, role, dates, geography and sources should read in Portuguese.
3. Explore → Timeline: titles and summaries should be Portuguese.
4. Search `G0026`: open the Greek term; see `ἀγάπη`, `amor`, transliteration and Strong code.
5. Search `H0175`/`Arão`: keep the Hebrew form while displaying Portuguese names/meanings.
6. Open Exodus 4 → Context; inspect related entities and source attribution.
7. Ask a question, including an English question on the Portuguese app: answer should be PT-BR.
8. Repeat on an English device outside Brazil: English content and labels should remain.

Mobile builds are verified locally; on-device acceptance remains with the owner.
