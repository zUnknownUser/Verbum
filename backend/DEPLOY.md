# Deploying the API to Railway

## Production rollout record — 2026-09-15

- Pushed implementation `710bfdc` and explicit startup configuration `bb366f7` to `main`.
- Applied migrations `0001` through `0005` with the existing migration command against production;
  verified the new tables/view. Existing content remains 45 entities, 59 relationships and 31,098
  Scripture verses. STEP content tables remain empty pending the existing editorial review.
- The GitHub deployment trigger could not be registered: Railway reported that no project member
  has repository access. Reauthorize the Railway GitHub App for `zUnknownUser/Verbum`, then connect
  `main` and verify a trigger exists. A successful git push alone does not confirm deployment.
- API images built successfully, but container creation stalled before application startup; the
  previous image also stalled on rollback. Initial runtime logs repeatedly reported mounting the
  audio cache volume. As a diagnostic, detached `api-volume` without deleting it; the database
  volume was not changed. While detached, audio caching uses the container filesystem and does
  not survive redeployment. Reattachment should be verified with a successful health check.
- Reusing the built `bb366f7` image with the cache detached recovered the API (`c0a3f3c7`,
  HTTP 200 health, search, entity, daily verse and TTS config; unauthenticated Ask returned 401).
  Reattachment interrupted the healthy instance and queued its replacement, so the cache remains
  detached pending infrastructure diagnosis. The original cache files remain on `api-volume`.

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
