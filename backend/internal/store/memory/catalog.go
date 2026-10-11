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
	if request.Category != "" {
		categories := map[string][]string{
			"fixture.event.exodus": {"exodus"}, "fixture.event.david-goliath": {"monarchy"}, "fixture.event.david-anointed": {"monarchy"}, "fixture.event.david-takes-jerusalem": {"monarchy"},
			"fixture.theme.faith": {"with-god", "foundations"}, "fixture.theme.prayer": {"with-god", "emotions"},
			"fixture.theme.love": {"relationships", "with-god"}, "fixture.theme.forgiveness": {"relationships", "foundations"},
			"fixture.theme.anxiety": {"emotions"}, "fixture.theme.suffering": {"emotions"},
			"fixture.theme.money": {"daily-life"}, "fixture.theme.justice": {"daily-life", "community"},
			"fixture.theme.wisdom": {"character", "daily-life"}, "fixture.theme.grace": {"foundations"},
		}
		filtered := []domain.Entity{}
		for _, e := range entities {
			for _, category := range categories[e.ID] {
				if category == request.Category {
					filtered = append(filtered, e)
					break
				}
			}
		}
		entities = filtered
	}
	return store.PaginateEntities(entities, request), nil
}
