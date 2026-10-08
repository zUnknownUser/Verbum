-- Optional bilingual editorial metadata; old clients retain the event contract.
ALTER TABLE timeline_events ADD COLUMN IF NOT EXISTS discovery jsonb;
