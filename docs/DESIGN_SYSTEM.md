# Design System

> Source of truth for visual direction and the token set in `VerbumKit/Sources/DesignSystem`.
> Product rules it serves: PRODUCT.md §3.4 (calm premium), §40–43 (visual, motion, type, accessibility), §64 (tokens).

## Direction

**Apple-system, light-first, editorial.** The app should feel like it shipped with iOS 26 — a
sibling of Books and Notes — not like a themed product.

Decided 2026-09-11. This overrides PRODUCT.md §40 ("near-black and warm-light themes"): the brand
does not carry a dark editorial theme. Dark appearance still works because every colour is a
system semantic colour, but it is the OS's dark mode, not a designed theme. Reason: the owner wants
the product to read as native Apple; a custom near-black theme would fight Liquid Glass materials
and system vibrancy on iOS 26.

## What the market shows (Sept 2026)

| App | What it gets right | What we avoid |
|---|---|---|
| Mere Bible (ex-NeuBible, designed by an Apple UI designer) | White space, typography-only hierarchy, no chrome | — |
| Dwell | Calm, focused, premium restraint | Audio-first |
| ESV (Crossway) | Real book typography for Scripture | — |
| YouVersion | Ubiquity | Feed, streaks, image cards, clutter (PRODUCT.md §2, §3.4) |
| Literal Word / Blue Letter Bible | Depth of lexical data | Dense, tool-like UI |

Sources: theleadpastor.com, learnofchrist.com, faithwall.app, Drew Coffman's *The Bible App Deep-Dive*, ChurchMag on NeuBible.

Takeaway: the best-loved Bible readers on iOS win on typography and silence. Nobody in the category
owns "native Apple + connected exploration". That is the gap.

## iOS 26 rules we follow

- **Liquid Glass only on the navigation layer.** Tab bar, toolbars, sheets, search get glass from the
  system for free. Never `.glassEffect()` on content, lists, cards or full-screen backgrounds.
- **System colours only.** `Palette` wraps `UIColor` semantic colours. Increase Contrast, Reduce
  Transparency and dark appearance are handled by the OS.
- **Two system type families.** SF Pro (`.default`) for interface, New York (`.serif`) for Scripture
  and editorial titles — the Apple Books pairing. Every font is a Dynamic Type text style.
- **One accent.** Warm bronze `#9A6B1F` (light) / `#D8A84A` (dark), defined once in the app's
  `AccentColor` asset. 4.7:1 on white. Interactive elements only, never decoration.
- **Depth from grouped backgrounds, not shadows.** `Elevation` has three steps and the middle one is
  nearly invisible on purpose.
- **Container-concentric corners** for anything nested in a system container; fixed `Radius` only
  at the top level.
- **Motion communicates navigation.** Three animations (`quick`, `standard`, `spatial`), all
  bypassed via `Motion.resolved(_:reduceMotion:)`.
- **No gradients, no glass-on-glass, no ambient animation, no religious stock imagery.** One
  exception, decided 2026-09-28: the arrival chooser's **drifting rows** (below).

## Android counterpart

Same token names and values in `android/core/designsystem` (`Spacing`, `Radius`, `VerbumTypography`,
`Palette`, `Elevation`, `Motion`). Material 3 is the system there the way Liquid Glass is on iOS:
Material surfaces and components, platform sans (Roboto) + platform serif (Noto Serif), `sp` sizes
for font scaling. Dynamic colour is off on purpose so the bronze accent is identical everywhere.
`TokenGallery.kt` is the Compose preview twin of the SwiftUI one.

## The reading page (decided 2026-09-11, after review)

The first reader looked functional and generic. The redesign, modelled on Apple Books and printed
Bibles, is the reference for every content screen that follows:

- **Paper and ink, not white and black.** `Palette.paper` `#FAF7F1` / `Palette.ink` `#1E1A15` in
  light; `#151311` / `#EBE6DE` when the OS is dark. Hairlines `Palette.rule`. Chrome keeps system
  colours; only the page is paper.
- **Chapter opener like a book's chapter page**, centred: letter-spaced small caps book name in
  bronze (`Text.overline`), a 76pt light New York numeral, a 28pt bronze hairline.
- **Verse numerals hang in the margin** (serif, 62% of body size, tertiary ink), text flush. Poetry
  keeps the source's line breaks (Psalms, Proverbs, Job, Song, Lamentations, Ecclesiastes); prose
  never does.
- **Selection is a bronze wash** (`Palette.selectionWash`, 16% accent), numeral turns bronze,
  haptic tick, and a glass capsule at the bottom shows the citation with Copy / Clear.
- **Chrome gets out of the way.** The navigation bar hides on downward scroll, returns on upward
  scroll, at the top, or on a tap on the paper. No bottom bar.
- **Turning chapters**: swipe sideways, or the "Continue → 1 Samuel 18" foot at the end of the
  chapter; the page slides in from the side of travel. The title ("1 Samuel 17 ⌄", serif) opens
  the index.
- **The index is a bookshelf that maps the canon.** One shelf per division (Law, History,
  Poetry & Wisdom, Prophets · Gospels & Acts, Letters of Paul, General Letters, Revelation), one
  block per book with the name set upright in strong serif and its chapter count beside it
  (`Genesis 50`), blocks flowing in rows, a faint warm tint per shelf (`Palette.spineTint`), the
  book being read in bronze. Tapping a block lifts it and unfolds its chapter grid beneath the
  shelf; tapping again closes it. Rotated spine text was tried and rejected: it forces the reader
  to tilt their head, which fights ease and legibility. Nothing is pushed. You see the shape of the Bible at a glance — which is
  the product's thesis. Divisions live in `BibleBook.Division` (Models), the standard Protestant
  grouping matching the 66-book canon.
- **Reading column** caps at 680pt; margins are `Spacing.readingMargin` (24).
- **Text size** sheet: four steps on top of Dynamic Type, with a live Genesis 1:1 preview.

Android renders the same page with Material primitives; `Palette` maps paper/ink onto the scheme
roles so `MaterialTheme.colorScheme.surface` *is* paper.

## Drifting rows (decided 2026-09-28)

The arrival chooser ("How are you feeling today?") was a vertical list of nine words, which reads
as a form to complete — the opposite of what that screen is for. It is now three rows of words on
paper that bleed past the reading column and drift sideways at walking pace, alternating direction,
each one also pushable with a finger. Nothing asks to be completed; you reach in and take one.

This is the only ambient motion in the app, and it earns its place under conditions:

- **Slow.** `Motion.Drift` is in points per second (7 / 9 / 12), so a word stays readable while it
  moves and a finger always overtakes it.
- **Free.** `DriftingRow` (DesignSystem) lays the content out several times and animates one
  transform over the strip — no re-layout per frame, one implicit animation for the life of the row.
  `RowWindow`, a small `Layout`, gives the row exactly the width it is offered and pins the strip to
  its leading edge, so an oversized strip can never resize or move the row.
- **Still when it should be.** Reduce Motion stops the drift; Reduce Motion, VoiceOver and
  accessibility text sizes drop the rows entirely for `FlowLayout`, where every word is in reach at
  once. Duplicate copies are hidden from VoiceOver, so the nine words are read once.
- **A push is not a choice.** While a row is being pushed it publishes
  `\.isPushingDriftingRow`, and the words ignore the tap that ends the push.

Identity is unchanged: paper and ink, New York for the words, hairline capsules, bronze only while
a finger is on a word. The only edge treatment is a mask that fades both ends of a row
(`View.edgeFade`), so words arrive and leave instead of being cut off.

## Tokens (iOS)

| Token | File | Notes |
|---|---|---|
| `Spacing` | Tokens/Spacing.swift | 4pt scale + `screenMargin` (16) + `readingMargin` (24) |
| `Radius` | Tokens/Radius.swift | 8 / 12 / 16 / 22 |
| `Typography` | Tokens/Typography.swift | Interface, editorial, Scripture roles |
| `Palette` | Tokens/Palette.swift | Background, surfaces, foreground, accent |
| `Elevation` | Tokens/Elevation.swift | `.elevation(.card)` modifier |
| `Motion` | Tokens/Motion.swift | Durations, curves, Reduce Motion helper, `Drift` speeds |

`TokenGallery.swift` is a `#Preview` that renders the set; open its canvas to review changes.

## Rules for feature code

- No literal colours, point sizes, radii, durations or spacing in feature views (PRODUCT.md §64).
- Scripture text always uses `Typography.scripture` + `Typography.scriptureLineSpacing`.
- Entity type is never conveyed by colour alone (PRODUCT.md §8.3) — shape or label carries it.
