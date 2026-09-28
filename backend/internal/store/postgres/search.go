package postgres

import (
	"context"
	"strings"

	"verbum/backend/internal/domain"
	"verbum/backend/internal/reference"
	"verbum/backend/internal/retrieval"
)

// SearchPassages fuses independently ranked lexical, semantic and structured channels.
// RRF avoids comparing a cosine similarity with an unrelated ts_rank scale. Each channel
// considers more candidates than the final result, with hard bounds on SQL work and output.
func (s *Store) SearchPassages(ctx context.Context, queryText string, queryEmbedding []float32, limit int) ([]domain.PassageReference, error) {
	text := strings.TrimSpace(queryText)
	if limit <= 0 {
		limit = 6
	}
	if limit > 40 {
		limit = 40
	}
	if ref, ok := reference.ParseVerse(text); ok {
		texts, err := s.PassageText(ctx, "WEB", []domain.PassageReference{ref})
		if err != nil {
			return nil, err
		}
		if texts[ref.Key()] != "" {
			return []domain.PassageReference{ref}, nil
		}
		return []domain.PassageReference{}, nil
	}
	var vector *string
	if len(queryEmbedding) > 0 {
		v := vectorLiteral(queryEmbedding)
		vector = &v
	}
	if text == "" && vector == nil {
		return []domain.PassageReference{}, nil
	}
	terms := retrieval.Terms(text)
	if terms == nil {
		terms = []string{}
	}
	alternatives := make([]string, len(terms))
	for i, term := range terms {
		alternatives[i] = "(" + term + ")"
	}
	pool := max(48, min(120, limit*4))
	return readJSON[[]domain.PassageReference](ctx, s, passageSearchSQL, text, vector, limit, pool, terms, strings.Join(alternatives, " | "))
}

const passageSearchSQL = `WITH concept_queries AS MATERIALIZED (
 SELECT query FROM (SELECT to_tsquery('english',term) query FROM unnest($5::text[]) term) q WHERE numnode(query)>0
 ), concept_frequency AS MATERIALIZED (
 SELECT query,(SELECT count(*) FROM scripture_verses WHERE translation='WEB' AND to_tsvector('english',text) @@ query) frequency
 FROM concept_queries
 ), concepts AS MATERIALIZED (
 SELECT query,frequency,1.0/(1+ln(1+frequency)) weight FROM concept_frequency
 ), structured_entities AS (
 SELECT e.id FROM entities e WHERE $1<>'' AND (
 EXISTS (SELECT 1 FROM entity_localizations l WHERE l.entity_id=e.id AND
 (lower(l.name)=lower($1) OR EXISTS (SELECT 1 FROM unnest(l.aliases) a WHERE lower(a)=lower($1))))
 OR EXISTS (SELECT 1 FROM entity_source_records r WHERE r.entity_id=e.id AND
 EXISTS (SELECT 1 FROM jsonb_each(r.identifiers) kv,jsonb_array_elements_text(kv.value) v WHERE lower(v)=lower($1))))
 ORDER BY e.id LIMIT 64
 ), structured_pool AS (
 SELECT DISTINCT p.book_id,p.chapter,p.verse_start verse
 FROM entity_passage_associations p JOIN structured_entities e ON e.id=p.entity_id
 WHERE p.verse_start IS NOT NULL AND EXISTS (SELECT 1 FROM scripture_verses sv
 WHERE sv.translation='WEB' AND sv.book_id=p.book_id AND sv.chapter=p.chapter AND sv.verse=p.verse_start)
 ORDER BY p.book_id,p.chapter,p.verse_start LIMIT $4
 ), matching AS MATERIALIZED (
 SELECT book_id,chapter,verse,to_tsvector('english',text) words FROM scripture_verses
 WHERE translation='WEB' AND $6<>'' AND to_tsvector('english',text) @@ to_tsquery('english',$6)
 ), lexical_scores AS (
 SELECT book_id,chapter,verse,
 (SELECT count(*) FROM concepts WHERE words @@ query) matches,
 EXISTS (SELECT 1 FROM concepts WHERE frequency <= 50 AND words @@ query) rare_match,
 (SELECT COALESCE(sum(weight),0) FROM concepts WHERE words @@ query) coverage,
 ts_rank_cd(words,to_tsquery('english',$6),2) strength FROM matching
 ), lexical_pool AS (
 SELECT * FROM lexical_scores WHERE matches >= LEAST(2,(SELECT count(*) FROM concepts)) OR rare_match
 ORDER BY coverage DESC,strength DESC,book_id,chapter,verse LIMIT $4
 ), semantic_pool AS (
 SELECT book_id,chapter,verse,embedding OPERATOR(public.<=>) $2::public.vector distance
 FROM scripture_verses WHERE translation='WEB' AND embedding IS NOT NULL AND $2::public.vector IS NOT NULL
 ORDER BY embedding OPERATOR(public.<=>) $2::public.vector LIMIT $4
 ), combined AS (
 SELECT book_id,chapter,verse,SUM(score) score FROM (
 SELECT book_id,chapter,verse,1.0/(20+row_number() OVER (ORDER BY coverage DESC,strength DESC,book_id,chapter,verse)) score FROM lexical_pool
 UNION ALL
 SELECT book_id,chapter,verse,2.0/(20+row_number() OVER (ORDER BY distance,book_id,chapter,verse)) score FROM semantic_pool
 UNION ALL
 SELECT book_id,chapter,verse,3.0/(20+row_number() OVER (ORDER BY book_id,chapter,verse)) score FROM structured_pool
 ) hits GROUP BY book_id,chapter,verse
 ), ranked AS (
 SELECT * FROM combined ORDER BY score DESC,book_id,chapter,verse LIMIT $3
 ) SELECT COALESCE(jsonb_agg(jsonb_build_object('bookId',book_id,'chapter',chapter,'verseStart',verse,'verseEnd',verse)
 ORDER BY score DESC,book_id,chapter,verse),'[]'::jsonb) FROM ranked`
