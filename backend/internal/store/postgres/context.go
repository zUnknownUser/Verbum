package postgres

import (
	"context"
	"fmt"
	"verbum/backend/internal/domain"
)

// Context follows only existing, sourced associations, as the fixture store does.
func (s *Store) Context(ctx context.Context, bookID string, chapter int) (domain.PassageContext, error) {
	return readJSON[domain.PassageContext](ctx, s, `WITH direct_edges AS (
 SELECT r.* FROM relationships r WHERE r.source_entity_id=$1 OR r.target_entity_id=$1
 ), details AS (
 SELECT DISTINCT p.entity_id FROM entity_key_passages p WHERE p.book_id=$2 AND p.chapter=$3
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
 ), source_ids AS (
 SELECT rs.source_id FROM relationship_sources rs JOIN direct_edges r ON r.id=rs.relationship_id
 UNION SELECT ds.source_id FROM entity_detail_sources ds JOIN details d ON d.entity_id=ds.entity_id
 UNION SELECT rs.source_id FROM relationship_sources rs JOIN related r ON r.id=rs.relationship_id
 ) SELECT jsonb_build_object('reference',jsonb_build_object('bookId',$2::text,'chapter',$3::int),
 'entities',COALESCE((SELECT jsonb_agg(`+entityJSON+` ORDER BY e.position,e.id) FROM entities e WHERE e.id IN (SELECT id FROM linked) AND e.type<>'passage'),'[]'::jsonb),
 'relatedPassages',COALESCE((SELECT jsonb_agg(jsonb_build_object('bookId',split_part(p.other,'.',2),'chapter',split_part(p.other,'.',3)::int) ORDER BY p.position,p.other) FROM related_passages p),'[]'::jsonb),
 'sources',COALESCE((SELECT jsonb_agg(`+sourceJSON+` ORDER BY s.position,s.id) FROM sources s WHERE s.id IN (SELECT source_id FROM source_ids)),'[]'::jsonb))
 WHERE EXISTS (SELECT 1 FROM linked)`, fmt.Sprintf("passage.%s.%d", bookID, chapter), bookID, chapter)
}
