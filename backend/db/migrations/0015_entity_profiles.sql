-- A bounded editorial overlay on existing identities; original records remain intact.
CREATE TABLE IF NOT EXISTS entity_profiles (
 entity_id text PRIMARY KEY REFERENCES entities(id),
 presentations jsonb NOT NULL
);
CREATE TABLE IF NOT EXISTS entity_profile_sources (
 entity_id text NOT NULL REFERENCES entity_profiles(entity_id),
 source_id text NOT NULL REFERENCES sources(id),
 position integer NOT NULL,
 PRIMARY KEY(entity_id,source_id)
);
CREATE TABLE IF NOT EXISTS entity_profile_passages (
 entity_id text NOT NULL REFERENCES entity_profiles(entity_id),
 position integer NOT NULL,
 book_id text NOT NULL,
 chapter integer NOT NULL CHECK(chapter>0),
 verse_start integer,
 verse_end integer,
 PRIMARY KEY(entity_id,position),
 CHECK ((verse_start IS NULL AND verse_end IS NULL) OR
        (verse_start IS NOT NULL AND verse_end IS NOT NULL AND verse_start>0 AND verse_end>=verse_start))
);
CREATE INDEX IF NOT EXISTS entity_profile_passages_chapter ON entity_profile_passages(book_id,chapter);
CREATE OR REPLACE VIEW entity_passage_associations AS
 SELECT entity_id,book_id,chapter,verse_start,verse_end FROM entity_key_passages
 UNION
 SELECT r.entity_id,o.book_id,o.chapter,o.verse,o.verse
 FROM entity_occurrences o JOIN entity_source_records r ON r.id=o.record_id
 UNION
 SELECT entity_id,book_id,chapter,verse_start,verse_end FROM entity_profile_passages;
