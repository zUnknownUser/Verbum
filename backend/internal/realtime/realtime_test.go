package realtime

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"
)

func broker(t *testing.T, handler http.HandlerFunc) *Broker {
	t.Helper()
	srv := httptest.NewServer(handler)
	t.Cleanup(srv.Close)
	return &Broker{apiKey: "test-key", endpoint: srv.URL, client: srv.Client()}
}

func TestCreateSessionSendsExpectedRequestAndParsesResponse(t *testing.T) {
	var gotAuth string
	var gotBody map[string]any
	b := broker(t, func(w http.ResponseWriter, r *http.Request) {
		gotAuth = r.Header.Get("Authorization")
		_ = json.NewDecoder(r.Body).Decode(&gotBody)
		w.WriteHeader(http.StatusOK)
		_ = json.NewEncoder(w).Encode(map[string]any{
			"value": "ek_abc123", "expires_at": 1234567890,
			"session": map[string]any{"type": "realtime", "model": "gpt-realtime"},
		})
	})

	session, err := b.CreateSession(context.Background(), "", 45*time.Second)
	if err != nil {
		t.Fatal(err)
	}
	if gotAuth != "Bearer test-key" {
		t.Errorf("Authorization header = %q", gotAuth)
	}
	sessionField, _ := gotBody["session"].(map[string]any)
	if sessionField["model"] != DefaultModel || sessionField["type"] != "realtime" {
		t.Errorf("session field = %#v", sessionField)
	}
	expiresAfter, _ := gotBody["expires_after"].(map[string]any)
	if expiresAfter["anchor"] != "created_at" || expiresAfter["seconds"] != float64(45) {
		t.Errorf("expires_after = %#v", expiresAfter)
	}
	if session != (Session{ClientSecret: "ek_abc123", ExpiresAt: 1234567890, Model: DefaultModel}) {
		t.Errorf("session = %#v", session)
	}
}

func TestCreateSessionHonoursRequestedModel(t *testing.T) {
	b := broker(t, func(w http.ResponseWriter, r *http.Request) {
		var body map[string]any
		_ = json.NewDecoder(r.Body).Decode(&body)
		session, _ := body["session"].(map[string]any)
		_ = json.NewEncoder(w).Encode(map[string]any{"value": "ek_x", "expires_at": 1})
		if session["model"] != "custom-model" {
			t.Errorf("model = %#v", session["model"])
		}
	})
	got, err := b.CreateSession(context.Background(), "custom-model", time.Minute)
	if err != nil {
		t.Fatal(err)
	}
	if got.Model != "custom-model" {
		t.Errorf("Model = %q", got.Model)
	}
}

func TestCreateSessionRejectsNonOKStatus(t *testing.T) {
	b := broker(t, func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusUnauthorized)
		_, _ = w.Write([]byte(`{"error":"invalid api key"}`))
	})
	if _, err := b.CreateSession(context.Background(), "", time.Minute); err == nil {
		t.Fatal("want an error for a non-200 upstream response")
	}
}

func TestCreateSessionRejectsMissingSecret(t *testing.T) {
	b := broker(t, func(w http.ResponseWriter, r *http.Request) {
		_ = json.NewEncoder(w).Encode(map[string]any{"expires_at": 1})
	})
	if _, err := b.CreateSession(context.Background(), "", time.Minute); err == nil {
		t.Fatal("want an error when the response has no client secret")
	}
}
