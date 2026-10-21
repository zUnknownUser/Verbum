-- Optional, reviewed chapter studies. Existing entity/occurrence context is preserved.
CREATE TABLE IF NOT EXISTS chapter_studies (
    book_id text NOT NULL,
    chapter integer NOT NULL CHECK (chapter > 0),
    presentations jsonb NOT NULL,
    PRIMARY KEY (book_id, chapter)
);

CREATE TABLE IF NOT EXISTS chapter_study_sources (
    book_id text NOT NULL,
    chapter integer NOT NULL,
    source_id text NOT NULL REFERENCES sources(id),
    position integer NOT NULL DEFAULT 0,
    PRIMARY KEY (book_id, chapter, source_id),
    FOREIGN KEY (book_id, chapter) REFERENCES chapter_studies(book_id, chapter)
);
