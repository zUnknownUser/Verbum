# Roadmap

## Audio safety override — 2026-09-13

- iOS / Android: native PT-BR synthesis disabled after an owner-reported crash;
  live audio explicitly uses English BSB recordings until a Portuguese provider is validated.
- Mini player labels the audio language and no longer displays native-speech preparation errors.
  Portuguese Scripture text is unchanged. Native speech entries below are historical, not active.
- No local Verbum crash report was found during this check; exact crash cause is unconfirmed.
  Device playback validation remains with the owner.

Execution order from PRODUCT.md §60 and §79. One line per step, one mark per platform
(`iOS` = `Verbum.xcodeproj` + `VerbumKit`, `Android` = `android/`). A mark is ✅ only when that
platform builds, its tests pass, and the definition of done in PRODUCT.md is met.

🟡 = implemented and compiled, awaiting owner validation (not a ✅).
Owner instruction (2026-09-12): leave test execution / simulator and device QA to the owner.
New tests are added as source; do not infer they have passed from a successful app build.

Android is a separate native app (Kotlin + Jetpack Compose, Material 3), built step by step in
parallel with iOS from the same spec. Decided 2026-09-11. Nothing is shared between the two apps
except the spec and, later, the backend API contract.

## Phase 0 — Foundation

- **Firebase account flows (§48, owner-requested block, 2026-09-13)** — iOS 🟡 · Android 🟡
  - Optional Home account entry; editorial/native account presentation, email/password sign-in,
    registration, password recovery and Firebase anonymous sign-in, in English and pt-BR.
    Scripture is never gated. No placeholder social-login buttons or claimed cloud sync.
  - Guest registration links credentials to the existing Firebase UID. Session restoration,
    verification email + refresh + resend cooldown, sign-out confirmation and account deletion
    with password reauthentication. Passwords are ephemeral, cleared on dismissal/completion;
    safe typed errors, duplicate-submit protection and password-manager semantics.
  - Separate account reducers/stores; SDK hidden behind `AccountClient`. Android Firebase adapter
    lives in `:core:auth`, keeping `:core:clients` and `:core:models` pure JVM. Existing design
    tokens, reader, audio and the parallel Ask/Voice work are preserved.
  - Firebase files are app resources: `Verbum/GoogleService-Info.plist` and
    `android/app/google-services.json`. The Android JSON is no longer bundled into iOS.
  - Both app builds succeed. New model/reducer test sources were added; test execution and
    real-account/device validation remain with the owner. See `AUTHENTICATION.md` for setup and QA.
  - iOS Debug/Release bundle ID aligned to the supplied Firebase plist, `com.nexussoft.verbum`,
    with the owner's explicit approval. A previous `com.NexusSoft.Verbum` installation may remain
    separate. Enabling Firebase providers and real-account QA remain owner tasks.

- **Task 1 — Project architecture** — iOS ✅ · Android ✅ (2026-09-11)
  - iOS: `VerbumKit` local package with `Models`, `Core`, `DesignSystem`, `Clients`, `Features`;
    app target is a thin shell; TCA 1.26.2; test targets wired.
  - Android: Gradle 9.6 / AGP 9.4 (built-in Kotlin) with `:core:models`, `:core:common`,
    `:core:clients` (pure JVM), `:core:designsystem`, `:app`; JVM unit tests wired.
- **Design system foundation** — iOS ✅ · Android ✅ (2026-09-11)
  - Tokens per §64 on both platforms, same names, same values. Direction in DESIGN_SYSTEM.md.
- **Audio — global player** — iOS ✅ · Android ✅ (2026-09-12)
  - `AudioPlayerFeature` on `AppFeature` (survives navigation), `AudioPlayerClient` (AVPlayer /
    Media3 ExoPlayer behind a client), `ScriptureAudioClient` (helloao MP3 + narrators for English;
    Portuguese native speech is the pending-validation increment below). Mini player in the
    iOS 26 tab-bar accessory / above the Android nav bar; Listen button in the reader; ±15 s, rate
    cycle, narrator kept across chapters, chapters chain at the end.
  - Background: iOS `UIBackgroundModes audio`, spoken-audio session, Now Playing + remote
    commands; Android `MediaSessionService` with foreground playback + notification.
  - Phase 2 (not started): verse timings → timestamp-to-verse (RAG context), offline downloads.
- **PT-BR native speech + media controls** — iOS 🟡 · Android 🟡 (2026-09-12)
  - PT-BR only: exact text from the reading client's cached translation, system voice, labelled
    "Leitura automática". English retains its existing helloao recordings.
  - iOS: `AVSpeechSynthesizer` produces a local CAF; Android: `TextToSpeech` produces local WAV
    chunks (without truncating long chapters), concatenated by streaming file I/O. Both then use
    the existing player: pause, seek ±15 s, speed, background playback and chapter chaining.
  - Android selects an installed, offline pt-BR voice; iOS selects an available pt-BR voice.
    If unavailable/preparation fails, show a clear message and allow retry, never silently play English.
  - Generated-audio cache: four chapters / approximately 100 MB, keyed by text and voice.
    Partial files are cleaned on cancellation/error; synthesis has timeouts. First preparation
    requires waiting; cached playback is reused. Disk cache is evictable, not an offline-download guarantee.
  - iOS: current playback state replay + subscription ownership, Now Playing artwork/metadata,
    explicit remote commands and periodic elapsed-time synchronization. Standard OS media surface,
    not an ActivityKit Live Activity. Lock Screen / Control Center / Dynamic Island require device QA.
  - Android: current playback state replay; MediaSession ±15 s button preferences and seek increments.
    Exact button placement remains controlled by the OS/controller.
- **Fluency / bounded work pass** — iOS 🟡 · Android 🟡 (2026-09-12)
  - Chapter text memory cache: 24-entry LRU; disk cache still available after memory eviction.
  - Android cache I/O and chapter decoding off-main; shell observes navigation independently of
    audio state so playback ticks only update the accessory. Player time ticks reduced to 1 Hz,
    with no idle playback ticker updates on iOS. No measured FPS or latency claim yet.
  - New chapter audio cancels the previous subscription/load; stopped or mismatched results are ignored.
  - Earlier scroll fix: iOS navigation bar stays visible to avoid the observed bar-layout loop;
    Android navigation protects outgoing animated depth from an out-of-bounds access on Back.
- **Performance pass** (2026-09-12) — iOS: scroll tracking moved out of `@State` (page re-rendered
  every frame), no idle shadow layers on 66 shelf blocks, verbatim numerals instead of
  `FormatStyle` per row, `FlowLayout` measurement cache, cached `Division.books`, memoised
  device language, mini player scoped so time ticks don't re-render the tabs.
- **Verse of the day + 7:00 notification + share** — iOS ✅ · Android ✅ (2026-09-12)
  - `DailyVerses` (Models, both platforms, same 101-verse pool and same per-day pick),
    `NotificationClient` (UNUserNotificationCenter / AlarmManager + receivers),
    `DailyVerseFeature` on Home: text in the device translation, open in reader, system share
    sheet, "Every morning at 7:00" toggle. Tap on the notification opens the verse. Replaces the
    older "Today" passage card. Design and later push/API path in ARCHITECTURE.md.
- Analytics abstraction (§46) — iOS ⬜ · Android ⬜
- Error taxonomy (§52) — iOS ⬜ · Android ⬜
- Persistence foundation (§39) — iOS ⬜ · Android ⬜
- CI — iOS ⬜ · Android ⬜

## Phase 1 — Scripture Core

- **Task 2 — Foundational models** — iOS ✅ · Android ✅ (2026-09-11)
  - §22 types verbatim (`BibleEntity`, `BibleEntityType`, `BibleRelationship`, `RelationshipType`,
    `BiblePassage`). `BibleBook` carries the 66-book canon (OSIS ids, abbreviations, chapter counts;
    deuterocanon deferred — licensing, §34). `PassageReference` = book + chapter + optional verse
    range, with `formatted`. Enum wire strings asserted by tests on both platforms.
- **Task 3 — PassageReference parser** — iOS ✅ · Android ✅ (2026-09-11)
  - `PassageReferenceParser` in `Core` / `:core:common`. Pure, deterministic, typed errors
    (empty, malformed, unknownBook, chapterOutOfRange, invalidVerseRange). Accepts the six spec
    inputs plus abbreviations with periods, Roman prefixes, `–`/`—` ranges, `.` as chapter
    separator, and single-chapter books (`Jude 3` → 1:3). Same 15 tests on both platforms.
- **Task 4 — BibleClient** — iOS ✅ · Android ✅ (2026-09-11)
  - iOS: `@DependencyClient BibleClient` (§38 signatures), `liveValue`/`previewValue` = fixtures
    until Task 11, `testValue` unimplemented. Android: `BibleClient` interface + `FixtureBibleClient`.
  - Typed errors: `unknownBook`, `contentUnavailable`, `verseOutOfRange` (§52).
  - Fixture = World English Bible (public domain), fetched from bible-api.com, generated as source
    on both platforms from one JSON: Gen 1, 1 Sam 16–17, 2 Sam 5, Ps 23, Ps 51, Matt 6, John 3,
    Rom 8 (271 verses). `chapter` = one passage per verse; `passage` = joined range.
  - Backend for Scripture became bible.helloao.org (see Task 11).
- **Task 5 — ScriptureFeature** — iOS ✅ · Android ✅ (2026-09-11)
  - Book/chapter navigation, reading, loading, error, Dynamic Type + user text size, verse selection
    (with copy + citation). Composed of `BookPickerFeature`, `ChapterReaderFeature`,
    `ReaderSettingsFeature`; every transition under `TestStore` on both platforms.
  - Resizable by size class (iPhone Duo / foldables / iPad): split view on iOS, two-pane by
    window size class on Android. See ARCHITECTURE.md.
  - Android gets the TCA-shaped store (`:core:common/arch`) here.
  - Design pass (2026-09-11, after review): paper/ink reader modelled on Apple Books — see
    DESIGN_SYSTEM.md "The reading page". Views only; reducers and tests unchanged.
  - Book picker redesigned as a bookshelf mapping the canon (2026-09-11). `BibleBook.Division`
    added to Models with tests on both platforms.
- **Task 6 — SearchFeature** — iOS ✅ · Android ✅ (2026-09-11)
  - `SearchResponse` (§27 groups), `BookMatcher` in Core (exact match outranks prefix, canon
    order), `SearchClient` with fixtures: parsed reference wins outright (§28), then books, then
    29 labelled fixture entities from §68 (people, places, themes).
  - `SearchFeature`: 250 ms debounce, in-flight cancellation, stale answers dropped, return key
    opens a parsed reference at once. Presented from the reader; `openPassage` lands in the reader.
    Entity rows listed but not tappable until Task 7. Same tests both platforms.
- **i18n — EN (base) + pt-BR, by device locale** — iOS ✅ · Android ✅ (2026-09-11)
  - Book names, abbreviations and division titles in pt-BR live in Models (one generated table for
    both platforms). Parser accepts English always plus the device language on top (`Jn` = Jonas in
    pt, John in en; `Jo` = João). `formatted` follows the device language.
  - Interface strings: `Localizable.xcstrings` (Features) / `values` + `values-pt-rBR`. Scripture
    text stays in the translation's language (WEB, English) until a licensed pt-BR text arrives.
- **Navigation shell (§6, §79.9)** — iOS ✅ · Android ✅ (2026-09-12)
  - `AppFeature`: Home · Explore · Journey · Library + Search. Content tabs own a stack of
    destinations (`reader`, `entity`, `entities`, `books`); search results land on the last content
    tab. iOS `TabView(.sidebarAdaptable)` + system search tab; Android `NavigationSuiteScaffold`.
  - Home (§5): greeting, the question, search, **Continue reading** (reader persists `lastRead`),
    **Today** (one sourced passage per day, deterministic; no streak). Explore (§7): People, Places,
    Themes, Events, Books — Timeline/Graph join when they exist. Journey/Library: honest empty pages.
  - Reader is now a destination; the shelf is a sheet in compact width, a column in regular.
    Golden path (§75) runs on one stack: Explore → People → David → Goliath → 1 Samuel 17.

## Phase 2 — Entities

- **Task 7 — EntityDetailFeature** — iOS ✅ · Android ✅ (2026-09-12)
  - Models: `EntityDetail` (§9 facts, hedged dates), `GraphSnapshot` (§44), `SourceReference` (§33).
    `GraphClient` (§38 `entity`/`neighbors` + `detail`) with a labelled fixture graph: 37 entities
    (people, places, themes, events, passage nodes), 59 sourced relationships, 25 details; one Swift
    table ported to Kotlin.
  - `EntityDetailFeature` (page = detail + one-hop neighbourhood, limit 12 per §8.1) and
    `EntityExplorerFeature` (`StackState` of pages; passages bubble to the reader). Search entity
    rows now open pages. Golden path §75 works end to end minus graph/context: David → Goliath →
    1 Samuel 17.

- **Home — voluntary exploration starting point** — iOS 🟡 · Android 🟡 (2026-09-12)
  - Owner-requested additive extension to §5/§14: one quiet card after existing Home sections,
    "Como você está chegando hoje?". Existing sections and design tokens are unchanged.
  - Nine choices; deterministic editorial-preview reading paths, two structured Scripture sources
    each, a guiding reading question, reader/context navigation, retry/change-selection states.
  - `ExplorationRequest` / `ExplorationPlan` + `GuidedExplorationClient` isolate future retrieval
    from UI/reducers. No RAG endpoint, AI synthesis, generated history or emotional diagnosis.
  - Selection exists only in navigation state, with no profile persistence or analytics logging.
    Existing reader last-read persistence continues normally when a passage is opened.
  - Historical context/people/themes use Context's existing, labelled coverage only; missing
    data is not fabricated. Full personalized synthesis remains Task 12 after reliable retrieval.

## Phase 3 — Context Engine

- **Task 8 — ContextFeature (local preview)** — iOS 🟡 · Android 🟡 (2026-09-12)
  - Chapter-level client/model/reducer/view; entry at start/end of reader; entities, shared-entity
    related passages, source links and before/after chapter navigation. EN + pt-BR interface.
  - Explicit fixture provenance; missing coverage is distinct from load failure; no generated
    historical commentary. Reader content retained when returning from Context.
  - Client/reducer regression tests added, not executed. Full editorial context and definition-of-done
    validation remain pending; do not mark complete solely because navigation is implemented.

## Phase 4 — Bible Graph

- **Task 9 — GraphFeature** — iOS ✅ · Android ✅ (2026-09-12)
  - Entered from an entity page ("Explore the graph", §8: the graph starts with the selected
    entity). One degree, 12 visible nodes **balanced across kinds** (the feature asks the client
    for 24 and picks round-robin, so David shows places and events, not only psalms and people).
  - **Deterministic ring layout** (`GraphLayout`, same arithmetic in Swift and Kotlin), no force
    simulation (§60 Task 9). Tap opens the entity/passage; long press → *Expand connections*
    (+6 nodes on an arc behind the node, incremental) or *Focus here* (a new graph page rooted
    there, same stack). Hard cap of 24 nodes, then the page says so and asks you to refocus.
  - §8.3 shapes + a symbol per kind (never colour alone); edge labels in words; a **List**
    presentation of the same connections (§43) — VoiceOver starts on it. Reduce Motion honoured.
  - Not in Explore yet: the graph needs a selected entity to start from (§21.4). Explore keeps
    People/Places/Themes/Events/Books; a Graph entry would need a chooser first.

## Phase 5 — Timeline

- **Task 10 — TimelineFeature** — iOS ✅ · Android ✅ (2026-09-12)
  - `TimelineEvent` + `TimelineDatePrecision` (§22.5 verbatim, plus `sourceReferenceIds` so
    dating claims are traceable, §33). `TimelineClient` with a curated fixture: the spec's own
    16 periods/events (§4.2), Abraham → Pauline missions, in one table for both platforms.
  - **Uncertainty is data, not decoration**: nothing is marked `exact`; the Exodus, the birth of
    Jesus and the crucifixion are `debated` and their span covers both scholarly positions
    instead of picking one. Rendered as `c. 1010–970 BC`, `1446–1250 BC · debated`,
    `c. 516 BC – AD 70` (pt-BR: a.C./d.C.), hollow markers for debated/unknown.
  - `TimelineFeature`: vertical spine, bar for a span / dot for a moment, tap opens the event in
    place (summary, chips for its people and places → entity pages, source line). Entered from
    Explore (new "Timeline" entry) and from an entity page ("View in timeline", only when the
    entity is on it), which scrolls to and opens its first event.
  - Entity names for the chips are resolved through `GraphClient.entity`; Task 11 should carry
    them on the event instead of N round-trips.

## Later

- Task 11 — Replace fixtures with backend API — **Scripture done** iOS ✅ · Android ✅ (2026-09-12); graph/search/context/timeline **iOS ✅ · Android ✅ (2026-09-13)**
  - **Apps go live (2026-09-13):** `VerbumAPI` in `Clients/VerbumAPI` (iOS) / `VerbumApi` in
    `:core:clients/api` (Android) — one function per route,
    the live values of `GraphClient`/`SearchClient`/`ContextClient`/`TimelineClient` over it;
    typed errors from `Problem` codes (§52); on-disk cache with stale-if-error (§39); contract tests
    decoding every `api/examples/*.json` to the fixture clients' answers. Search degrades to
    device-only results with an "unreachable" line instead of an empty list. Base URL per
    configuration (`VERBUM_API_BASE_URL`: an Xcode build setting / a Gradle property), plain HTTP
    allowed only in Debug (ATS / `usesCleartextTraffic`). Design in ARCHITECTURE.md.
- **Task 12 — Ask Scripture, client side** — iOS ✅ · Android ✅ (2026-09-13)
  - `AskScriptureClient` → `POST /v1/ask`, `ScriptureAnswer` = §30 verbatim; `AskFeature` +
    `AskView`/`AskPane` render §13.2 from structured fields only, with confidence and
    interpretive variance shown (§31) and the §51 fallback to search results (§21.3). Entered from
    Search when the query reads as a question. Tests on both platforms; contract-level tests for
    the request body and every failure state. Design in ARCHITECTURE.md.
  - Pending on the backend side, not blocked here: the product decision on
    `internal/ask/safety.go` (docs/BACKEND_RAG.md, Bloco 8) and pt-BR (§34 licence).
- **Voice — talking with the study companion (§19, brought forward)** — iOS ✅ · Android ✅ (2026-09-13)
  - `RealtimeSessionClient` → `POST /v1/realtime/session`; `VoiceClient`/`RealtimeConversation` over
    a WebSocket with injected transport and audio (protocol tested with fakes); `VoiceScript`
    (rules + page content + tools `ask_scripture`/`search_scripture`/`open_passage`, grounded through
    `/v1/ask` and `/v1/search`, §73); `VoiceFeature` sheet from the reader, an entity page and an
    Ask answer. Microphone permission on both platforms. Design in ARCHITECTURE.md.
  - Owner QA on devices: echo cancellation on speaker, Bluetooth headsets, interrupting the
    companion, a tool call mid-sentence, pt-BR voice quality. The server must run with
    `OPENAI_API_KEY` for the endpoint to answer.
- **Read-along highlighting (owner-requested, parked 2026-09-13)** — iOS ⬜ · Android ⬜
  - Words lit as the narration reaches them; tap a word to seek; follows pause, ±15 s, scrub, rate
    and chapter chaining (position = f(time), binary search over per-word timings, 10–20 Hz ticks
    while highlighting only). Needs a timings table the helloao MP3s do not carry: agreed design is
    a backend endpoint `GET /v1/audio/timings/{translation}/{book}/{chapter}/{narrator}` that
    transcribes the recording once with `whisper-1` (word timestamps, ~US$0.006/min), aligns the
    words to the chapter text and stores the result. pt-BR text vs English audio → per-verse
    highlighting only. Blocks: backend → iOS → Android.
  - **Contract first (2026-09-12):** `api/openapi.yaml` — the §45 routes plus `/v1/daily-verse`,
    written from the client interfaces the apps already have — and `api/examples/` generated from
    the fixtures (`api/scripts/gen-examples.py`), to be the inputs of contract tests on both apps.
  - Decided: **Go** for the API (`backend/`, net/http + pgx/sqlc + Postgres, Docker), **Python** for
    the content pipeline (`pipeline/`, §32) — they meet only in the database. Lucas develops the
    backend on his own server machine; same monorepo.
  - **Backend skeleton (2026-09-12):** `backend/` — Go, standard library only. `cmd/api` +
    `internal/{domain,store,httpapi,dailyverse}`; the in-memory store serves `db/seed/fixtures.json`
    and `contract_test.go` proves every `api/examples/*.json` byte-for-byte; Postgres schema in
    `db/migrations/`; Dockerfile (distroless), compose for a local DB, README with the next steps.
  - **Backend-only block 1 (2026-09-12): PostgreSQL complete and tested.** `internal/store/postgres`
    implements `Store`; `VERBUM_DATABASE_URL` selects it; `cmd/seed` atomically imports the existing
    development fixtures into empty content tables. Migration 0002 preserves editorial ordering.
    Seven API examples, fixture-wide store parity, missing content, cancellation and seed rollback
    validated against PostgreSQL 17 with Go 1.24 in Docker. No app code/builds/tests in this block.
    Backend/RAG documentation map and remaining checkpoints: `docs/BACKEND_RAG.md`.
  - Next: (a) deployment configuration for the backend;
    (b) `.live` clients + contract tests on iOS and Android; (c) daily verse from the server;
    (d) editorial pipeline and the real content load (§68).
  - **Backend-only block 2 (2026-09-12): initial editorial pipeline implemented.** Python 3.12,
    uv lockfile, JSON import/normalization, source-backed review queue and per-item human decisions,
    content-bound approval checks, atomic PostgreSQL publication and audit (migration 0003).
    Twenty Python tests include Python publication → Go HTTP examples in isolated schemas.
    The real local queue remains pending; AI extraction, embeddings and real editorial coverage
    are still pending. Owner chose continued local implementation before external deployment.
  - **Backend-only block 3 (2026-09-12): AI-assisted extraction stage.** `pipeline/extract.py`
    implements the §32 entity/relationship extraction stage: raw source text + editor-supplied
    `Source`/`Provenance` (license stays a human decision, §34) go through OpenAI (JSON mode) and
    come back as an ordinary `editorial` Bundle — no new trust path, the same strict Pydantic
    validation and human review/publish gate as any hand-written batch. `run.ps1` decrypts the
    DPAPI-stored key only for the `extract` command, passes it to the container by env var name
    only, and clears it after. 28 Python tests (8 new) pass with a fake OpenAI client; no live
    OpenAI call has been made and the key's validity remains unexercised. Timeline-event
    extraction, real source text, and embeddings/hybrid retrieval remain for later blocks.
  - **Backend-only block 4 (2026-09-12): Scripture text + embeddings for retrieval.** Owner
    tested the OpenAI key with a real call (negligible cost) and approved `text-embedding-3-large`.
    Source: WEB (public domain, already bundled in the repo) — English only; PT-BR stays out
    until a real license is confirmed (§34). `pipeline/verbum_pipeline/scripture.py` loads
    verbatim verse text + embeddings into a new `scripture_verses` table (migration
    `0004_scripture_search.sql`, pgvector; dev Postgres image switched to `pgvector/pgvector:pg17`).
    Deliberately outside the editorial review flow (no interpretive claim to review, same
    reasoning as `cmd/seed`). pgvector's HNSW index caps at 2000 dimensions, so embeddings are
    requested at 1536 (OpenAI's `dimensions` parameter) rather than 3-large's native 3072. Ran
    for real: all 31,098 WEB verses embedded and loaded (a few cents). 37 Python tests (9 new);
    full Go suite re-verified against migration 0004, no regressions. No Go code reads this table
    yet — the hybrid-search query layer into `/v1/search` is the next block.
  - **Backend-only block 5 (2026-09-12, owner-requested, outside the original plan): OpenAI
    Realtime session broker.** `POST /v1/realtime/session` (`internal/realtime`) mints a
    short-lived OpenAI Realtime client secret so a future app feature can connect directly to
    OpenAI for voice without ever holding the real key. Not part of the read-only content
    contract — it doesn't touch `store.Store`. Runs continuously off `OPENAI_API_KEY` (unlike
    the pipeline's transient per-command use); absent key degrades to `503
    realtime_unavailable`, not a startup failure. Verified with a real call (genuine `ek_...`
    secret returned). 8 new Go tests (fake broker; no live calls in CI). Server-side plumbing
    only — no client has been built or tested against it yet.
  - **Backend-only block 6 (2026-09-12): hybrid search query layer.** `Store.SearchPassages`
    blends Postgres full-text search with pgvector cosine similarity over `scripture_verses`
    (block 4's data); `internal/embeddings` embeds the query at request time. `/v1/search`'s
    `passages` field, always empty before, now returns real hits — same response shape. Found
    and fixed a real bug via the integration tests: pgvector's `<=>` operator needs
    `OPERATOR(public.<=>)` schema-qualification, not just the `vector` type, wherever
    `search_path` excludes `public` (every isolated test schema). Degrades to lexical-only
    search, not a failed request, when `OPENAI_API_KEY` is absent or the embedding call fails.
    Verified with real queries against the full 31,098-verse corpus, including a natural-language
    question sharing no words with the Job chapters it correctly surfaced. Ranking is a
    documented-as-tunable heuristic (sum of both channels' scores), not a calibrated relevance
    model; direct-reference priority (§28) still relies on the apps' on-device parser.
  - **Backend-only block 7 (2026-09-13): Task 12 — Ask Scripture backend.** `POST /v1/ask`
    (`internal/ask`, `internal/synthesis`) returns exactly the §30 response contract. The model
    never names a Bible reference itself — it only picks indexes into evidence `internal/ask`
    already retrieved via block 6's hybrid search and confirmed has real text; anything
    out-of-range is silently dropped, and an answer citing nothing verifiable is replaced with
    the same empty/low-confidence fallback used when retrieval finds nothing (§73: no generation
    before reliable retrieval). Verified with real questions: a grounded answer citing Job 2:7
    for "why did job suffer", an honest empty fallback for an off-topic question, and correct
    `interpretiveVariance: true` on a genuinely disputed question (universal salvation).
    Real-testing finding: the §31 professional-help guardrail was **not reliably followed by
    prompting alone** — a direct "I feel hopeless and depressed" question got a purely
    devotional answer twice, even after strengthening the prompt. Fixed with a second,
    deterministic layer (`internal/ask/safety.go`): a fixed, non-generated note is appended
    whenever the question matches a conservative keyword list, unless the model's own answer
    already covers it. This floor has not had a broader safety review beyond the cases tested
    here. `entityReferences` is always empty this increment (no entity-linking yet); PT-BR stays
    blocked on the same license gap as block 4; `VERBUM_ASK_MODEL` (default `gpt-4o-mini`)
    controls the synthesis model independently of the pipeline's extraction model.
  - **Backend-only block 8 (2026-09-13): direct-reference search, entity linking, observability.**
    `internal/reference.ParseVerse` gives `/v1/search` §28's "reference wins outright" for the
    API's own book-name/OSIS-id vocabulary (narrower than the apps' parser on purpose) — a
    verified hit skips hybrid retrieval and its embedding call entirely. `Store.EntitiesForPassages`
    (`entity_key_passages`) links Ask citations to the entity graph, verified end-to-end ("how
    did David defeat Goliath" → linked to the real fixture David/Goliath/Saul/Elah/faith
    entities); a linking failure degrades to an empty list, never fails the answer. Observability
    (§54) scoped down from OpenTelemetry to structured JSON logs + a per-request correlation id
    (`internal/reqid`) — no tracing backend has been chosen yet, so spans/exporters would be an
    unrequested new dependency; latency for embedding/retrieval/synthesis and citation/reference
    failures are logged and correlate by id today, with a documented migration path to real otel
    spans later. Owner feedback captured but not yet acted on: Ask's professional-help note
    should end up grounded in what the answer says, not triggered by matching the question
    against a keyword list — revisit `internal/ask/safety.go` with the owner before extending it.
  - `BibleClient.live`: bible.helloao.org (free, keyless, open-licensed; static JSON with
    headings, paragraphs, poetry). BSB for English, Bíblia Livre for Portuguese, by device
    language. Chapters cached on disk; the bundled public-domain WEB (`web.tsv`, all 1,189
    chapters) is the English offline fallback. `networkUnavailable` → "You're offline" (§52).
  - Not yet surfaced from helloao: section headings, paragraph breaks, footnotes, audio links —
    the model has no fields for them yet. Bible Brain dropped: keyed, unclear commercial terms.
- Task 12 — Ask Scripture (only after Scripture, entities, context and search work — §60) — iOS ✅ · Android ✅ (2026-09-13)
  - Backend: `POST /v1/ask` (backend-only block 7 below). Client side: see the "Task 12 — Ask
    Scripture, client side" row above. End-to-end against a deployed backend still to be
    exercised by the owner (the LAN server must run an image built from `92ee889` or later).
- Phase 7 — Personal layer (auth, saved items, notes, journey) — iOS ⬜ · Android ⬜
- Phase 8 — Monetization — iOS ⬜ · Android ⬜
- Phase 9 — Collaboration (post-MVP) — iOS ⬜ · Android ⬜

## Golden path (§75)

Search "David" → open David → explore graph → open Goliath → open 1 Samuel 17 → open Context →
open related passage. Every route exists on both platforms and, since 2026-09-13, reads the
backend (`VerbumAPI`) instead of fixtures; Ask Scripture is reachable from the same Search field.
Full acceptance validation on devices against a deployed backend is the owner's.

## Owner validation for the current delivery

- PT-BR: first preparation, cached replay, missing downloaded voice, offline cached text,
  long chapter (Psalm 119), cancelling preparation and switching chapters rapidly.
- Playback: play/pause, ±15 s, closing/opening the screen, Control Center/notification,
  headphones, chapter chaining, iOS Dynamic Island on a compatible physical device.
- Performance: scroll a long chapter with audio playing, return from Context, switch tabs;
  profile CPU/memory/frame pacing on physical devices. Builds do not establish these budgets.
- Home: every choice → cited chapter → Context → Back → change choice; unsupported context;
  accessibility text sizes/VoiceOver/TalkBack. Confirm no emotional choice is persisted.
- Tests: new/updated test sources await owner execution; app builds have been checked only.
