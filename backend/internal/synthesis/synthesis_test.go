package synthesis

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
	return &Client{apiKey: "test-key", model: DefaultModel, endpoint: srv.URL, client: srv.Client()}
}

func TestCompleteSendsExpectedRequestAndReturnsContent(t *testing.T) {
	var gotAuth string
	var gotBody map[string]any
	c := client(t, func(w http.ResponseWriter, r *http.Request) {
		gotAuth = r.Header.Get("Authorization")
		_ = json.NewDecoder(r.Body).Decode(&gotBody)
		_ = json.NewEncoder(w).Encode(map[string]any{
			"choices": []map[string]any{{"message": map[string]any{"content": `{"answer":"x"}`}}},
		})
	})
	got, err := c.Complete(context.Background(), "system", "user question")
	if err != nil {
		t.Fatal(err)
	}
	if gotAuth != "Bearer test-key" {
		t.Errorf("Authorization = %q", gotAuth)
	}
	if gotBody["model"] != DefaultModel {
		t.Errorf("model = %v", gotBody["model"])
	}
	if gotBody["reasoning_effort"] != "none" || gotBody["max_completion_tokens"] != float64(1024) {
		t.Error("missing bounded Luna settings")
	}
	if _, ok := gotBody["temperature"]; ok {
		t.Error("unvalidated sampling parameter sent to Luna")
	}
	rf, _ := gotBody["response_format"].(map[string]any)
	if rf["type"] != "json_object" {
		t.Errorf("response_format = %#v", rf)
	}
	if got != `{"answer":"x"}` {
		t.Errorf("content = %q", got)
	}
}

func TestCompleteRejectsNonOKStatus(t *testing.T) {
	c := client(t, func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
	})
	if _, err := c.Complete(context.Background(), "s", "u"); err == nil {
		t.Fatal("want an error for a non-200 upstream response")
	}
}

func TestCompleteRejectsEmptyChoices(t *testing.T) {
	c := client(t, func(w http.ResponseWriter, r *http.Request) {
		_ = json.NewEncoder(w).Encode(map[string]any{"choices": []map[string]any{}})
	})
	if _, err := c.Complete(context.Background(), "s", "u"); err == nil {
		t.Fatal("want an error for empty choices")
	}
}

func TestUnsupportedModelDoesNotCallProvider(t *testing.T) {
	c := client(t, func(http.ResponseWriter, *http.Request) { t.Fatal("unsupported model called provider") })
	c.model = "gpt-unknown"
	if _, err := c.Complete(context.Background(), "s", "u"); err == nil {
		t.Fatal("unpriced model accepted")
	}
}
func TestLegacyModelRetainsRollbackContract(t *testing.T) {
	c := client(t, func(w http.ResponseWriter, r *http.Request) {
		var got map[string]any
		json.NewDecoder(r.Body).Decode(&got)
		if got["model"] != legacyModel || got["temperature"] != float64(0) {
			t.Error("rollback contract changed")
		}
		if _, ok := got["reasoning_effort"]; ok {
			t.Error("legacy model received reasoning")
		}
		w.Write([]byte(`{"choices":[{"message":{"content":"{}"}}]}`))
	})
	c.model = legacyModel
	if _, err := c.Complete(context.Background(), "JSON", "u"); err != nil {
		t.Fatal(err)
	}
}
