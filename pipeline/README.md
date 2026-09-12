# pipeline/ — content pipeline (Python) — not started

What fills the graph (docs/PRODUCT.md §32). Runs offline, never as part of the API; its only
output is rows in the Postgres the API reads. Stages:

```
import sources → normalize → extract entities & relationships (AI proposes)
              → HUMAN REVIEW (nothing is published without it) → embeddings → publish
```

Planned shape (when started):
- `pyproject.toml` managed with uv; Python ≥ 3.12.
- `verbum_pipeline/` with one module per stage; each stage reads and writes plain files
  (JSON/CSV) so a human can inspect and edit between stages.
- `review/` — the editorial queue: proposed entities/edges with their evidence, approved by hand.
- `publish` — writes approved rows to Postgres (the schema in `backend/db/migrations/`), citing
  sources (§33). The very first publish is `backend/db/seed/fixtures.json` as-is.
- Later: embeddings of passages and graph content into pgvector for Task 12 (Ask Scripture, §29).

First real content target (§68): ~50 people, ~30 places, ~20 themes, ~40 events, every
relationship sourced.
