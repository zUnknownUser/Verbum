# Bounded Exodus study sequence — 2026-10-12

## Scope and stopping point

This release completes one usable study path, not an exhaustive biblical catalogue.
The Events catalogue remains at 137 canonical records. Only Exodus and wilderness
gets a 16-event narrative sequence. Seven existing events gain individual Timeline
entries, taking the timeline from 91 to 98 entries, with 93 canonical event bindings.
No additional event entities, paid audio or other module expansion is included.

Done means a user can find the period, read its events in a documented order, open
biblical references, move to the previous/next event and reach an explicit endpoint,
in Portuguese and English on both platforms. Other periods' sequences, the remaining
44 timeline links and broader catalogue expansion stay in the backlog. They are not
automatic follow-up work or prerequisites for completing this feature or releasing
a useful study app. Prioritize defects, comprehension and actual usage before adding
more content; any future expansion needs a named, finite batch and an acceptance test.

## Content and traceability

`pipeline/verbum_pipeline/sequences.py` builds `sources/sequences/exodus.json` from
the existing Events and Timeline catalogues. Chapter placement determines the reading
order: Exodus 2, 3, 7–10, 12, 14–20, 32, 40; Numbers 13–14, 21, 22; Deuteronomy 34.
These are selected episodes, not every event in those books or exact historical dates.
Jethro follows Exodus 18; the qualification invites comparison with Deuteronomy 1:9–18.
John 3 remains a later cross-reference for the bronze serpent, not a sequence step.

Existing prose, sources, chapter references, dating and canonical identities are
preserved. The seven added timeline rows reuse the bilingual event studies, with
unknown absolute dates. Bibliographic checks include [Exodus 18](https://www.biblegateway.com/passage/?search=Exodus+18&version=WEB),
[Numbers 21](https://www.biblegateway.com/passage/?search=Numbers+21&version=WEB) and
[Numbers 22](https://www.biblegateway.com/passage/?search=Numbers+22&version=WEB).
All sequence chapter references were also checked against the local canonical inventory.
The hash-bound review identifies delegated AI editorial review, not an independent
human/theological review.

## Implementation

Bundle v7 adds reviewed sequences alongside the bilingual timeline. Old bundle hashes
are preserved. Publication checks complete era coverage, canonical binding consistency
and the existing timeline text/dating snapshot, then updates atomically. Repeated
publication is idempotent. Migration 0012 stores sequences and ordered canonical links.

The API's optional `order=narrative` scopes ordering before pagination and searching;
legacy/default requests remain alphabetical. Exodus defaults to narrative in the new
apps, with an alphabetical toggle. Narrative order hides the alphabetical index.
Entity detail optionally includes localized sequence context, position/total and
adjacent canonical entities. Boundary neighbours are null, with an endpoint message.
Adjacent links reuse the established entity-detail navigation. Old responses without
sequence metadata still decode. The normal catalogue/search behaviour is unchanged
for all other periods.

## Verification

Backend tests pass with PostgreSQL, including reverse-alphabetical fixtures, page
boundaries, bilingual neighbour labels and first/last boundaries; go vet passes.
Pipeline: 66 passed, 2 optional skipped. Added publication checks were rerun after the
snapshot guard, including rollback, idempotency and preservation of all 137 entities.
Both app builds passed. Android: 216 tests passed (122 feature, 94 client).
iOS: 13 feature/model tests and 1 entity-detail contract test passed. The legacy
contract test now explicitly requests English, matching its English fixture and the
API language parameter independently of simulator language. Existing iOS localization
edits are preserved outside this commit.

Local end-to-end checks confirmed all 16 ordered IDs, previous/next boundaries and
readings in EN/PT-BR, with 98 timeline entries and 137 canonical events. iOS's Portuguese
study page was visually checked. Android checks exercised narrative list → first study
→ next → previous, switching to alphabetical order, and the final event's endpoint
message without a Next link. Normal production builds were restored after preview.

Production release:
- Code pushed to main: `09caf47`.
- Railway deployment `cb8500e4-0e7e-4d37-929b-37e01deccb7e`: SUCCESS.
- Published bundle hash: `26fc1ae359c0e607be0a204d20f186bbbdafd07ed8569ab075e53313a42fdf52`.
- Remote verification passed in EN and PT-BR: exact 16-event narrative order across
  7-item test pages, all localized titles, previous/next boundaries, chapter readings,
  consistent Exodus timeline order, 98 timeline rows and 137 unique event entities.
- Owner-only prepublication backup:
  `pipeline/work/backups/exodus-sequence-2026-10-12-before.json` (gitignored).
- Git authentication was restored by Lucas before push; production was updated only
  after the tested commit reached main. Temporary local QA resources were stopped.

This feature is complete. No further catalogue growth or period sequences are started
as part of this release.
