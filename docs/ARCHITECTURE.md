# Architecture

> How the two apps are built. Product rules live in PRODUCT.md; visual rules in DESIGN_SYSTEM.md.

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
