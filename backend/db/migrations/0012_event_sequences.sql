-- Editorial reading order, independent from historical date precision.
CREATE TABLE IF NOT EXISTS event_sequences (
 era_id text PRIMARY KEY,
 presentations jsonb NOT NULL
);
CREATE TABLE IF NOT EXISTS event_sequence_items (
 entity_id text PRIMARY KEY REFERENCES entities(id),
 era_id text NOT NULL REFERENCES event_sequences(era_id),
 position int NOT NULL CHECK(position > 0),
 timeline_id text NOT NULL UNIQUE REFERENCES timeline_events(id),
 UNIQUE(era_id, position)
);
