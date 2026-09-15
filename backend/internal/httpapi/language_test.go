package httpapi

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"verbum/backend/internal/domain"
	"verbum/backend/internal/store"
)

type languageStore struct {
	store.Store
	language string
}

func (s *languageStore) Entities(ctx context.Context, _ domain.EntityType) ([]domain.Entity, error) {
	s.language = store.Language(ctx)
	return []domain.Entity{}, nil
}

func TestContentLanguageReachesExistingStore(t *testing.T) {
	for _, test := range []struct{ query, want string }{{"", "en"}, {"en", "en"}, {"pt", "pt-BR"}, {"pt-BR", "pt-BR"}, {"unsupported", "en"}} {
		t.Run(test.query, func(t *testing.T) {
			s := &languageStore{}
			handler := New(s, time.Now, nil, nil, nil, nil)
			response := httptest.NewRecorder()
			handler.ServeHTTP(response, httptest.NewRequest(http.MethodGet, "/v1/entities?type=person&lang="+test.query, nil))
			if response.Code != http.StatusOK || s.language != test.want {
				t.Fatalf("status=%d language=%s", response.Code, s.language)
			}
		})
	}
}
