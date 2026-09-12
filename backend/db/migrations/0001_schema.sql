-- Verbum content schema (docs/PRODUCT.md §25, §26, §33). Read by the API,
-- written only by the editorial pipeline (§32). Apply with any migration tool
-- (e.g. `migrate -path db/migrations -database "$VERBUM_DATABASE_URL" up`) or
-- plain psql; it is idempotent.

CREATE TABLE IF NOT EXISTS sources (
    id        text PRIMARY KEY,
    citation  text NOT NULL,
    url       text
);

CREATE TABLE IF NOT EXISTS entities (
    id       text PRIMARY KEY,
    type     text NOT NULL CHECK (type IN ('person','place','event','theme','passage','book','prophecy','originalTerm','historicalPeriod')),
    name     text NOT NULL,
    summary  text
);
CREATE INDEX IF NOT EXISTS entities_type_name ON entities (type, name);

CREATE TABLE IF NOT EXISTS entity_aliases (
    entity_id  text NOT NULL REFERENCES entities (id) ON DELETE CASCADE,
    alias      text NOT NULL,
    PRIMARY KEY (entity_id, alias)
);

-- §9 facts. One row per entity that has curated facts; absent = "no facts yet".
CREATE TABLE IF NOT EXISTS entity_details (
    entity_id          text PRIMARY KEY REFERENCES entities (id) ON DELETE CASCADE,
    approximate_dates  text,          -- always hedged prose: "c. 1010–970 BC (commonly dated)"
    role               text,
    modern_geography   text
);

CREATE TABLE IF NOT EXISTS entity_key_passages (
    entity_id  text NOT NULL REFERENCES entities (id) ON DELETE CASCADE,
    position   int  NOT NULL,          -- reading order
    book_id    text NOT NULL,          -- OSIS id
    chapter    int  NOT NULL,
    verse_start int,
    verse_end   int,
    PRIMARY KEY (entity_id, position)
);

CREATE TABLE IF NOT EXISTS entity_detail_sources (
    entity_id  text NOT NULL REFERENCES entities (id) ON DELETE CASCADE,
    source_id  text NOT NULL REFERENCES sources (id),
    PRIMARY KEY (entity_id, source_id)
);

-- §26. Directed; the API answers undirected neighbourhoods by querying both columns.
CREATE TABLE IF NOT EXISTS relationships (
    id                text PRIMARY KEY,
    source_entity_id  text NOT NULL REFERENCES entities (id) ON DELETE CASCADE,
    target_entity_id  text NOT NULL REFERENCES entities (id) ON DELETE CASCADE,
    relationship_type text NOT NULL,
    confidence        double precision CHECK (confidence BETWEEN 0 AND 1),
    metadata          jsonb
);
CREATE INDEX IF NOT EXISTS relationships_source ON relationships (source_entity_id);
CREATE INDEX IF NOT EXISTS relationships_target ON relationships (target_entity_id);
CREATE INDEX IF NOT EXISTS relationships_source_type ON relationships (source_entity_id, relationship_type);
CREATE INDEX IF NOT EXISTS relationships_target_type ON relationships (target_entity_id, relationship_type);

-- §33: every relationship shown to users carries at least one source.
CREATE TABLE IF NOT EXISTS relationship_sources (
    relationship_id  text NOT NULL REFERENCES relationships (id) ON DELETE CASCADE,
    source_id        text NOT NULL REFERENCES sources (id),
    PRIMARY KEY (relationship_id, source_id)
);

-- §22.5. Years are astronomical integers: negative = BC. NULL = unknown.
CREATE TABLE IF NOT EXISTS timeline_events (
    id              text PRIMARY KEY,
    title           text NOT NULL,
    start_year      int,
    end_year        int,
    date_precision  text NOT NULL CHECK (date_precision IN ('exact','approximate','debated','unknown')),
    summary         text,
    position        int NOT NULL       -- chronological order as curated (ties resolved editorially)
);

CREATE TABLE IF NOT EXISTS timeline_event_entities (
    event_id   text NOT NULL REFERENCES timeline_events (id) ON DELETE CASCADE,
    entity_id  text NOT NULL REFERENCES entities (id) ON DELETE CASCADE,
    position   int  NOT NULL,
    PRIMARY KEY (event_id, entity_id)
);

CREATE TABLE IF NOT EXISTS timeline_event_sources (
    event_id   text NOT NULL REFERENCES timeline_events (id) ON DELETE CASCADE,
    source_id  text NOT NULL REFERENCES sources (id),
    PRIMARY KEY (event_id, source_id)
);

-- The verse-of-the-day pool, in order (the pick algorithm walks a seeded permutation of it).
CREATE TABLE IF NOT EXISTS daily_verse_pool (
    position     int  PRIMARY KEY,
    book_id      text NOT NULL,
    chapter      int  NOT NULL,
    verse_start  int  NOT NULL,
    verse_end    int  NOT NULL
);
