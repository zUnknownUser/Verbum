# Roadmap

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

- Task 11 — Replace fixtures with backend API — **Scripture done** iOS ✅ · Android ✅ (2026-09-12); graph/search ⬜
  - `BibleClient.live`: bible.helloao.org (free, keyless, open-licensed; static JSON with
    headings, paragraphs, poetry). BSB for English, Bíblia Livre for Portuguese, by device
    language. Chapters cached on disk; the bundled public-domain WEB (`web.tsv`, all 1,189
    chapters) is the English offline fallback. `networkUnavailable` → "You're offline" (§52).
  - Not yet surfaced from helloao: section headings, paragraph breaks, footnotes, audio links —
    the model has no fields for them yet. Bible Brain dropped: keyed, unclear commercial terms.
- Task 12 — Ask Scripture (only after Scripture, entities, context and search work — §60) — iOS ⬜ · Android ⬜
- Phase 7 — Personal layer (auth, saved items, notes, journey) — iOS ⬜ · Android ⬜
- Phase 8 — Monetization — iOS ⬜ · Android ⬜
- Phase 9 — Collaboration (post-MVP) — iOS ⬜ · Android ⬜

## Golden path (§75)

Search "David" → open David → explore graph → open Goliath → open 1 Samuel 17 → open Context →
open related passage. Reader/entity/context routes exist as local previews; graph interaction
and full acceptance validation are pending. Next primary implementation block: Task 9 GraphFeature,
after owner validation of the current Context increment. Then Task 10 TimelineFeature; Task 12
Ask Scripture/RAG remains gated by reliable retrieval and the earlier foundations (§60, §73).

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
