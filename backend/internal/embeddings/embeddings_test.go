package embeddings

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
)

func client(t *testing.T, handler http.HandlerFunc) *Client {
	t.Helper()
	srv := httptest.NewServer(handler)
	t.Cleanup(srv.Close)
	return &Client{apiKey: "test-key", endpoint: srv.URL, client: srv.Client()}
}

func fakeVector() []float32 {
	v := make([]float32, Dimensions)
	v[0] = 1
	return v
}

func TestEmbedSendsExpectedRequestAndParsesResponse(t *testing.T) {
	var gotAuth string
	var gotBody map[string]any
	c := client(t, func(w http.ResponseWriter, r *http.Request) {
		gotAuth = r.Header.Get("Authorization")
		_ = json.NewDecoder(r.Body).Decode(&gotBody)
		_ = json.NewEncoder(w).Encode(map[string]any{
			"data": []map[string]any{{"embedding": fakeVector(), "index": 0}},
		})
	})
	got, err := c.Embed(context.Background(), "what is love")
	if err != nil {
		t.Fatal(err)
	}
	if gotAuth != "Bearer test-key" {
		t.Errorf("Authorization = %q", gotAuth)
	}
	if gotBody["model"] != Model || gotBody["input"] != "what is love" || gotBody["dimensions"] != float64(Dimensions) {
		t.Errorf("request body = %#v", gotBody)
	}
	if len(got) != Dimensions || got[0] != 1 {
		t.Errorf("embedding = %v...", got[:min(3, len(got))])
	}
}

func TestEmbedRejectsWrongDimensionCount(t *testing.T) {
	c := client(t, func(w http.ResponseWriter, r *http.Request) {
		_ = json.NewEncoder(w).Encode(map[string]any{
			"data": []map[string]any{{"embedding": []float32{1, 2, 3}, "index": 0}},
		})
	})
	if _, err := c.Embed(context.Background(), "x"); err == nil {
		t.Fatal("want an error for a short embedding")
	}
}

func TestEmbedRejectsNonOKStatus(t *testing.T) {
	c := client(t, func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusTooManyRequests)
	})
	if _, err := c.Embed(context.Background(), "x"); err == nil {
		t.Fatal("want an error for a non-200 upstream response")
	}
}

func TestEmbedRejectsEmptyData(t *testing.T) {
	c := client(t, func(w http.ResponseWriter, r *http.Request) {
		_ = json.NewEncoder(w).Encode(map[string]any{"data": []map[string]any{}})
	})
	if _, err := c.Embed(context.Background(), "x"); err == nil {
		t.Fatal("want an error for empty data")
	}
}
