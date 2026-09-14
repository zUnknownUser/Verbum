# Deploying the API to Railway

One Railway project, three services: **api** (this Go server, from `backend/Dockerfile`),
**Postgres with pgvector**, and one **volume** on the api service for the TTS MP3 cache.
The apps already point at `https://api.vendlydigital.com.br` — the last step is moving that
hostname from the Cloudflare Tunnel to the Railway service.

Config-as-code lives in `/railway.json` (repo root): Dockerfile path, watch patterns (only
`backend/**` and `api/examples/**` trigger a build — app commits do not), `/healthz` health check.

## 1. Postgres (pgvector)

Deploy the **pgvector** template from the Railway marketplace (the plain "PostgreSQL" template
has no `vector` extension and migration `0004_scripture_search.sql` needs it). Enable public
networking on it (Settings → Networking → TCP proxy) so the one-off commands below can reach it
from your machine; it exposes `DATABASE_URL` (private) and `DATABASE_PUBLIC_URL`.

From `backend/`, with `DATABASE_PUBLIC_URL` copied from Railway:

```sh
export VERBUM_DATABASE_URL='<DATABASE_PUBLIC_URL>'
go run ./cmd/migrate     # applies db/migrations/*.sql in order; idempotent, safe to re-run
go run ./cmd/seed        # fixture graph; requires an EMPTY content database
```

Scripture retrieval for `/v1/search` passages and `/v1/ask` needs `scripture_verses` filled
(pipeline, needs `OPENAI_API_KEY`; ~cents of embeddings):

```sh
cd ../pipeline && uv run --locked verbum-pipeline embed-scripture ../VerbumKit/Sources/Clients/Resources/web.tsv
```

Without it the API still runs: search is lexical/entity-only and Ask answers with the
low-confidence/empty response — never an error.

## 2. api service

New service → GitHub repo `zUnknownUser/Verbum`, branch `main`. Leave **Root Directory empty**
(the Dockerfile copies `backend/` from the repo root; `railway.json` tells Railway where the
Dockerfile is).

Variables:

| Variable | Value | Effect |
| --- | --- | --- |
| `VERBUM_DATABASE_URL` | `${{Postgres.DATABASE_URL}}` (reference to the pgvector service; name may differ) | Postgres store instead of fixtures |
| `OPENAI_API_KEY` | `sk-…` | `/v1/ask`, `/v1/realtime/session`, semantic search |
| `GOOGLE_APPLICATION_CREDENTIALS_JSON` | the service-account JSON, one line (or its base64) | `/v1/tts` |
| `VERBUM_TTS_CACHE_DIR` | `/var/cache/verbum/tts` | already the Dockerfile default; set explicitly to match the volume |
| `RAILWAY_RUN_UID` | `0` | only if the log says `TTS disabled` — the image runs as UID 65532 and Railway mounts volumes as root |

Do **not** set `VERBUM_ADDR`: the server listens on Railway's `PORT`. Optional:
`VERBUM_ASK_MODEL`, `VERBUM_LOG_FORMAT=text`.

Volume: Settings → Volumes → mount path `/var/cache/verbum/tts`. Without it the MP3 cache is
lost on every deploy and every chapter is re-synthesised (paid).

Networking: Generate Domain first to test, then Custom Domain `api.vendlydigital.com.br` and
create the CNAME Railway shows at Cloudflare (proxy off / "DNS only" while validating, or the
tunnel record must be removed first — same hostname).

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
