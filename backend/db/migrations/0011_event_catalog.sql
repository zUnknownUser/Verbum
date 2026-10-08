-- Bind timeline entries to canonical event studies; keep these identities stable.
-- Separate from participant links so later timeline publications cannot erase them.
CREATE TABLE IF NOT EXISTS timeline_event_catalog (
    event_id text PRIMARY KEY REFERENCES timeline_events(id),
    entity_id text NOT NULL UNIQUE REFERENCES entities(id)
);

-- Preserve era classification independently of the current alphabetical UI.
CREATE TABLE IF NOT EXISTS event_eras (
    entity_id text PRIMARY KEY REFERENCES entities(id),
    era_id text NOT NULL
);
CREATE INDEX IF NOT EXISTS event_eras_era_idx ON event_eras(era_id,entity_id);
