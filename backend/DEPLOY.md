# Deploying the API to Railway

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
     -d '{"question":"how did David defeat Goliath","language":"en"}'   # 503 = no OPENAI_API_KEY
curl "https://<railway-domain>/v1/tts/config?language=pt-BR"           # {"version":…} = TTS on
```

Startup log lines to expect: `serving Postgres content`, `Google Cloud TTS enabled`,
`realtime session broker, query embedder and ask enabled`.

Note on `/v1/tts`: a chapter can take minutes to synthesise and the handler holds the request
open for it; Railway's proxy allows that, but the apps' request timeouts already account for it.
