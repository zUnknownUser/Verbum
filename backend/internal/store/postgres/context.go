package postgres

import (
	"context"
	"fmt"
	"verbum/backend/internal/domain"
	"verbum/backend/internal/store"
)

// Context follows only existing, sourced associations, as the fixture store does.
func (s *Store) Context(ctx context.Context, bookID string, chapter int) (domain.PassageContext, error) {
	return readJSON[domain.PassageContext](ctx, s, `WITH direct_edges AS (
 SELECT r.* FROM relationships r WHERE r.source_entity_id=$1 OR r.target_entity_id=$1
 ), details AS (
 SELECT DISTINCT p.entity_id FROM entity_passage_associations p WHERE p.book_id=$2 AND p.chapter=$3
 ), linked AS (
 SELECT CASE WHEN r.source_entity_id=$1 THEN r.target_entity_id ELSE r.source_entity_id END id FROM direct_edges r
 UNION SELECT entity_id FROM details
 ), related_edges AS (
 SELECT r.*,CASE WHEN r.source_entity_id IN (SELECT id FROM linked) THEN r.target_entity_id ELSE r.source_entity_id END other
 FROM relationships r WHERE r.source_entity_id IN (SELECT id FROM linked) OR r.target_entity_id IN (SELECT id FROM linked)
 ), related AS (
 SELECT * FROM related_edges WHERE other<>$1 AND other ~ '^passage\.[^.]+\.[0-9]+$'
 ), related_passages AS (
 SELECT other,min(position) position FROM related GROUP BY other
 ), selected_records AS (
 SELECT DISTINCT r.id,r.source_id FROM entity_source_records r
 JOIN entity_occurrences o ON o.record_id=r.id
 WHERE o.book_id=$2 AND o.chapter=$3 AND o.verse=$5 AND $5>0
 ), occurrence_refs AS (
 SELECT DISTINCT o.book_id,o.chapter,o.verse FROM selected_records r
 CROSS JOIN LATERAL (
  SELECT DISTINCT book_id,chapter,verse FROM entity_occurrences
  WHERE record_id=r.id AND NOT (book_id=$2 AND chapter=$3)
  ORDER BY book_id,chapter,verse LIMIT 4
 ) o
 ORDER BY o.book_id,o.chapter,o.verse LIMIT 24
 ), source_ids AS (
 SELECT source_id FROM selected_records
 UNION SELECT rs.source_id FROM relationship_sources rs JOIN direct_edges r ON r.id=rs.relationship_id
 UNION SELECT ds.source_id FROM entity_detail_sources ds JOIN details d ON d.entity_id=ds.entity_id
 UNION SELECT sr.source_id FROM entity_source_records sr JOIN entity_occurrences o ON o.record_id=sr.id WHERE o.book_id=$2 AND o.chapter=$3
 UNION SELECT l.source_id FROM entity_localizations l WHERE l.entity_id IN (SELECT id FROM linked) AND l.language IN ($4,'en')
 UNION SELECT rs.source_id FROM relationship_sources rs JOIN related r ON r.id=rs.relationship_id
 ) SELECT jsonb_build_object('reference',jsonb_build_object('bookId',$2::text,'chapter',$3::int),
 'entities',COALESCE((SELECT jsonb_agg(`+localizedEntityJSON("$4")+` ORDER BY e.position,e.id) FROM entities e WHERE e.id IN (SELECT id FROM linked) AND e.type<>'passage'),'[]'::jsonb),
 'relatedPassages',COALESCE((SELECT jsonb_agg(jsonb_build_object('bookId',o.book_id,'chapter',o.chapter,'verseStart',o.verse,'verseEnd',o.verse) ORDER BY o.book_id,o.chapter,o.verse) FROM occurrence_refs o),'[]'::jsonb) || COALESCE((SELECT jsonb_agg(jsonb_build_object('bookId',split_part(p.other,'.',2),'chapter',split_part(p.other,'.',3)::int) ORDER BY p.position,p.other) FROM related_passages p),'[]'::jsonb),
 'sources',COALESCE((SELECT jsonb_agg(`+localizedSourceJSON("$4")+` ORDER BY s.position,s.id) FROM sources s WHERE s.id IN (SELECT source_id FROM source_ids)),'[]'::jsonb))
 WHERE EXISTS (SELECT 1 FROM linked)`, fmt.Sprintf("passage.%s.%d", bookID, chapter), bookID, chapter, store.Language(ctx), store.Verse(ctx))
}
