package httpapi

import (
	"context"
	"errors"
	"net/http/httptest"
	"testing"
	"time"

	"verbum/backend/internal/domain"
	"verbum/backend/internal/store"
)

type readinessStore struct {
	store.Store
	pool    []domain.PassageReference
	err     error
	called  bool
	bounded bool
}

func (s *readinessStore) DailyVersePool(ctx context.Context) ([]domain.PassageReference, error) {
	s.called = true
	deadline, ok := ctx.Deadline()
	s.bounded = ok && time.Until(deadline) <= 2*time.Second
	return s.pool, s.err
}

func TestReadinessRequiresAvailableContent(t *testing.T) {
	for _, tc := range []struct {
		name string
		pool []domain.PassageReference
		err  error
		want int
	}{
		{"ready", []domain.PassageReference{{BookID: "John", Chapter: 3}}, nil, 200},
		{"empty", nil, nil, 503},
		{"database unavailable", nil, errors.New("private database diagnostic"), 503},
		{"deadline", nil, context.DeadlineExceeded, 503},
	} {
		t.Run(tc.name, func(t *testing.T) {
			s := &readinessStore{pool: tc.pool, err: tc.err}
			h := New(s, time.Now, nil, nil, nil, nil)
			w := httptest.NewRecorder()
			h.ServeHTTP(w, httptest.NewRequest("GET", "/readyz", nil))
			if w.Code != tc.want || !s.called || !s.bounded || w.Header().Get("Cache-Control") != "no-store" {
				t.Fatalf("status=%d called=%v bounded=%v headers=%v", w.Code, s.called, s.bounded, w.Header())
			}
			if tc.want == 503 && w.Body.String() != "not ready\n" {
				t.Fatal("readiness leaked diagnostics")
			}
		})
	}
}

func TestLivenessDoesNotDependOnDatabase(t *testing.T) {
	s := &readinessStore{err: errors.New("offline")}
	w := httptest.NewRecorder()
	New(s, time.Now, nil, nil, nil, nil).ServeHTTP(w, httptest.NewRequest("GET", "/healthz", nil))
	if w.Code != 200 || s.called {
		t.Fatalf("status=%d store called=%v", w.Code, s.called)
	}
}
