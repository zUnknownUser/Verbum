package postgres

import (
	"context"
	"verbum/backend/internal/domain"
	"verbum/backend/internal/store"
)

// EntitySources attributes graph enrichment separately from the Bible translation.
// Returned only for entity IDs actually linked to verified Ask citations.
func (s *Store) EntitySources(ctx context.Context, ids []string) ([]domain.SourceReference, error) {
	return readJSON[[]domain.SourceReference](ctx, s, `SELECT COALESCE(jsonb_agg(`+localizedSourceJSON("$2")+` ORDER BY s.id),'[]'::jsonb)
 FROM sources s WHERE s.id IN (
 SELECT r.source_id FROM entity_source_records r WHERE r.entity_id=ANY($1::text[]))`, ids, store.Language(ctx))
}
