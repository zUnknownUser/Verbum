package memory

import (
	"context"
	"verbum/backend/internal/domain"
	"verbum/backend/internal/store"
)

func (s *Store) EntityPage(ctx context.Context, kind domain.EntityType, request store.EntityPageRequest) (store.EntityPage, error) {
	entities, err := s.Entities(ctx, kind)
	if err != nil {
		return store.EntityPage{}, err
	}
	return store.PaginateEntities(entities, request), nil
}
