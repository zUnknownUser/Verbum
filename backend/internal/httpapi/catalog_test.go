package httpapi

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"
	"verbum/backend/internal/store"
	"verbum/backend/internal/store/memory"
)

func TestCatalogPaginationContractAndValidation(t *testing.T) {
	s, err := memory.Load("../../db/seed/fixtures.json")
	if err != nil {
		t.Fatal(err)
	}
	h := New(s, time.Now, nil, nil, nil, nil)
	for _, query := range []string{"limit=0", "limit=101", "offset=-1", "offset=1000001", "letter=AA", "letter=%27", "limit=no", "offset=no", "category=unknown", "category=emotions"} {
		w := httptest.NewRecorder()
		h.ServeHTTP(w, httptest.NewRequest(http.MethodGet, "/v1/entities?type=person&"+query, nil))
		if w.Code != 400 {
			t.Fatalf("%s: %d", query, w.Code)
		}
	}
	w := httptest.NewRecorder()
	h.ServeHTTP(w, httptest.NewRequest(http.MethodGet, "/v1/entities?type=person&limit=1", nil))
	var page store.EntityPage
	if w.Code != 200 || json.Unmarshal(w.Body.Bytes(), &page) != nil || len(page.Entities) != 1 || page.NextOffset == nil || len(page.Letters) == 0 {
		t.Fatalf("bad page: %s", w.Body.String())
	}
	w = httptest.NewRecorder()
	h.ServeHTTP(w, httptest.NewRequest(http.MethodGet, "/v1/entities?type=person&q=missing&limit=30", nil))
	if w.Code != 200 || json.Unmarshal(w.Body.Bytes(), &page) != nil || len(page.Entities) != 0 || page.NextOffset != nil {
		t.Fatalf("empty page: %s", w.Body.String())
	}
}

func TestEventPeriodValidation(t *testing.T) {
	s, err := memory.Load("../../db/seed/fixtures.json")
	if err != nil {
		t.Fatal(err)
	}
	h := New(s, time.Now, nil, nil, nil, nil)
	for _, tc := range []struct {
		kind, category string
		status         int
	}{
		{"event", "exodus", 200}, {"event", "monarchy", 200}, {"event", "", 200},
		{"event", "emotions", 400}, {"theme", "exodus", 400}, {"person", "exodus", 400}, {"event", "unknown", 400},
	} {
		w := httptest.NewRecorder()
		h.ServeHTTP(w, httptest.NewRequest(http.MethodGet, "/v1/entities?type="+tc.kind+"&category="+tc.category, nil))
		if w.Code != tc.status {
			t.Fatalf("%+v: %d", tc, w.Code)
		}
		if tc.kind == "event" && tc.category == "exodus" {
			var page store.EntityPage
			if json.Unmarshal(w.Body.Bytes(), &page) != nil || len(page.Entities) != 1 || page.Entities[0].ID != "fixture.event.exodus" {
				t.Fatalf("wrong fixture period: %s", w.Body.String())
			}
		}
	}
}

func TestNarrativeOrderValidation(t *testing.T) {
	s, err := memory.Load("../../db/seed/fixtures.json")
	if err != nil {
		t.Fatal(err)
	}
	h := New(s, time.Now, nil, nil, nil, nil)
	for _, tc := range []struct {
		query  string
		status int
	}{
		{"type=event&category=exodus&order=narrative", 200},
		{"type=event&category=exodus&order=alphabetical&letter=A", 200},
		{"type=event&category=exodus&order=narrative&letter=A", 400},
		{"type=event&order=narrative", 400}, {"type=person&order=narrative", 400}, {"type=event&order=unknown", 400},
	} {
		w := httptest.NewRecorder()
		h.ServeHTTP(w, httptest.NewRequest(http.MethodGet, "/v1/entities?"+tc.query, nil))
		if w.Code != tc.status {
			t.Fatalf("%s: %d", tc.query, w.Code)
		}
	}
}
