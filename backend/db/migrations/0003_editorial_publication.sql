-- Offline editorial provenance and approval audit (§32–33). No new HTTP routes.
CREATE TABLE IF NOT EXISTS source_provenance (
    reference_id text PRIMARY KEY REFERENCES sources(id),
    source_id text NOT NULL,
    license text NOT NULL,
    page text,
    section text
);

CREATE TABLE IF NOT EXISTS entity_sources (
    entity_id text NOT NULL REFERENCES entities(id) ON DELETE CASCADE,
    source_id text NOT NULL REFERENCES sources(id),
    position int NOT NULL,
    PRIMARY KEY(entity_id, source_id)
);

CREATE TABLE IF NOT EXISTS content_publications (
    bundle_hash text PRIMARY KEY,
    kind text NOT NULL CHECK (kind IN ('fixture', 'editorial')),
    review jsonb NOT NULL,
    published_at timestamptz NOT NULL DEFAULT now()
);
