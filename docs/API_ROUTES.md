# API routes

Human-readable companion to `api/openapi.yaml` (the actual contract — this doc explains what each
route is *for*; the spec is authoritative on shapes, required fields and error codes). All routes
are under `/v1`, public and keyless (`security: []`), except where noted. Errors are one shape,
`Problem { code, message }` (§52) — apps switch on `code`, never show `message` to users.

## Content — Bible entities and their web

### `GET /v1/entities/{id}`
One entity's page: role, dates, key passages, sources. Backs the entity detail screen.
404 `unknown_entity` if the id doesn't exist.

### `GET /v1/entities?type=&lang=`
Every entity of one kind (`person`, `place`, `event`, `theme`, `prophecy`, `originalTerm`,
`historicalPeriod` — never `passage`), sorted by name in the requested language. Backs the
Explore lists (People, Places, Themes, Events).

### `GET /v1/entities/{id}/graph?limit=&depth=`
The one-hop neighbourhood around an entity — nodes + the edges among `root + nodes`. `depth` only
ever serves `1` today (the parameter exists so the URL already matches the eventual multi-hop
shape). The apps ask for `limit=24` and pick a balanced dozen themselves; edges are undirected
(David → Goliath is also Goliath's edge). 404 `unknown_entity` for an unknown root.

### `GET /v1/passages/{Book.Chapter}/context`
Chapter-level context: which entities appear, related passages, sources. `reference` is an OSIS
`Book.Chapter` (`1Sam.17`) — verses are ignored, context is per chapter. Missing coverage is
**404 `content_unavailable`**, shown as "no context yet" — never a generated explanation.

### `GET /v1/timeline?entity=`
Every dated period/event, chronological (by `startYear`, longer spans first, unknown dates last).
With `entity=`, only events that id appears in. `entityNames` is included so the app doesn't need
one round-trip per chip to resolve names.

### `GET /v1/search?q=&lang=`
References, books and entities matching a query. Hybrid retrieval for `passages` — Postgres
full-text (lexical) + pgvector cosine similarity against a query embedding (semantic), blended; a
direct reference match (`Jn 3:16`) wins outright, `books` is empty when it does. Falls back to
lexical-only, silently, if no embedding model is configured — never a failed request.

### `GET /v1/daily-verse?from=&days=`
The verse of the day, as a reference only (never the text — the app renders it in the reader's own
translation). `from`/`days` let the app plan a run of mornings (up to 31) in one call, for the 7:00
notification. Not part of the original content contract; added so the server can be the source of
truth once the on-device deterministic picker is retired.

## Ask Scripture and voice — generation, retrieval-grounded

### `POST /v1/ask`
`{question}` → a synthesized answer, **retrieval-grounded with a hard citation gate**: the model
never names a Bible reference itself, it only picks from passages this same hybrid search already
retrieved and verified exist. Nothing citable found → `confidence: "low"` with an empty `answer`,
never a response that looks grounded but isn't. English only (WEB) until a licensed pt-BR
Scripture text exists. Never cached (`Cache-Control: no-store` — questions aren't retained).
503 `ask_unavailable` when no OpenAI key is configured on the server; no offline fallback.

### `POST /v1/realtime/session?model=`
Mints a short-lived OpenAI Realtime API credential (`clientSecret`, an `ek_...` token) so the real
API key never ships in a client app. The server's job ends here — once the client has the secret,
it talks to OpenAI directly over WebSocket; no audio, transcript or model output passes through
this backend. Never cached. 503 `realtime_unavailable` when no OpenAI key is configured.

## Speech — Google Cloud TTS

### `POST /v1/tts`
`{text, language, voice?, speed?, pitch?, format?}` → one complete MP3 (`audio/mpeg`), synthesized
by Google Cloud Chirp 3 HD (`pt-BR-Chirp3-HD-Aoede` by default for pt-BR, `en-US-Standard-A` for
en-US). Text up to 100000 UTF-8 bytes; longer chapters are segmented (2200 bytes/segment, under
Google's 5000-byte ceiling) and reassembled into one file with a short crossfade at each join, so a
chapter reads continuously rather than as audibly stitched clips. Generation can take up to 10
minutes for a long chapter — the response is the full file, never partial or streamed.

Cached persistently on the server, keyed by exact text + normalized voice/speed/pitch/format:
asking for the same chapter again — from any client, any time — never re-contacts Google.
`Cache-Control: no-store` on the HTTP response itself is unrelated to this (it just stops proxies
caching); the server's own cache is invisible to and independent of HTTP intermediaries.

Errors: 400 malformed input or a voice/setting Google rejects, 415 wrong `Content-Type`, 429
`tts_rate_limited` (Google quota), 502 `tts_failed` (bad provider response), 503 `tts_unavailable`
(not configured, or provider down), 504 `tts_timeout`, 408 if the request is cancelled client-side.

## Not routes, but worth knowing

- `GET /v1/tts/config?language=pt-BR` — audio version for mobile cache invalidation, cached for one hour; no provider call. Pass its `version` as optional `revision` in `POST /v1/tts` to prevent deployment races.
- `GET /healthz` — plain liveness check (`ok`), not in the OpenAPI spec, not versioned under `/v1`.
- Nothing here requires auth today (`security: []`). `/me`-scoped routes (accounts, saved data) are
  planned for a later phase and will add authentication when they land.
