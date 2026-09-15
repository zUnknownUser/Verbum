-- Presentation overlays only: canonical STEP records and Scripture remain immutable.
ALTER TABLE entity_localizations ADD COLUMN IF NOT EXISTS fields jsonb NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE entity_localizations ADD COLUMN IF NOT EXISTS input_hash text;
CREATE TABLE IF NOT EXISTS timeline_localizations (
 event_id text NOT NULL REFERENCES timeline_events(id),
 language text NOT NULL CHECK(language IN ('en','pt-BR')),
 source_id text NOT NULL REFERENCES sources(id),
 fields jsonb NOT NULL,
 input_hash text NOT NULL,
 PRIMARY KEY(event_id,language,source_id)
);
CREATE TABLE IF NOT EXISTS source_localizations (
 reference_id text NOT NULL REFERENCES sources(id),
 language text NOT NULL CHECK(language IN ('en','pt-BR')),
 source_id text NOT NULL REFERENCES sources(id),
 fields jsonb NOT NULL,
 input_hash text NOT NULL,
 PRIMARY KEY(reference_id,language,source_id)
);
