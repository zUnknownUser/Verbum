# Deploying the API to Railway

## Verified production state — 2026-09-15

- GitHub repository `zUnknownUser/Verbum`, branch `main`, has an active production trigger.
  Push `37282da` automatically built and deployed; the mobile-only `aca7b63` push was correctly
  skipped by backend watch patterns.
- Deployment `6e0b27e6-6fa1-4e3e-8f3d-81452e819225` applied migrations `0001`–`0005`,
  mounted the original `api-volume`, started API/TTS/Ask, and passed `/readyz` with HTTP 200.
  The persistent audio volume is attached again; the database volume was preserved.
- Lucas explicitly approved the unchanged STEP batch. It was published transactionally in
  29.8 seconds after batching relationship writes; reexecution returned `already_published`.
  Production contains 26,760 STEP records, 29,763 occurrences, 26,782 total entities,
  4,707 total relationships and the same 31,098 Scripture verses.
- Readiness, search, detail, context, graph, daily verse and TTS configuration were checked.
  A real Portuguese Ask request returned cited passages and STEP attribution. A short TTS
  request returned MP3 audio. The temporary anonymous Firebase test account was deleted.
- The first publication attempt lost its remote connection and rolled back completely.
  Its per-relationship round trips were replaced with batched writes in the existing publisher.
  Publication and schema deployment must run sequentially: migrations can wait on publication locks.
- The unused local `verbum-step-api-test` image and disposable restore-test resources were removed.
  Active Railway images, rollback history and persistent data were retained.

### Backups and recovery

A PostgreSQL 18 custom-format backup taken before STEP publication is stored locally at
`pipeline/work/backups/production-before-step-20260915.dump` (207,876,598 bytes, mode 0600,
Git-ignored). Restoring it to a disposable PostgreSQL 18/pgvector database succeeded and reproduced
45 entities, 59 relationships and 31,098 Scripture verses. Production PostgreSQL reported 18.6;
use compatible PostgreSQL 18 client tools when making/restoring these backups.

**Automatic backups remain a required owner action:** the Railway API denied permission to enable
DAILY/WEEKLY schedules. In Railway, select the **pgvector service → Backups**, enable the schedules,
then verify successful backups appear. No automated backup is claimed by this delivery.

Restore to a **new, isolated database** with pgvector, validate counts and functional routes, and
only then switch the API's database URL during a planned recovery. Do not restore the old dump over
the live database: it predates STEP publication. Real approval is also persisted in
`content_publications.review`; the compact publication receipt is versioned under
`pipeline/sources/step/publication-2026-09-15.json`.

## Automated schema migration (2026-09-15)

Deployment settings run `/app/migrate` as the pre-deploy command and explicitly start `/app/api`.
`/readyz` checks the content store and nonempty daily passage pool with a two-second deadline;
`/healthz` remains process-only. No paid provider is contacted by either check. The Docker image includes
that existing binary and `db/migrations/`; migration failure prevents the new API deployment.
The migrations remain additive/idempotent. STEP data itself is **not** downloaded, approved or
published by deployment: use the reviewed Python pipeline described in
[STEP_BIBLE.md](../docs/STEP_BIBLE.md). Database service/image upgrades are separate from schema
migrations. See [Railway pre-deploy documentation](https://docs.railway.com/deployments/pre-deploy-command).

The effective Railway manifest on 2026-09-15 had empty file configuration despite reporting JSON
property mappings. Setting a new `railwayConfigFile` was rejected by the provider's deprecation
policy. Start, pre-deploy, readiness, restart limit (5) and watch patterns were therefore set
explicitly on the production API service. `railway.json` retains the intended values for reference;
verify the effective deployment manifest rather than assuming that the legacy file was applied.


**Before deploying the 2026-09-15 access update:** configure
`VERBUM_FIREBASE_PROJECT_ID`, enable Anonymous auth, and grant the ADC service account
Firebase Authentication user-read permission. Roll out token-capable mobile clients
alongside enforcement; old clients receive 401 on paid POSTs. Full procedure and
proxy limits: [SECURITY.md](SECURITY.md). The 2026-09-14 record below predates this change.

One Railway project, three services: **api** (this Go server, from `backend/Dockerfile`),
**Postgres with pgvector**, and one **volume** on the api service for the TTS MP3 cache.
The apps already point at `https://api.vendlydigital.com.br` — the last step is moving that
hostname from the Cloudflare Tunnel to the Railway service.

Config-as-code lives in `/railway.json` (repo root): Dockerfile path, watch patterns (only
`backend/**` and `api/examples/**` trigger a build — app commits do not), `/healthz` health check.
Railway deprecated `railway.json` in favour of `.railway/railway.ts` (works until 2026-12-01), but
as of 2026-09-14 the new format has no field for the Dockerfile path or watch patterns
(`railway config migrate` turns them into comments and `--apply` would drop them), so it is
kept. `RAILWAY_DOCKERFILE_PATH=backend/Dockerfile` is also set as a service variable so the
build does not depend on the deprecated file.

**Done 2026-09-14** with the Railway CLI (`brew install railway`, `railway login`; the repo
root is linked to project `verbum`, environment `production`). Everything below is the record
of that, kept so it can be redone.

## 1. Postgres (pgvector)

`railway deploy -t 3jJFCA` — the **pgvector** template Railway's own docs link to (the plain
"PostgreSQL" template has no `vector` extension and migration `0004_scripture_search.sql` needs
it). It comes with a TCP proxy and exposes `DATABASE_URL` (**public**, via the proxy) and
`DATABASE_URL_PRIVATE` (`pgvector.railway.internal`, what the api uses).

From `backend/`, against the public URL:

```sh
export VERBUM_DATABASE_URL="$(railway variables --service pgvector --json | python3 -c 'import json,sys;print(json.load(sys.stdin)["DATABASE_URL"])')"
go run ./cmd/migrate     # applies db/migrations/*.sql in order; idempotent, safe to re-run
go run ./cmd/seed        # fixture graph; requires an EMPTY content database
```

Scripture retrieval for `/v1/search` passages and `/v1/ask` needs `scripture_verses` filled
(pipeline, needs `OPENAI_API_KEY`; ~cents of embeddings):

```sh
cd ../pipeline && OPENAI_API_KEY=… uv run --locked verbum-pipeline embed-scripture ../VerbumKit/Sources/Clients/Resources/web.tsv
```

About 15 minutes from a laptop to Railway (one batched upsert per 200 verses — the per-row
write it used to do cost a network round trip per verse and was five times slower over the
public proxy); re-running is an idempotent upsert.

Without it the API still runs: search is lexical/entity-only and Ask answers with the
low-confidence/empty response — never an error.

## 2. api service

New service → GitHub repo `zUnknownUser/Verbum`, branch `main`. Leave **Root Directory empty**
(the Dockerfile copies `backend/` from the repo root; `railway.json` tells Railway where the
Dockerfile is).

Variables:

| Variable | Value | Effect |
| --- | --- | --- |
| `VERBUM_DATABASE_URL` | `${{pgvector.DATABASE_URL_PRIVATE}}` | Postgres store instead of fixtures, over the private network |
| `OPENAI_API_KEY` | `sk-…` | `/v1/ask`, `/v1/realtime/session`, semantic search |
| `GOOGLE_APPLICATION_CREDENTIALS_JSON` | the service-account JSON, one line (or its base64) | `/v1/tts` |
| `VERBUM_TTS_CACHE_DIR` | `/var/cache/verbum/tts` | already the Dockerfile default; set explicitly to match the volume |
| `RAILWAY_RUN_UID` | `0` | **required**: the image runs as UID 65532, Railway mounts the volume as root, and without it the log says `TTS disabled` (confirmed 2026-09-14) |
| `RAILWAY_DOCKERFILE_PATH` | `backend/Dockerfile` | same as `railway.json`; survives its deprecation |

Do **not** set `VERBUM_ADDR`: the server listens on Railway's `PORT`. Optional:
`VERBUM_ASK_MODEL`, `VERBUM_LOG_FORMAT=text`.

Volume: Settings → Volumes → mount path `/var/cache/verbum/tts`. Without it the MP3 cache is
lost on every deploy and every chapter is re-synthesised (paid).

Networking: `railway domain --service api` (test domain), then
`railway domain --service api api.vendlydigital.com.br`. At Cloudflare the hostname was a
**Tunnel**-type record (from the `verbum-api` tunnel on the Windows box) — those cannot be
edited into a CNAME, so: delete it, then add `CNAME api → <id>.up.railway.app`, **DNS only**
(grey cloud — proxied breaks Railway's certificate issuance). Because the zone is on Cloudflare,
Railway also demands a `TXT _railway-verify.api` record with the token from
`railway domain --service api status api.vendlydigital.com.br`; verification took ~2 minutes
after that, then Let's Encrypt issued.

## 3. Check

```sh
curl https://<railway-domain>/healthz                                  # ok
curl "https://<railway-domain>/v1/daily-verse?days=1"                  # store works
curl -X POST https://<railway-domain>/v1/ask -H 'content-type: application/json' \
     -d '{"question":"how did David defeat Goliath"}'   # 401 expected without a Firebase ID token
curl "https://<railway-domain>/v1/tts/config?language=pt-BR"           # {"version":…} = TTS on
```

Startup log lines to expect: `serving Postgres content`, `Google Cloud TTS enabled`,
`realtime session broker, query embedder and ask enabled`.

Note on `/v1/tts`: a chapter can take minutes to synthesise and the handler holds the request
open for it; Railway's proxy allows that, but the apps' request timeouts already account for it.
