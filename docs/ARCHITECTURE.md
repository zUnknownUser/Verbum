# Architecture

## STEP Bible — 2026-09-15

O adaptador STEP reutiliza o pipeline editorial e as entidades/relações existentes. A migração 0005 acrescenta proveniência por revisão, registros externos, localizações independentes e ocorrências bíblicas. O Store existente integra essas ocorrências no contexto e RAG. Ver [STEP_BIBLE.md](STEP_BIBLE.md).

> How the two apps are built. Product rules live in PRODUCT.md; visual rules in DESIGN_SYSTEM.md.

## Paid API identity — 2026-09-15

Existing feature/client boundaries are preserved. `VerbumAPI`/`VerbumApi` receive an
injected token provider; their production composition uses the Firebase adapter.
Only a paid POST creates an anonymous user when needed. Public GETs stay independent
of Firebase, while search may reuse an existing identity for semantic retrieval.
Android Firebase code stays in `:core:auth`; `:core:clients` remains pure JVM.
The Go `identity` adapter uses the Admin SDK; `httpapi.Protect`, installed by `cmd/api`,
owns verification and UID/IP/process/concurrency limits. Domain stores and synthesis
remain behind their existing interfaces. This supersedes the old keyless-paid-API
description below. Operational contract: [SECURITY.md](../backend/SECURITY.md).

## Layering (both platforms)

```
App ─▶ Features ─▶ Clients ─▶ Models
          │  └─▶ Core / :core:common ─▶ Models
          └─▶ DesignSystem
```

- **Models** — pure value types (PRODUCT.md §22) plus the 66-book canon. No dependencies.
- **Core / :core:common** — non-UI infrastructure: reference parser, and on Android the UDF store.
- **Clients** — interfaces features depend on (`BibleClient`, `PasteboardClient`/`ClipboardClient`,
  `PreferencesClient`), each with a fixture and, later, a live implementation. Features never see a
  backend (PRODUCT.md §37).
- **DesignSystem** — tokens only. No domain knowledge.
- **Features** — one folder/module per feature; reducers + views.
- **App** — a thin shell that assembles dependencies and hosts the root feature.

## iOS: The Composable Architecture

TCA 1.26 is used everywhere a feature has state, effects or navigation (PRODUCT.md §62):

- `@Reducer` + `@ObservableState`; `State / Action / body / View` per feature.
- Big features are composed from small reducers with `Scope`, `ifLet`, `forEach`. Example:
  `ScriptureFeature` = `BookPickerFeature` + `ChapterReaderFeature` + `ReaderSettingsFeature`;
  the parent only integrates, every rule lives in a child.
- Children talk up through `delegate` actions; parents talk down by mutating child state and
  sending child actions. Children never know their parent.
- Dependencies are `@DependencyClient`s with `liveValue` / `previewValue` / `testValue`. The test
  value is unimplemented, so a feature that calls an un-stubbed endpoint fails its test.
- Cross-feature state (reader text size) is `@Shared(.appStorage(...))`.
- Navigation is state: `@Presents` for sheets, split-view column/visibility as enums in state,
  bound to SwiftUI through `sending(...)`.
- Every state transition has a `TestStore` test. Effects are deterministic under `withDependencies`.
- Purely visual components (a verse row, a selection bar) are plain views, not reducers.

## Android: the same shape, in Kotlin

There is no TCA for Kotlin, so `:core:common/arch` provides the minimum needed to keep features
1:1 with iOS (`Reducer`, `Effect`, `Store`, `TestStore`, `pullback`, `pullbackOptional`, `combine`):

- A reducer is `(state, action) -> Reduced(state, effect)`. Pure.
- `Effect.Run` is the only place side effects happen; `id + cancelInFlight` gives TCA-style
  cancellation. `Effect.Send` re-dispatches synchronously.
- `Store` reduces synchronously on the caller (main) thread and launches effects in a scope —
  `viewModelScope`, so state survives fold/unfold and rotation.
- `TestStore` is exhaustive: every `send` must describe the resulting state, every effect-sent action
  must be `receive`d, `finish()` fails on leftovers.
- Dependencies are interfaces; tests use fixtures whose un-stubbed calls throw.
- Compose screens take `(state, send)` and hold no logic.

Features are named and shaped identically on both platforms (`ScriptureFeature`, `BookPickerFeature`,
`ChapterReaderFeature`, `ReaderSettingsFeature`) with matching test suites. When one side changes,
the other changes in the same step.

## Navigation (spec §6, §63, §76)

`AppFeature` holds the selected tab and one `StackState<Path.State>` per content tab (Home,
Explore). Every screen the app can show is a `Path` case — `reader`, `entity`, `entities`,
`books` — so "where the user is" is a value: a stack of destinations. Children never navigate;
they emit `delegate` actions (`openPassage`, `openEntity`, `chapterSelected`) and the shell
decides where to push. Search is its own tab; what it opens is pushed on the last content tab,
which becomes visible, so Back returns to the list the user came from. Journey and Library never
host destinations. On Android the same shape is `List<Destination>` per tab with `Pop`.

## Audio

One `AudioPlayerFeature` on the app shell owns playback; a chapter only *asks* for it
(`reader → scripture → app` delegate `listen`). The player itself is a client: `AudioPlayerClient`
wraps AVPlayer on iOS and a Media3 `MediaController` bound to `PlaybackService` on Android, and
reports back through an event stream (ready, time, playing, ended, failed, remote commands). Tests
script that stream. Recordings come from `ScriptureAudioClient` (helloao), which follows the
reading translation when it has audio and otherwise offers BSB, labelled.

## The Bible Graph (spec §8, §44)

`GraphFeature` is a destination pushed from an entity page; its state is the picture itself —
`Graph.nodes` carry unit coordinates, so the reducer, not the view, decides where things are and
tests can assert positions. `GraphLayout` is pure and identical in Swift and Kotlin:

- **Balanced dozen.** The client is asked for 24 neighbours (`fetchLimit`) and the feature shows
  12 (`visibleLimit`) taken round-robin across kinds in a fixed order (person, event, place,
  theme, passage…), then grouped by kind around the ring so like sits with like.
- **Rings, not forces.** Root at the origin, first degree on the unit circle starting at the
  top. An expansion places up to 6 new nodes on an arc of radius 0.75 behind the expanded node,
  facing away from the node it was laid out around. Nothing moves once placed; expanding never
  re-layouts what is already on screen.
- **Capacity.** 24 nodes. The reducer refuses further expansion and sets `atCapacity`; the page
  says so and offers *Focus here* (a new `GraphFeature` rooted on that node, same stack).
- **One line per connection.** Edges are deduplicated by unordered pair + type (`pairKey`), so
  the same relationship seen from both ends is drawn once.

Views scale unit coordinates to points (first ring fits the shorter side, minimum 90 pt per
unit) and scroll both ways when expansions grow past the viewport. iOS draws edges in a
`Canvas` and nodes as positioned `Button`s; Android draws edges in a `Canvas` and nodes as
offset composables. Both keep a **List** presentation of the same data (§43) — screen readers
start there — and both label edges in words rather than relying on line style.

## The Timeline (spec §4.2, §21.5)

`TimelineFeature` is a destination with two entrances: Explore → Timeline (whole history) and
an entity page → "View in timeline" (`highlight: EntityID`, which opens and scrolls to that
entity's first event). State is the list plus `selectedID` (the row open in place) and the
resolved `entityNames` for the chips.

Dating is a first-class field, never prose: `startYear`/`endYear` are astronomical integers
(negative = BC), `datePrecision` is `exact | approximate | debated | unknown`. `TimelineDates`
turns that into text with the era words from the string catalogue (`%@ BC`, `AD %@`, and the
range forms), and the numbers are interpolated as strings so a pt-BR device shows `1010`, not
`1.010`. Where scholarship is split the fixture says `debated` and the span covers both
positions — the app never picks a side silently. The Android `TimelineDates.Words` carries the
same fragments from `strings.xml`; both platforms' tests pin the same six shapes.

## Verse of the day and its morning notification

One verse a day (spec §14: a short touch, never the centre — no streak, no devotional frame).
`DailyVerses` in Models holds a curated pool of 101 single verses and picks **deterministically
per calendar day**: the pool is shuffled once per 101-day cycle with a seeded generator
(splitmix64 + Fisher–Yates) and walked in order. No repeats within a cycle, and every device —
and the notification — shows the same verse on the same day. The same table and algorithm live
in Swift and Kotlin; `DailyVersesTests` asserts identical dates → verses on both.

The text is never stored: it is read through `BibleClient` in the device's translation, so the
verse follows the reader's language for free.

**Notifications are local, planned ahead, and need no network at 7:00.** `DailyVerseFeature`
(a child of Home) builds a plan of the next 14 mornings on every launch — each entry already
carries its verse text — and hands it to `NotificationClient`:

- iOS: `UNUserNotificationCenter`, one `UNCalendarNotificationTrigger` per morning (well under
  the 64-pending limit). The delegate is installed in `App.init` (`AppLaunch.prepare()`) because
  the system only delivers the launching tap to a delegate that is already in place. Taps flow
  through `NotificationClient.openedVerses` into `AppFeature.openedVerse`.
- Android: the plan is stored in `SharedPreferences`; one inexact `AlarmManager.setWindow`
  (15-minute window, no exact-alarm permission) wakes `VerseAlarmReceiver`, which posts the due
  entry and arms the next. `BootReceiver` re-arms after a reboot. `POST_NOTIFICATIONS` is
  requested from the toggle (Android 13+); taps arrive as `MainActivity` intent extras and are
  relayed through `VerseTapRelay` into `AppFeature.OpenedVerse`.

If the app is not opened for 14 days the notifications stop — by design; the plan is refreshed
on the next launch. The toggle's choice is kept even while the system permission is denied
(Home shows a "turn them on in Settings" line), so re-enabling in Settings is enough.

**Later, when a backend exists (Task 11):** the verse can come from `GET /daily-verse?date=` on
our own server (a reference only — text still through `BibleClient`), and the morning delivery
can move to push (APNs/FCM) with the same `NotificationClient` surface. Third-party
"verse of the day" APIs were considered and rejected: they are English-only, cannot be planned
ahead (which breaks local scheduling), and would put a paid key inside the app binary.

## The backend client (Task 11)

`VerbumAPI` (iOS: `Clients/VerbumAPI`, Android: `:core:clients/api`) is the only code that knows
the wire — one function per route of `api/openapi.yaml`, decoding into the models. The content
clients' `.live` values (`GraphClient`, `SearchClient`, `ContextClient`, `TimelineClient`) are thin
wrappers over it; fixtures stay the preview/test doubles. Features never see it (§37).

- **One shape of a reference.** `PassageReference` encodes as the contract's
  `{bookId, chapter, verseStart?, verseEnd?}` everywhere — on the wire, in `lastRead.json`, in the
  notification plan.
- **Errors are states (§52).** A `Problem` code becomes a typed error (`unknownEntity`,
  `contentUnavailable` → `nil` context, `askUnavailable`…); a transport failure is
  `networkUnavailable`; a body that is not the contract is `malformedResponse`. `message` never
  reaches a user.
- **Offline (§39).** Every `GET` answer is kept on disk keyed by URL. Fresh (the server's
  `max-age=3600`) → served without a request; stale → revalidated; unreachable → the stale answer,
  and only with nothing cached does the call fail. `POST /v1/ask` is never cached (§47).
- **Search (§28).** The server ranks Scripture hits and entities; the device still parses the
  reference (wins outright, opens on return) and matches book names, so both rankings agree and a
  typed `Jn 3:16` never waits on the network. If the service can't be reached the field shows what
  the device knows and says so (`SearchFeature.isOffline`).
- **Contract tests.** Each `api/examples/*.json` is decoded through the live client and must equal
  the fixture client's answer for the same call — the same files the backend proves it serves.
- **Where the backend is.** Both Debug and Release currently point to
  `https://api.vendlydigital.com.br` — a Cloudflare Tunnel to the owner's dev machine, not a
  deployed server; it only answers while that machine and its Docker containers are running.
  iOS: the `VERBUM_API_BASE_URL` build setting → `VerbumAPIBaseURL` in Info.plist, overridable at
  runtime with the launch argument `-VerbumAPIBaseURL http://<ip>:8080` (e.g. back to a LAN dev
  box). Debug's `Info-Debug.plist` opens ATS for plain-HTTP dev backends; Release keeps it intact.
  Android: the `VERBUM_API_BASE_URL` Gradle property → `BuildConfig`, cleartext allowed only in
  the debug manifest. No key ships in either app: the API is keyless and OpenAI is reached only by
  the server (§56).

## Ask Scripture (Task 12, client side)

`AskScriptureClient` (§38) is `POST /v1/ask` and nothing else: the client never generates, never
caches a question (§47), and maps the server's failures to three states — `unavailable` (503, or
a backend without the route), `networkUnavailable`, `failed`. `ScriptureAnswer` is the §30
contract verbatim; the page renders and navigates only from its structured fields, never from
prose (§30), because the server guarantees every `passageReference` was retrieved and verified
before the model could cite it (§31).

`AskFeature` is a destination pushed from Search (§6: search and ask share one field). A query
that reads as a question — a `?`, a question word in English or Portuguese, or four-plus words
that are not a reference or a name (`looksLikeQuestion`, same rule on both platforms) — gets an
"Ask Scripture" row above the results and is asked on return. The page asks once when it appears,
resolves `entityReferences` to names through `GraphClient.entity` (best-effort, unknown ids are
dropped), and renders §13.2 in order: short answer, answer + confidence line, key passages,
explore further, perspectives only when `interpretiveVariance`, sources, and a fixed line saying
what the text is (a checked synthesis, not a word from God — §13.3). An empty `answer` is not a
failure: the page shows the §51 copy, the closest passages if any, and "See search results",
which returns to the Search tab with the same question still in the field (§21.3).

## Voice: talking with the study companion (Realtime API)

Brought forward from §19 at the owner's request. The backend only mints the credential
(`POST /v1/realtime/session` → OpenAI's ephemeral `ek_` secret, §56); the app connects to OpenAI
itself over a WebSocket (`wss://api.openai.com/v1/realtime`, GA events) and streams PCM16 mono
24 kHz both ways. No audio or transcript ever touches our server; nothing is persisted (§47).

- **Clients.** `RealtimeSessionClient` (the secret) and `VoiceClient` (the conversation).
  `RealtimeConversation` is the protocol — `session.update` with instructions, tools, server VAD
  and transcription; `input_audio_buffer.append` up; `response.output_audio.delta` played as it
  arrives; transcripts surfaced; `function_call` items run through a handler and answered with
  `function_call_output` + `response.create` once the active response ends; the user speaking
  over the companion drops queued playback. It runs over an injected `RealtimeTransport`
  (`URLSessionWebSocketTask` / OkHttp) and `VoiceAudio` (`AVAudioEngine` with voice processing /
  `AudioRecord`+`AudioTrack` on the voice-communication route, with the platform AEC), so the
  protocol is tested with fakes on both platforms. WebSocket rather than WebRTC on purpose: no
  native binary dependency; the transport can be swapped behind the same interface later.
- **Half-duplex.** While the companion's audio is coming out of the speaker, and for a 600 ms
  tail after the last frame is heard, microphone chunks are not sent. Without it the speaker
  leaks into the microphone (the simulator/emulator has no echo cancellation; a speakerphone's is
  imperfect), the server VAD hears "speech", transcribes the companion's own words as the reader's
  and answers itself — a runaway monologue. The price is no barge-in: the reader waits for the
  sentence to end (or taps End). Server VAD runs at threshold 0.65 / 800 ms silence so room noise
  is not a question, and the instructions demand one answer per turn, then silence.
- **What it is told (`VoiceScript`).** The product's rules — stay with Scripture, distinguish text
  from interpretation, never claim revelation or foretell (§13.3, §31), point high-stakes questions
  to a professional — plus the page: the chapter's text (from `BibleClient`, cached), the entity's
  facts, or the Ask answer and its passages. It speaks the device language.
- **Grounding (§73).** The Realtime model answers directly, so anything beyond the page goes through
  tools that are the app's own retrieval-first calls: `ask_scripture` → `/v1/ask`, `search_scripture`
  → `/v1/search`. Results carry references in English; an Ask failure is reported to the model as
  "do not answer from memory". `open_passage` hands a reference to the reader after the user agrees.
- **Where it lives.** `VoiceFeature` is a sheet over the page that started it — three entry points,
  no more: the reader toolbar ("Talk about this chapter"), the entity page ("Talk about David") and
  an Ask answer ("Go on out loud"). The sheet shows a status line, the transcript, the passages
  mentioned (tappable), mute and end. Dismissing it stops the microphone and the socket; chapter
  audio pauses when a conversation starts. Failures are named: unavailable (no key on the server),
  microphone denied (with a Settings link), offline, dropped.

## Performance rules

- Never write `@State` (or a `StateFlow`) from a per-frame callback; keep scroll bookkeeping in a
  reference type.
- No `shadow` on views that are not currently elevated — every shadow is an offscreen layer.
- Numerals in lists: `Text(verbatim:)`, not `Text(_, format:)`.
- Anything ticking (playback time) is observed by the smallest possible view, never by the shell.
- Static canon data is computed once (`Division.books`, `BookLanguage.current`).

## Resizable by design (iPhone Duo, foldables, iPad, Split View)

Apple's iPhone Duo guidance and Android's foldable guidance agree, so both apps follow one rule:

**Layout decisions come from size classes, never from orientation or device idiom.**

- iOS: `NavigationSplitView` — collapses to a stack in compact width, tiles in regular width.
  Which column is in front and whether the sidebar shows are reducer state (`compactColumn`,
  `columnVisibility`). Never `UIDevice.orientation`, never `userInterfaceIdiom`.
- Android: `currentWindowAdaptiveInfo().windowSizeClass` — below the medium width breakpoint one
  pane at a time (`compactColumn`), at or above it two panes. Never `Configuration.orientation`.
- Same hierarchy in every posture: opening the device adds a column, it never changes what the
  user can do (Apple: "keep the same hierarchy and functionality across poses").
- Reading columns cap at `Spacing.readingMaxWidth` (680) so wide displays don't produce long lines.
- Safe-area insets are taken per edge (they are asymmetric on an inner display); no
  `UIRequiresFullScreen`; all orientations declared.
- Verified on: iPhone 17 Pro (compact), iPad (regular), Pixel 6 emulator at 1080 and 1800 px wide.

Not yet adopted (iOS 27.1): `ArrangementView` / `reservedRegion` for hinge-aware placement. The
current split view is already correct across poses; hinge APIs are a refinement for later.
