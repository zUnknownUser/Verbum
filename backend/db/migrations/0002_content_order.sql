-- Preserve editorial order across JSON fixtures and relational storage.
ALTER TABLE sources ADD COLUMN IF NOT EXISTS position int NOT NULL DEFAULT 0;
ALTER TABLE entities ADD COLUMN IF NOT EXISTS position int NOT NULL DEFAULT 0;
ALTER TABLE relationships ADD COLUMN IF NOT EXISTS position int NOT NULL DEFAULT 0;
ALTER TABLE entity_aliases ADD COLUMN IF NOT EXISTS position int NOT NULL DEFAULT 0;
ALTER TABLE entity_detail_sources ADD COLUMN IF NOT EXISTS position int NOT NULL DEFAULT 0;
ALTER TABLE relationship_sources ADD COLUMN IF NOT EXISTS position int NOT NULL DEFAULT 0;
ALTER TABLE timeline_event_sources ADD COLUMN IF NOT EXISTS position int NOT NULL DEFAULT 0;
