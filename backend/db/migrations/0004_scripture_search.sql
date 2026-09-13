-- Scripture text for retrieval only (§27-29 hybrid search), not for the reading feature: apps
-- keep reading from bible.helloao.org (backend/README.md). This is a separate, additive
-- concern from the entity/relationship graph in 0001-0003: verbatim public-domain translation
-- text plus its embedding, loaded by the pipeline (pipeline/verbum_pipeline/scripture.py), never
-- by the API. book_id is the same OSIS id used by entity_key_passages.
--
-- Requires an image with the pgvector extension built in (pgvector/pgvector:pg17 in
-- docker-compose.yml; a managed Postgres host must offer the same extension). Installed
-- into `public` and referenced schema-qualified below because extensions are database-wide:
-- the integration tests create one isolated schema per run (search_path set to just that
-- schema), and the first test to install the extension must not strand it somewhere later
-- schemas cannot see.
CREATE EXTENSION IF NOT EXISTS vector SCHEMA public;

-- text-embedding-3-large, requested at 1536 dimensions (OpenAI's `dimensions` parameter, a
-- Matryoshka truncation of the native 3072): pgvector's hnsw/ivfflat indexes reject anything
-- over 2000 dimensions, and OpenAI's own evaluations show 3-large truncated to 1536 still beats
-- 3-small at the same width. A different model or width requires a new migration (the column
-- width is part of the schema).
CREATE TABLE IF NOT EXISTS scripture_verses (
    translation text    NOT NULL,       -- e.g. 'WEB'; public-domain-only until §34 clears another
    book_id     text    NOT NULL,       -- OSIS id
    chapter     int     NOT NULL,
    verse       int     NOT NULL,
    text        text    NOT NULL,
    embedding   public.vector(1536),    -- NULL until the pipeline's embedding stage runs
    PRIMARY KEY (translation, book_id, chapter, verse)
);

CREATE INDEX IF NOT EXISTS scripture_verses_fts_idx
    ON scripture_verses USING GIN (to_tsvector('english', text));

-- HNSW over cosine distance; the query side casts its embedding to vector and orders by <=>.
CREATE INDEX IF NOT EXISTS scripture_verses_embedding_idx
    ON scripture_verses USING hnsw (embedding public.vector_cosine_ops);
