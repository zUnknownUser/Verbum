-- Additive, source-owned metadata on the existing entity graph. No second entity store.
CREATE TABLE IF NOT EXISTS source_datasets (
    source_id text NOT NULL REFERENCES sources(id),
    repository text NOT NULL,
    revision text NOT NULL CHECK (revision ~ '^[0-9a-f]{40}$'),
    path text NOT NULL,
    sha256 text NOT NULL CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    license_url text NOT NULL,
    attribution text NOT NULL,
    modifications text NOT NULL,
    PRIMARY KEY (source_id, revision)
);
CREATE TABLE IF NOT EXISTS entity_source_records (
    id text PRIMARY KEY,
    entity_id text NOT NULL REFERENCES entities(id),
    source_id text NOT NULL REFERENCES sources(id),
    revision text NOT NULL,
    FOREIGN KEY (source_id, revision) REFERENCES source_datasets(source_id, revision),
    external_id text NOT NULL,
    source_line int NOT NULL CHECK (source_line > 0),
    identifiers jsonb NOT NULL CHECK (jsonb_typeof(identifiers) = 'object'),
    lexical jsonb,
    UNIQUE (source_id, external_id),
    UNIQUE (source_id, entity_id)
);
CREATE INDEX IF NOT EXISTS entity_source_records_entity ON entity_source_records(entity_id);
CREATE INDEX IF NOT EXISTS entity_source_records_identifiers ON entity_source_records USING gin(identifiers);
-- Localization is independent of original-language lexemes and source identity.
-- Editors can add a different attributed localization source, without altering STEP records.
CREATE TABLE IF NOT EXISTS entity_localizations (
    entity_id text NOT NULL REFERENCES entities(id),
    language text NOT NULL CHECK (language IN ('en','pt-BR')),
    source_id text NOT NULL REFERENCES sources(id),
    name text NOT NULL CHECK (length(name)>0),
    aliases text[] NOT NULL DEFAULT '{}',
    description text,
    PRIMARY KEY (entity_id, language, source_id)
);
CREATE INDEX IF NOT EXISTS entity_localizations_lookup ON entity_localizations(language,lower(name));
-- Occurrences are evidence, not curated key passages or invented passage graph nodes.
-- Keep original locator (including repeated-word suffix) and verified OSIS verse together.
CREATE TABLE IF NOT EXISTS entity_occurrences (
    record_id text NOT NULL REFERENCES entity_source_records(id) ON DELETE CASCADE,
    locator text NOT NULL,
    book_id text NOT NULL,
    chapter int NOT NULL CHECK (chapter>0),
    verse int NOT NULL CHECK (verse>0),
    PRIMARY KEY (record_id, locator)
);
CREATE INDEX IF NOT EXISTS entity_occurrences_passage ON entity_occurrences(book_id,chapter,verse);
-- Reuse this projection wherever the existing store looks up biblical entity associations.
CREATE OR REPLACE VIEW entity_passage_associations AS
 SELECT entity_id,book_id,chapter,verse_start,verse_end FROM entity_key_passages
 UNION
 SELECT r.entity_id,o.book_id,o.chapter,o.verse,o.verse
 FROM entity_occurrences o JOIN entity_source_records r ON r.id=o.record_id;
