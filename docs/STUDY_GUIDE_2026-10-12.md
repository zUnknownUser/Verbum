# Bounded study guide — 2026-10-12

The 21/10 chapter-content increment adds 21 bilingual Exodus-sequence studies to
this existing guide; see [chapter studies](CHAPTER_STUDIES_2026-10-21.md). The
implementation and publication status below describe the original 12/10 delivery.

## Evidence and scope

The user requested improvements inspired by Olive Tree, Logos and YouVersion,
using REA where available, preserving Verbum's identity and finite content scope.

Observed public product behavior (official documentation, consulted 2026-10-12):

- [Olive Tree Resource Guide](https://www.olivetree.com/blog/resource-guide/):
  resources organized by type around the passage, with short reference previews.
- [Logos mobile guides](https://support.logos.com/hc/en-us/articles/360017893592-Using-Guides):
  passage and word guides offer distinct ways to deepen a study.
- [YouVersion offline plans](https://help.youversion.com/l/en/article/ihb45ly7lt-access-plans-offline-android):
  plans can be downloaded; Scripture requires its Bible version to be downloaded.

These sources establish documented product behavior, not competitors' internal
architecture or a hands-on audit of their current app binaries. REA's 6.4.0 CLI
reported aligned Codex registration, but this session has no connected REA tools.
No competitor APK/IPA was supplied or available as a selected local target. No
competitor binary was decompiled and no REA Evidence IDs were produced. Registration
was not rewritten, engines were not installed, and no REA runtime was added to Verbum.
The implementation below comes from the public behavior and inspection of Verbum's
own complete source, using the existing Swift/TCA and Kotlin/UDF architecture.

Repository observations: the existing study sheet already contains annotations,
comparison, chapter context, references and Ask. Reader history already restores
position after explicit reference navigation. However, context mixed categories
and silently displayed only the first 24 entities; reference taps immediately
left the study. Journey already has continuation/history, and Exodus already has
a bounded 16-event sequence. A new plans engine or expanded catalogue would exceed
this release's scope. No offline plan downloads or word-study catalogue are added.

## Implemented on both platforms

1. A discreet **Study this chapter** action in the chapter header opens the existing
   sheet in chapter scope. Only Context and References appear in that mode. Verse
   actions (notes, highlights, comparison and Ask) remain in the verse study sheet.
2. Context resources are grouped by entity type, with counts and batches of six.
   Only available categories appear. IDs are deduplicated; the former silent
   24-entity cutoff is removed. References also start with six, followed by an
   explicit Show more action. Ordering and source attribution come from the
   existing context/detail clients.
3. Tapping a reference previews its text inside the study sheet, with translation,
   loading/error/retry, Back to study, and an explicit Open in reader action.
   Merely previewing does not change last-read, reading history, annotation state,
   or the reader's scroll position. Opening the reader still saves an unfinished
   note first and uses the existing return-to-reading history. Closing/replacing
   previews suppresses obsolete responses; entity failures now have a retry path.

Existing colors, type, spacing, native sheets and navigation remain the basis of
the UI. All new copy supports English and Portuguese. iOS uses the existing
BookLanguage mechanism in a dedicated StudyGuideCopy to preserve the user's
unrelated edits to Localizable.xcstrings. Android uses localized resources.

No backend migration, production data publication, new catalogue, AI generation,
paid audio or new subscription dependency is involved. Preview uses BibleClient
and its existing cache/offline policy; uncached content can still require network.
API context coverage and its reference-selection bounds remain unchanged.

## Verification

- iOS: 20 selected tests passed across VerseStudyFeatureTests (9 new),
  ChapterReaderFeatureTests and ProfileFeatureTests. Final simulator app build passed.
- Android: all 135 feature unit tests passed (13 new); final app debug build passed
  using the production API URL.
- iOS rendered the Portuguese chapter guide and English passage preview against
  existing live clients. A temporary launch harness was restored byte-for-byte;
  the final normal app was rebuilt and installed.
- Android runtime EN/PT-BR: guide opens fully, type selection works, reference
  previews show translation, returning preserves category, explicit navigation
  changes the chapter and its existing return action restores the prior chapter.
- Tests cover resource deduplication and access beyond 24 items, six-item batches,
  chapter scope, unchanged reader position/history, preview failure/retry and stale
  responses, and saving unfinished notes before explicit navigation.
- User's pre-existing Localizable.xcstrings edits were preserved and excluded from
  this feature commit. No backend/data changes were needed.

Local inspection artifacts (not required at runtime):
`/tmp/verbum-study-ios-guide-pt.png`, `/tmp/verbum-study-ios-preview-en.png`,
`/tmp/verbum-study-android-guide-pt.png`, `/tmp/verbum-study-android-preview-en.png`.
The iOS guide screenshot predates a copy correction to chapter-level wording and
larger tap targets; final code/build includes those refinements.
