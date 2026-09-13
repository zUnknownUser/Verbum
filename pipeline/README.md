# pipeline/ — offline editorial content pipeline

First content block of PRODUCT.md §32–33: import structured JSON, normalize and validate it,
prepare proposals, require human review, and publish into PostgreSQL. Python ≥3.12, uv and
checked-in `uv.lock`. No HTTP write endpoints or app changes.

The full documented sequence remains:

```text
source import → normalization → entity/relationship extraction → human review
              → embeddings → indexing → publish
```

This increment accepts entities/relationships already proposed in JSON, including the existing
fixtures, or produced by the AI extraction stage below. Reliable hybrid retrieval (the Go query
side of §27-28) is the next block. Positive chapter/verse ranges do not prove a verse exists;
full citation validation belongs to the Task 12 Ask block.

## Modules

| Module | Responsibility |
|---|---|
| `models.py` | Strict types, duplicates, enums/ranges, endpoint/source references, matching details, entity evidence and provenance. |
| `normalize.py` | JSON import/normalization preserving prose, IDs and array order. |
| `extract.py` | AI-assisted entity/relationship extraction from raw source text into an ordinary editorial proposal (§32). No new trust path: output still requires normalize/review/publish. |
| `scripture.py` | Loads Scripture text + embeddings into `scripture_verses` for retrieval (§27-29). Outside the review flow on purpose — see "Scripture embeddings" below. |
| `review.py` | Proposal queue and separate human decisions, initially pending. SHA-256 binds review to content/evidence/license metadata. |
| `publish.py` | Transactional upserts, scoped link replacement, audit and repeat-batch idempotence. |
| `cli.py` | Explicit `import`, `normalize`, `extract`, `embed-scripture`, `review`, `validate`, `publish` stages. No approval command. |
| `run.ps1` | Runs the locked Python environment in Docker on Windows; decrypts the DPAPI OpenAI key transiently for `extract`/`embed-scripture` only. |

Dependencies: Pydantic validates imported structured data without silent type conversion;
psycopg connects to PostgreSQL; the `openai` SDK is used by `extract.py` and `scripture.py`,
behind `Client`/`Embedder` protocols so tests never call the network. pytest and Ruff are
development-only tools.

## Scripture embeddings (new)

`scripture.py` implements the §32 "Embedding generation" stage, but only for Scripture text —
it is deliberately **outside** the Bundle/review flow above. That flow exists to gate
*interpretive claims* (entities, relationships, historical facts) that need a human to check
evidence. Loading `scripture_verses` is not that: it copies a translation's verse text
byte-for-byte from a file already in this repo
(`VerbumKit/Sources/Clients/Resources/web.tsv`, World English Bible, public domain) and attaches
a mechanical embedding of that exact text — no claim to review, the same reasoning `cmd/seed`
already relies on for the fixtures.

```powershell
$env:VERBUM_DATABASE_URL = 'postgres://verbum:verbum@db:5432/verbum?sslmode=disable'
.\run.ps1 embed-scripture ../VerbumKit/Sources/Clients/Resources/web.tsv
```

Run on 2026-09-12: all 31,098 WEB verses embedded with `text-embedding-3-large` at 1536
dimensions (`--dimensions`, overridable; pgvector's hnsw/ivfflat indexes reject anything over
2000, so the native 3072 does not fit — see migration `0004_scripture_search.sql`). Real OpenAI
cost was a few cents. Upserts by `(translation, book_id, chapter, verse)`, so re-running is safe
and idempotent; `--limit` embeds only the first N verses for a cheap smoke test.

`vector` and `vector_cosine_ops` are schema-qualified as `public.vector`/`public.vector_cosine_ops` in
both the migration and this module's SQL: the integration tests (and `pipeline/tests/conftest.py`
in general) run each test in its own isolated schema with a `search_path` that does not include
`public`, and pgvector extensions are database-wide, not per-schema.

Nothing here reads this table yet — the Go backend has no query against `scripture_verses`;
that is the next block (hybrid search into `/v1/search`, `internal/store`).

## AI-assisted extraction (new)

`extract.py` implements the §32 "Entity extraction / Relationship extraction" stages. It is a
producer of ordinary editorial `Bundle` JSON, not a new gate: its output is validated by the same
strict Pydantic models as any hand-written editorial batch, then must pass through `normalize`,
`review` and `require_approval` exactly like before. Nothing it proposes is ever written to the
database directly, and it never marks anything approved.

Inputs: a raw source text file, plus a small JSON file with the `Source` and `Provenance`
records — the citation, URL, and (crucially, §34) the **human-verified license**, which this
stage never infers or guesses.

```powershell
# Rotate/store the key once (Windows DPAPI, outside the repo):
..\backend\scripts\Set-OpenAIKey.ps1

# source-input.json: {"source": {"id": "...", "citation": "..."}, "provenance": {"sourceId": "...", "license": "..."}}
.\run.ps1 extract path\to\raw-text.txt --source source-input.json --output work\extracted\bundle.json
.\run.ps1 normalize work\extracted\bundle.json --output work\extracted\normalized.json
.\run.ps1 review work\extracted\normalized.json --queue review\extracted\queue.json --decisions review\extracted\decisions.json
```

From here it is the same human review and publication process as any other editorial batch —
see "Human review and publication" below. The model name defaults to `gpt-4o-mini` and is
overridable with `--model` or `VERBUM_OPENAI_MODEL`; confirm current OpenAI pricing/availability
before relying on the default, since the model lineup changes independently of this repo.

The extraction prompt instructs the model to use only what the supplied text states, never
outside knowledge, and to propose relationships only between entities it also proposed in the
same response — but the model can still misread the text. Nothing bypasses human review because
of this instruction; it only makes bad proposals less likely, not impossible. `OPENAI_API_KEY`
is required (set transiently by `run.ps1` from the DPAPI secret); no call is made without it, and
no fallback silently skips extraction.

## Ready to test

From `C:\Verbum\Verbum\pipeline`, with Docker running:

```powershell
.\run.ps1 validate work/fixtures/normalized.json
```

Expected: `kind=fixture`, `approved=false`, 2 sources, 45 entities, 59 relationships,
25 details, 16 timeline events and 101 daily references. This reads files only.

The queue is `review/fixtures/queue.json`; decisions are `review/fixtures/decisions.json`.
All decisions are pending. No real editorial approval was created by this implementation.

```powershell
.\run.ps1 validate work/fixtures/normalized.json --decisions review/fixtures/decisions.json
```

Expected: exit code 1 and `human approval missing` — the review gate working.

## Reproduce the stages under new filenames

```powershell
.\run.ps1 import ../backend/db/seed/fixtures.json --fixtures --output work/trial/imported.json
.\run.ps1 normalize work/trial/imported.json --output work/trial/normalized.json
.\run.ps1 review work/trial/normalized.json --queue review/trial/queue.json --decisions review/trial/decisions.json
.\run.ps1 validate work/trial/normalized.json
```

Commands refuse to overwrite artifacts. Use a new path for each revision. With native Python
3.12 and uv, replace `.\run.ps1` with `uv run --locked verbum-pipeline`.

## Human review and publication

Read [review/README.md](review/README.md). A human checks each proposal and evidence, then fills
its decision: `approved` or `rejected`, reviewer identity and `reviewedAt` with timezone.
Pending/rejected/missing/duplicate decisions block the whole batch. Changed content/provenance
requires a new review. Local records rely on a trusted editor, not authenticated signatures.

Apply migrations before publishing (from `backend/`; Compose applies them automatically only
on first initialization, not existing volumes):

```powershell
docker compose -p verbum-backend exec -T db psql -U verbum -d verbum -v ON_ERROR_STOP=1 -f /docker-entrypoint-initdb.d/0003_editorial_publication.sql
```

Only after real human review, from `pipeline/`:

```powershell
$env:VERBUM_DATABASE_URL = 'postgres://verbum:verbum@db:5432/verbum?sslmode=disable'
.\run.ps1 publish work/fixtures/normalized.json --decisions review/fixtures/decisions.json --allow-fixtures
```

`db` is the hostname inside the wrapper's Docker network; native Python on Windows instead
uses `localhost`. Fixture publication requires the explicit development flag. Review is checked
before connecting. Concurrent publishers serialize, and errors roll back all changes. The
content hash/review are stored in `content_publications`; a repeated batch returns
`already_published`.

Submitted sources/entities/relationships/details/events are upserted; submitted child lists
replace their previous lists. Omitted records remain; this command does not withdraw previously
published claims or delete entities. A non-empty daily pool replaces the full ordered pool;
an empty pool preserves it. No embedding index is written in this block.

## Editorial input

Bundles have `version: 1`, `kind: editorial`, `content`, `provenance`, `entitySources`.
`content` uses the fixture export's six collections: `sources`, `entities`, `relationships`,
`details`, `timeline`, `dailyVersePool`. Include every referenced source/entity so validation
can run offline. Every entity needs at least one source.

`entitySources` maps each entity ID to SourceReference IDs. `provenance` maps every source
reference ID to `{sourceId, license, page?, section?}` (§33). Editorial batches require license
metadata and reject labelled fixture sources. A filled license field is not legal verification;
the editor must check usage rights and claims (§34).

Unknown fields and duplicate keys are errors. The queue includes proposals, source references
and provenance. Bibliographic metadata stays in PostgreSQL without changing API schemas;
entity pages without curated details expose their submitted entity evidence.

## Tests

```powershell
$env:VERBUM_TEST_DATABASE_URL = 'postgres://verbum:verbum@db:5432/verbum?sslmode=disable'
$env:VERBUM_TEST_API_BINARY = '/repo/backend/bin/api-linux'
.\run.ps1 test -q
```

The prepared Go binary exists locally. Rebuild from the repository root if needed:

```powershell
docker run --rm --mount "type=bind,source=$PWD,target=/repo" -e CGO_ENABLED=0 -w /repo/backend golang:1.24 go build -o bin/api-linux ./cmd/api
```

Tests cover fixture preservation, broken references, review gates, post-review changes,
idempotence, updates and rollback. The end-to-end test publishes through Python, starts Go
against an isolated schema and checks seven HTTP examples plus evidence for a new synthetic
editorial entity. Synthetic approvals exist only in temporary tests. Schemas are isolated and
removed; application tables are untouched. Missing integration variables produce explicit skips.

## Next blocks

OpenAI extraction (`extract.py`) and Scripture embeddings (`scripture.py`) are both implemented
and have made real, working OpenAI calls (2026-09-12). Next: the Go query layer for hybrid
retrieval (`scripture_verses` has no reader yet), real editorial use of `extract.py` against
actual source text, and Task 12 answer synthesis/citation validation. First real editorial
coverage remains §68: approximately 50 people, 30 places, 20 themes and 40 events with sourced
relationships; this fixture batch does not fulfill that milestone.
