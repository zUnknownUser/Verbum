# api/ — the contract

`openapi.yaml` is the wire shape of the backend the apps expect (docs/PRODUCT.md §45), written from
the client interfaces that already exist in `VerbumKit/Sources/Clients` and `android/core/clients`.
The backend (Go, `backend/`) implements it; the apps' live clients decode it. For what each route is
*for*, in plain language, see [docs/API_ROUTES.md](../docs/API_ROUTES.md).

`examples/` are responses generated from the fixtures the apps ship today (`EntityFixtureData`,
`TimelineFixtureData`, `DailyVerses`), so they are also what the fixture clients answer. They are the
inputs of the **contract tests** on both platforms: a change to a JSON here must be reflected in the
apps' decoders, or their tests fail — before a user does.

| File | Route |
|---|---|
| `entities/david.json` | `GET /v1/entities/fixture.person.david` |
| `entities/people.json` | `GET /v1/entities?type=person` |
| `graph/david.json` | `GET /v1/entities/fixture.person.david/graph?limit=24` |
| `context/1Sam.17.json` | `GET /v1/passages/1Sam.17/context` |
| `timeline/all.json` | `GET /v1/timeline` |
| `search/david.json` | `GET /v1/search?q=David` |
| `daily-verse/week.json` | `GET /v1/daily-verse?from=2026-09-13&days=7` |

Regenerate the examples with `scripts/gen-examples.py` after changing a fixture. Lint the spec with
any OpenAPI 3.1 validator (e.g. `npx @redocly/cli lint api/openapi.yaml`).

Things the contract decides on purpose:
- Scripture text is not served; references are structured so the apps render them in the reader's
  translation. `PassageReference` on the wire is `{bookId, chapter, verseStart?, verseEnd?}`.
- Missing context is `404 content_unavailable`, never a generated paragraph (§3.5).
- Every relationship, detail and dating claim carries `sourceReferenceIds` (§33).
- `/v1/daily-verse` is not in §45; it exists so the apps can plan a week of notifications in one call.
