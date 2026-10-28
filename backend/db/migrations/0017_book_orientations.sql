-- Optional book-level guidance, distinct from chapter commentary.
CREATE TABLE IF NOT EXISTS book_orientations (
 book_id text PRIMARY KEY,
 chapter_count integer NOT NULL CHECK (chapter_count > 0),
 presentations jsonb NOT NULL
);
CREATE TABLE IF NOT EXISTS book_orientation_sources (
 book_id text NOT NULL REFERENCES book_orientations(book_id),
 source_id text NOT NULL REFERENCES sources(id),
 position integer NOT NULL DEFAULT 0,
 PRIMARY KEY (book_id, source_id)
);
