-- In-depth guides for existing themes. No catalogue or user-data replacement.
CREATE TABLE IF NOT EXISTS theme_studies (
 entity_id text PRIMARY KEY REFERENCES entities(id),
 presentations jsonb NOT NULL
);
CREATE TABLE IF NOT EXISTS theme_study_sources (
 entity_id text NOT NULL REFERENCES theme_studies(entity_id),
 source_id text NOT NULL REFERENCES sources(id),
 position integer NOT NULL,
 PRIMARY KEY(entity_id,source_id)
);
CREATE TABLE IF NOT EXISTS theme_study_passages (
 entity_id text NOT NULL REFERENCES theme_studies(entity_id),
 position integer NOT NULL,
 book_id text NOT NULL,
 chapter integer NOT NULL CHECK(chapter>0),
 verse_start integer NOT NULL CHECK(verse_start>0),
 verse_end integer NOT NULL CHECK(verse_end>=verse_start),
 PRIMARY KEY(entity_id,position)
);
CREATE INDEX IF NOT EXISTS theme_study_passages_chapter ON theme_study_passages(book_id,chapter);
CREATE OR REPLACE VIEW entity_passage_associations AS
 SELECT entity_id,book_id,chapter,verse_start,verse_end FROM entity_key_passages
 UNION
 SELECT r.entity_id,o.book_id,o.chapter,o.verse,o.verse
 FROM entity_occurrences o JOIN entity_source_records r ON r.id=o.record_id
 UNION
 SELECT entity_id,book_id,chapter,verse_start,verse_end FROM entity_profile_passages
 UNION
 SELECT entity_id,book_id,chapter,verse_start,verse_end FROM theme_study_passages;
