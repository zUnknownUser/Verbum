package httpapi

import (
	"context"
	"errors"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"verbum/backend/internal/domain"
	"verbum/backend/internal/store"
	"verbum/backend/internal/store/memory"
)

// recordingStore delegates everything to an embedded store.Store except SearchPassages, which
// it captures instead of executing — a seam so these tests don't need real Scripture data.
type recordingStore struct {
	store.Store
	gotEmbedding []float32
	gotQuery     string
}

func (r *recordingStore) SearchPassages(_ context.Context, q string, embedding []float32, _ int) ([]domain.PassageReference, error) {
	r.gotQuery = q
	r.gotEmbedding = embedding
	return []domain.PassageReference{}, nil
}

type fakeEmbedder struct {
	vector []float32
	err    error
}

func (f *fakeEmbedder) Embed(_ context.Context, _ string) ([]float32, error) { return f.vector, f.err }

func recordingSearchServer(t *testing.T, embedder queryEmbedder) (*httptest.Server, *recordingStore) {
	t.Helper()
	base, err := memory.Load("../../db/seed/fixtures.json")
	if err != nil {
		t.Fatal(err)
	}
	rec := &recordingStore{Store: base}
	srv := httptest.NewServer(New(rec, time.Now, nil, embedder, nil))
	t.Cleanup(srv.Close)
	return srv, rec
}

func TestSearchWithoutEmbedderIsLexicalOnly(t *testing.T) {
	srv, rec := recordingSearchServer(t, nil)
	res, err := http.Get(srv.URL + "/v1/search?q=David")
	if err != nil {
		t.Fatal(err)
	}
	defer res.Body.Close()
	if res.StatusCode != http.StatusOK {
		t.Fatalf("status = %d", res.StatusCode)
	}
	if rec.gotQuery != "David" {
		t.Errorf("query forwarded = %q", rec.gotQuery)
	}
	if rec.gotEmbedding != nil {
		t.Errorf("embedding = %v, want nil (no embedder configured)", rec.gotEmbedding)
	}
}

func TestSearchUsesEmbedderWhenConfigured(t *testing.T) {
	srv, rec := recordingSearchServer(t, &fakeEmbedder{vector: []float32{1, 2, 3}})
	res, err := http.Get(srv.URL + "/v1/search?q=David")
	if err != nil {
		t.Fatal(err)
	}
	defer res.Body.Close()
	if res.StatusCode != http.StatusOK {
		t.Fatalf("status = %d", res.StatusCode)
	}
	if len(rec.gotEmbedding) != 3 {
		t.Errorf("embedding = %v, want the fake vector", rec.gotEmbedding)
	}
}

func TestSearchDegradesWhenEmbedderFails(t *testing.T) {
	srv, rec := recordingSearchServer(t, &fakeEmbedder{err: errors.New("upstream exploded")})
	res, err := http.Get(srv.URL + "/v1/search?q=David")
	if err != nil {
		t.Fatal(err)
	}
	defer res.Body.Close()
	if res.StatusCode != http.StatusOK {
		t.Fatalf("status = %d, want 200 (embedder failure must not fail the whole search)", res.StatusCode)
	}
	if rec.gotEmbedding != nil {
		t.Errorf("embedding = %v, want nil after embedder failure", rec.gotEmbedding)
	}
}
