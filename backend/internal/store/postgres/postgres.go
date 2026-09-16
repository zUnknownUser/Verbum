// Package postgres implements the read-only content store over PostgreSQL.
package postgres

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"strconv"
	"strings"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"verbum/backend/internal/domain"
	"verbum/backend/internal/store"
)

type Store struct{ pool *pgxpool.Pool }

var _ store.Store = (*Store)(nil)

func Open(ctx context.Context, url string) (*Store, error) {
	pool, err := pgxpool.New(ctx, url)
	if err != nil {
		return nil, err
	}
	if err = pool.Ping(ctx); err != nil {
		pool.Close()
		return nil, err
	}
	return &Store{pool: pool}, nil
}

func (s *Store) Close() { s.pool.Close() }

// Each response is read in one statement, so concurrent publication cannot
// mix two versions of an entity, graph, or its provenance.
func readJSON[T any](ctx context.Context, s *Store, query string, args ...any) (T, error) {
	var result T
	var raw []byte
	err := s.pool.QueryRow(ctx, query, args...).Scan(&raw)
	if errors.Is(err, pgx.ErrNoRows) {
		return result, store.ErrNotFound
	}
	if err != nil {
		return result, err
	}
	err = json.Unmarshal(raw, &result)
	return result, err
}

const entityJSON = `jsonb_build_object('id',e.id,'type',e.type,'name',e.name,'summary',e.summary)`

// Parameter is supplied only by query code. Presentation never falls back to
// English for Portuguese requests. Original Hebrew/Greek headwords stay intact.
func localizedEntityJSON(parameter string) string {
	choice := `(SELECT jsonb_build_object('name',l.name,'summary',CASE WHEN l.language='en' THEN COALESCE(l.description,e.summary) ELSE l.description END,'nameLanguage',l.language)
 FROM entity_localizations l WHERE l.entity_id=e.id AND l.language=` + parameter + `
 ORDER BY (l.source_id LIKE 'step.%'),l.source_id LIMIT 1)`
	fallback := `CASE WHEN ` + parameter + `='en' THEN '{}'::jsonb ELSE jsonb_build_object(
 'name',CASE WHEN e.type='originalTerm' THEN e.name ELSE 'Nome em tradução' END,
 'summary',NULL,'nameLanguage','pt-BR') END`
	return `(` + entityJSON + ` || COALESCE(` + choice + `,` + fallback + `))`
}

func localizedSourceJSON(parameter string) string {
	return `jsonb_build_object('id',s.id,'citation',COALESCE((SELECT l.fields->>'citation' FROM source_localizations l WHERE l.reference_id=s.id AND l.language=` + parameter + ` ORDER BY l.source_id LIMIT 1),s.citation),'url',s.url)`
}

const passageJSON = `jsonb_strip_nulls(jsonb_build_object('bookId',p.book_id,'chapter',p.chapter,'verseStart',p.verse_start,'verseEnd',p.verse_end))`
const relationshipJSON = `jsonb_build_object('id',r.id,'sourceId',r.source_entity_id,'targetId',r.target_entity_id,
 'type',r.relationship_type,'confidence',r.confidence,'sourceReferenceIds',
 COALESCE((SELECT jsonb_agg(rs.source_id ORDER BY rs.position,rs.source_id) FROM relationship_sources rs WHERE rs.relationship_id=r.id),'[]'::jsonb))`

func (s *Store) Entity(ctx context.Context, id string) (domain.Entity, error) {
	return readJSON[domain.Entity](ctx, s, `SELECT `+localizedEntityJSON("$2")+` FROM entities e WHERE e.id=$1`, id, store.Language(ctx))
}

func (s *Store) Entities(ctx context.Context, kind domain.EntityType) ([]domain.Entity, error) {
	return readJSON[[]domain.Entity](ctx, s, `SELECT COALESCE(jsonb_agg(value ORDER BY value->>'name' COLLATE "C",position,id),'[]'::jsonb)
 FROM (SELECT `+localizedEntityJSON("$2")+` value,e.position,e.id FROM entities e
 WHERE e.type=$1 AND e.type<>'passage') selected`, kind, store.Language(ctx))
}

// Structured search never conflates an eStrong with its disambiguated identity. Matching
// may return several entities; each keeps its own ID and attributed source record.
const structuredMatch = `(
 EXISTS (SELECT 1 FROM entity_localizations l WHERE l.entity_id=e.id AND
 (strpos(lower(l.name),$1)>0 OR EXISTS (SELECT 1 FROM unnest(l.aliases) a WHERE strpos(lower(a),$1)>0)))
 OR EXISTS (SELECT 1 FROM entity_source_records sr WHERE sr.entity_id=e.id AND
 EXISTS (SELECT 1 FROM jsonb_each(sr.identifiers) kv, jsonb_array_elements_text(kv.value) v
 WHERE lower(v)=$1)))`

func (s *Store) Search(ctx context.Context, query string) ([]domain.Entity, error) {
	q := strings.ToLower(strings.TrimSpace(query))
	return readJSON[[]domain.Entity](ctx, s, `SELECT COALESCE(jsonb_agg(value ORDER BY position,id),'[]'::jsonb)
 FROM (SELECT `+localizedEntityJSON("$2")+` value,e.position,e.id FROM entities e
 WHERE e.type<>'passage' AND $1<>'' AND (strpos(lower(e.name),$1)>0 OR `+structuredMatch+`)
 ORDER BY e.position,e.id LIMIT 100) selected`, q, store.Language(ctx))
}

func vectorLiteral(v []float32) string {
	parts := make([]string, len(v))
	for i, f := range v {
		parts[i] = strconv.FormatFloat(float64(f), 'g', -1, 32)
	}
	return "[" + strings.Join(parts, ",") + "]"
}

// SearchPassages combines lexical (full-text), semantic (pgvector cosine) and exact
// structured-name/identifier retrieval. Each channel proposes bounded candidates; the
// union is ranked by summed scores. Structured hits must have stored Scripture text.
// This is a first, documented-as-tunable heuristic (§27-28), not a calibrated relevance model —
// citation-grade validation is Task 12's job (§31), not this endpoint's.
func (s *Store) SearchPassages(ctx context.Context, queryText string, queryEmbedding []float32, limit int) ([]domain.PassageReference, error) {
	text := strings.TrimSpace(queryText)
	var vector *string
	if len(queryEmbedding) > 0 {
		v := vectorLiteral(queryEmbedding)
		vector = &v
	}
	if text == "" && vector == nil {
		return []domain.PassageReference{}, nil
	}
	return readJSON[[]domain.PassageReference](ctx, s, `WITH structured_entities AS (
 SELECT e.id FROM entities e WHERE $1<>'' AND (
 EXISTS (SELECT 1 FROM entity_localizations l WHERE l.entity_id=e.id AND
 (lower(l.name)=lower($1) OR EXISTS (SELECT 1 FROM unnest(l.aliases) a WHERE lower(a)=lower($1))))
 OR EXISTS (SELECT 1 FROM entity_source_records r WHERE r.entity_id=e.id AND
 EXISTS (SELECT 1 FROM jsonb_each(r.identifiers) kv,jsonb_array_elements_text(kv.value) v WHERE lower(v)=lower($1))))
 ORDER BY e.id LIMIT 64
 ), structured AS (
 SELECT DISTINCT p.book_id,p.chapter,p.verse_start verse,1.0::real score
 FROM entity_passage_associations p JOIN structured_entities e ON e.id=p.entity_id
 WHERE p.verse_start IS NOT NULL AND EXISTS (SELECT 1 FROM scripture_verses sv
 WHERE sv.book_id=p.book_id AND sv.chapter=p.chapter AND sv.verse=p.verse_start)
 ORDER BY p.book_id,p.chapter,p.verse_start LIMIT $3
 ), lexical AS (
 SELECT book_id,chapter,verse,ts_rank_cd(to_tsvector('english',text),plainto_tsquery('english',$1)) score
 FROM scripture_verses WHERE $1<>'' AND to_tsvector('english',text) @@ plainto_tsquery('english',$1)
 ORDER BY score DESC LIMIT $3
 ), semantic AS (
 SELECT book_id,chapter,verse,1-(embedding OPERATOR(public.<=>) $2::public.vector) score
 FROM scripture_verses WHERE $2::public.vector IS NOT NULL
 ORDER BY embedding OPERATOR(public.<=>) $2::public.vector LIMIT $3
 ), combined AS (
 SELECT book_id,chapter,verse,SUM(score) score FROM (
 SELECT * FROM lexical UNION ALL SELECT * FROM semantic UNION ALL SELECT * FROM structured
 ) hits GROUP BY book_id,chapter,verse
 ), ranked AS (
 SELECT * FROM combined ORDER BY score DESC,book_id,chapter,verse LIMIT $3
 )
 SELECT COALESCE(jsonb_agg(jsonb_build_object('bookId',book_id,'chapter',chapter,'verseStart',verse,'verseEnd',verse) ORDER BY score DESC),'[]'::jsonb)
 FROM ranked`, text, vector, limit)
}

// PassageText batches a lookup for exactly the references given (via unnest, not N round
// trips). References with no stored row are simply absent from the result.
func (s *Store) PassageText(ctx context.Context, translation string, refs []domain.PassageReference) (map[string]string, error) {
	result := map[string]string{}
	if len(refs) == 0 {
		return result, nil
	}
	books := make([]string, len(refs))
	chapters := make([]int32, len(refs))
	verses := make([]int32, len(refs))
	for i, r := range refs {
		books[i] = r.BookID
		chapters[i] = int32(r.Chapter)
		if r.VerseStart != nil {
			verses[i] = int32(*r.VerseStart)
		}
	}
	rows, err := s.pool.Query(ctx, `SELECT book_id,chapter,verse,text FROM scripture_verses
 WHERE translation=$1 AND (book_id,chapter,verse) IN (SELECT * FROM unnest($2::text[],$3::int[],$4::int[]))`,
		translation, books, chapters, verses)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	for rows.Next() {
		var bookID, text string
		var chapter, verse int
		if err := rows.Scan(&bookID, &chapter, &verse, &text); err != nil {
			return nil, err
		}
		v := verse
		result[domain.PassageReference{BookID: bookID, Chapter: chapter, VerseStart: &v}.Key()] = text
	}
	return result, rows.Err()
}

// EntitiesForPassages returns IDs whose curated key passages or sourced occurrences
// cover any of the given verses — used only by Ask (§29) to link cited Scripture back to the
// entity graph. A chapter-only keyPassage (verse_start IS NULL) covers every verse in that
// chapter; entities are deduplicated but not ordered (callers sort if they need determinism).
func (s *Store) EntitiesForPassages(ctx context.Context, refs []domain.PassageReference) ([]string, error) {
	if len(refs) == 0 {
		return []string{}, nil
	}
	books := make([]string, len(refs))
	chapters := make([]int32, len(refs))
	verses := make([]int32, len(refs))
	for i, r := range refs {
		books[i] = r.BookID
		chapters[i] = int32(r.Chapter)
		if r.VerseStart != nil {
			verses[i] = int32(*r.VerseStart)
		}
	}
	rows, err := s.pool.Query(ctx, `SELECT DISTINCT p.entity_id FROM entity_passage_associations p
 JOIN unnest($1::text[],$2::int[],$3::int[]) AS q(book_id,chapter,verse) ON p.book_id=q.book_id AND p.chapter=q.chapter
 WHERE p.verse_start IS NULL OR q.verse BETWEEN p.verse_start AND COALESCE(p.verse_end,p.verse_start)`,
		books, chapters, verses)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	result := []string{}
	for rows.Next() {
		var id string
		if err := rows.Scan(&id); err != nil {
			return nil, err
		}
		result = append(result, id)
	}
	return result, rows.Err()
}

func (s *Store) Detail(ctx context.Context, id string) (domain.EntityDetail, error) {
	return readJSON[domain.EntityDetail](ctx, s, `SELECT jsonb_build_object(
 'entity',`+localizedEntityJSON("$2")+`,
 'originalTerm',(SELECT jsonb_build_object('language',r.lexical->>'language','transliteration',r.lexical->>'transliteration','strong',r.lexical->>'extendedStrong') FROM entity_source_records r WHERE r.entity_id=e.id AND r.lexical IS NOT NULL ORDER BY r.id LIMIT 1),
 'aliases',COALESCE((SELECT to_jsonb(l.aliases) FROM entity_localizations l WHERE l.entity_id=e.id AND l.language=$2 ORDER BY (l.source_id LIKE 'step.%'),l.source_id LIMIT 1),
 (SELECT jsonb_agg(a.alias ORDER BY a.position,a.alias) FROM entity_aliases a WHERE a.entity_id=e.id AND $2='en'),'[]'::jsonb),
 'approximateDates',COALESCE(dl.fields->>'approximateDates',CASE WHEN $2='en' THEN d.approximate_dates END),
 'role',COALESCE(dl.fields->>'role',CASE WHEN $2='en' THEN d.role END),'modernGeography',COALESCE(dl.fields->>'modernGeography',CASE WHEN $2='en' THEN d.modern_geography END),
 'keyPassages',COALESCE((SELECT jsonb_agg(`+passageJSON+` ORDER BY p.position) FROM entity_key_passages p WHERE p.entity_id=e.id),'[]'::jsonb),
 'sources',COALESCE((SELECT jsonb_agg(`+localizedSourceJSON("$2")+` ORDER BY s.position,s.id) FROM sources s WHERE s.id IN (
 SELECT ds.source_id FROM entity_detail_sources ds WHERE ds.entity_id=e.id
 UNION SELECT es.source_id FROM entity_sources es WHERE es.entity_id=e.id
 UNION SELECT sr.source_id FROM entity_source_records sr WHERE sr.entity_id=e.id
 UNION SELECT l.source_id FROM entity_localizations l WHERE l.entity_id=e.id AND l.language IN ($2,'en'))),
 (SELECT jsonb_agg(`+localizedSourceJSON("$2")+`) FROM sources s WHERE s.id='fixture.source.editorial'),'[]'::jsonb),
 'localizations',(SELECT jsonb_agg(jsonb_build_object('language',l.language,'sourceId',l.source_id,
 'name',l.name,'aliases',l.aliases,'description',l.description) ORDER BY l.language,l.source_id)
 FROM entity_localizations l WHERE l.entity_id=e.id),
 'sourceRecords',(SELECT jsonb_agg(jsonb_build_object('id',sr.id,'sourceId',sr.source_id,
 'externalId',sr.external_id,'sourceLine',sr.source_line,'identifiers',sr.identifiers,'lexical',sr.lexical,
 'occurrenceCount',(SELECT count(*) FROM entity_occurrences o WHERE o.record_id=sr.id),
 'dataset',jsonb_build_object('repository',ds.repository,'revision',ds.revision,'path',ds.path,
 'sha256',ds.sha256,'licenseUrl',ds.license_url,'attribution',ds.attribution,'modifications',ds.modifications))
 ORDER BY sr.id) FROM entity_source_records sr JOIN source_datasets ds
 ON ds.source_id=sr.source_id AND ds.revision=sr.revision WHERE sr.entity_id=e.id))
 FROM entities e LEFT JOIN entity_details d ON d.entity_id=e.id
 LEFT JOIN LATERAL (SELECT fields FROM entity_localizations l WHERE l.entity_id=e.id AND l.language=$2
 ORDER BY (l.source_id LIKE 'step.%'),l.source_id LIMIT 1) dl ON true WHERE e.id=$1`, id, store.Language(ctx))
}

func (s *Store) Neighbors(ctx context.Context, id string, limit int) (domain.GraphSnapshot, error) {
	if limit < 1 || limit > 48 {
		return domain.GraphSnapshot{}, fmt.Errorf("graph limit must be 1–48")
	}
	return readJSON[domain.GraphSnapshot](ctx, s, `WITH touching AS (
 SELECT CASE WHEN r.source_entity_id=$1 THEN r.target_entity_id ELSE r.source_entity_id END id,
 COALESCE(r.confidence,0) confidence,r.position FROM relationships r
 WHERE r.source_entity_id=$1 OR r.target_entity_id=$1
 ), ranked AS (
 SELECT DISTINCT ON (t.id) t.id,t.confidence,t.position FROM touching t WHERE t.id<>$1
 ORDER BY t.id,t.confidence DESC,t.position
 ), neighbors AS (
 SELECT e.*,r.confidence,r.position edge_position FROM ranked r JOIN entities e ON e.id=r.id
 ORDER BY r.confidence DESC,e.name COLLATE "C",r.position,e.id LIMIT $2
 ), selected AS (SELECT id FROM neighbors UNION SELECT $1::text)
 SELECT jsonb_build_object('root',`+localizedEntityJSON("$3")+`,
 'nodes',COALESCE((SELECT jsonb_agg(`+localizedEntityJSON("$3")+` ORDER BY e.confidence DESC,e.name COLLATE "C",e.edge_position,e.id) FROM neighbors e),'[]'::jsonb),
 'edges',COALESCE((SELECT jsonb_agg(`+relationshipJSON+` ORDER BY r.position,r.id) FROM relationships r
 WHERE r.source_entity_id IN (SELECT id FROM selected) AND r.target_entity_id IN (SELECT id FROM selected)),'[]'::jsonb))
 FROM entities e WHERE e.id=$1`, id, limit, store.Language(ctx))
}

func (s *Store) DailyVersePool(ctx context.Context) ([]domain.PassageReference, error) {
	return readJSON[[]domain.PassageReference](ctx, s, `SELECT COALESCE(jsonb_agg(`+passageJSON+` ORDER BY p.position),'[]'::jsonb) FROM daily_verse_pool p`)
}

func (s *Store) Timeline(ctx context.Context, entityID string) (domain.Timeline, error) {
	return readJSON[domain.Timeline](ctx, s, `WITH events AS (
 SELECT t.*,tl.fields FROM timeline_events t LEFT JOIN LATERAL (SELECT fields FROM timeline_localizations l WHERE l.event_id=t.id AND l.language=$2 ORDER BY l.source_id LIMIT 1) tl ON true WHERE ($2='en' OR NULLIF(tl.fields->>'title','') IS NOT NULL) AND ($1='' OR EXISTS (SELECT 1 FROM timeline_event_entities x WHERE x.event_id=t.id AND x.entity_id=$1))
 ) SELECT jsonb_build_object('events',COALESCE((SELECT jsonb_agg(jsonb_build_object(
 'id',t.id,'title',COALESCE(t.fields->>'title',t.title),'startYear',t.start_year,'endYear',t.end_year,'datePrecision',t.date_precision,'summary',COALESCE(t.fields->>'summary',CASE WHEN $2='en' THEN t.summary END),
 'entityIds',COALESCE((SELECT jsonb_agg(x.entity_id ORDER BY x.position,x.entity_id) FROM timeline_event_entities x WHERE x.event_id=t.id),'[]'::jsonb),
 'sourceReferenceIds',COALESCE((SELECT jsonb_agg(x.source_id ORDER BY x.position,x.source_id) FROM timeline_event_sources x WHERE x.event_id=t.id),'[]'::jsonb)
 ) ORDER BY t.start_year NULLS LAST,(COALESCE(t.end_year,t.start_year)::bigint-t.start_year::bigint) DESC,t.position,t.id) FROM events t),'[]'::jsonb),
 'entityNames',COALESCE((SELECT jsonb_object_agg(e.id,(`+localizedEntityJSON("$2")+`)->>'name') FROM entities e WHERE EXISTS
 (SELECT 1 FROM timeline_event_entities x JOIN events t ON t.id=x.event_id WHERE x.entity_id=e.id)),'{}'::jsonb))`, entityID, store.Language(ctx))
}
