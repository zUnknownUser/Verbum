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

func TestLanguageHeaderAndExplicitQueryPrecedence(t *testing.T) {
	for _, test := range []struct{ query, header, want string }{
		{"", "pt-BR,pt;q=0.9,en;q=0.8", "pt-BR"},
		{"en", "pt-BR", "en"}, {"", "fr", "en"},
	} {
		s := &languageStore{}
		handler := New(s, time.Now, nil, nil, nil, nil)
		request := httptest.NewRequest(http.MethodGet, "/v1/entities?type=person&lang="+test.query, nil)
		request.Header.Set("Accept-Language", test.header)
		response := httptest.NewRecorder()
		handler.ServeHTTP(response, request)
		if s.language != test.want {
			t.Fatalf("language=%s want=%s", s.language, test.want)
		}
	}
}
