# Events by period — 2026-10-11

Events now opens with ten period cards on iOS and Android, using the same bilingual
IDs, titles and contextual descriptions as the Timeline catalogue. All events remains
available. Choosing a period scopes alphabetical browsing, search and 30-row paging
to that period. Returning from a study retains selection; Browse periods clears the
filters and returns to discovery. Themes uses the same renamed discovery state without
changing its category behaviour.

The existing `category` query parameter now also accepts the ten event period IDs.
Postgres filters through `event_eras` before searching, generating the letter index,
and paginating. Type-specific validation rejects theme categories for events and vice
versa. Existing clients and requests without a category keep their prior behaviour.
Fixture catalogues classify the four original event IDs for offline development.
No migration, content republication, audio generation or duplicate events are needed.
Deploy the API before distributing these app builds.

This step covers period navigation. Events within each period remain alphabetical;
narrative sequences and individual timeline positions for the 51 additional events
remain separate editorial work. The UI explicitly describes the alphabetical order.

Validation:
- Backend `go test -p 1 ./...` against isolated PostgreSQL schemas and `go vet ./...` passed.
  Serial package execution avoids concurrent extension creation in a fresh test DB.
  Tests cover period validation, localization, alias search, letter filtering,
  pagination before/after period selection, and unclassified entries in All events.
- iOS app build and EntityListFeatureTests passed (7 tests, including theme/event
  parameterized category selection). Portuguese discovery screen visually checked.
- Android app build and 214 tests passed (121 feature, 93 client). English discovery
  screen visually checked. Event-specific tests cover selection, paging, search,
  return, reset and stale responses.
- Existing user changes in the iOS string catalogue are preserved outside the commit.

Production verification is recorded after deployment.
