package httpapi

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"verbum/backend/internal/realtime"
	"verbum/backend/internal/store/memory"
)

type fakeBroker struct {
	session realtime.Session
	err     error
	model   string // last model requested, for assertions
}

func (f *fakeBroker) CreateSession(_ context.Context, model string, _ time.Duration) (realtime.Session, error) {
	f.model = model
	return f.session, f.err
}

func realtimeServer(t *testing.T, rt realtimeBroker) *httptest.Server {
	t.Helper()
	s, err := memory.Load("../../db/seed/fixtures.json")
	if err != nil {
		t.Fatal(err)
	}
	srv := httptest.NewServer(New(s, time.Now, rt, nil, nil))
	t.Cleanup(srv.Close)
	return srv
}

func TestRealtimeSessionNotConfigured(t *testing.T) {
	srv := realtimeServer(t, nil)
	res, err := http.Post(srv.URL+"/v1/realtime/session", "", nil)
	if err != nil {
		t.Fatal(err)
	}
	defer res.Body.Close()
	if res.StatusCode != http.StatusServiceUnavailable {
		t.Fatalf("status = %d", res.StatusCode)
	}
	var p Problem
	_ = json.NewDecoder(res.Body).Decode(&p)
	if p.Code != CodeRealtimeUnavailable {
		t.Errorf("code = %q", p.Code)
	}
}

func TestRealtimeSessionSuccess(t *testing.T) {
	fake := &fakeBroker{session: realtime.Session{ClientSecret: "ek_test", ExpiresAt: 42, Model: "gpt-realtime"}}
	srv := realtimeServer(t, fake)
	res, err := http.Post(srv.URL+"/v1/realtime/session?model=custom", "", nil)
	if err != nil {
		t.Fatal(err)
	}
	defer res.Body.Close()
	if res.StatusCode != http.StatusOK {
		t.Fatalf("status = %d", res.StatusCode)
	}
	if cc := res.Header.Get("Cache-Control"); cc != "no-store" {
		t.Errorf("Cache-Control = %q, want no-store (this is a one-time secret)", cc)
	}
	var got realtime.Session
	if err := json.NewDecoder(res.Body).Decode(&got); err != nil {
		t.Fatal(err)
	}
	if got != fake.session {
		t.Errorf("body = %#v", got)
	}
	if fake.model != "custom" {
		t.Errorf("model forwarded = %q, want %q", fake.model, "custom")
	}
}

func TestRealtimeSessionUpstreamFailure(t *testing.T) {
	fake := &fakeBroker{err: errors.New("upstream exploded")}
	srv := realtimeServer(t, fake)
	res, err := http.Post(srv.URL+"/v1/realtime/session", "", nil)
	if err != nil {
		t.Fatal(err)
	}
	defer res.Body.Close()
	if res.StatusCode != http.StatusBadGateway {
		t.Fatalf("status = %d", res.StatusCode)
	}
	var p Problem
	_ = json.NewDecoder(res.Body).Decode(&p)
	if p.Code != CodeInternal || p.Message == "upstream exploded" {
		t.Errorf("problem = %#v (must not leak the upstream error)", p)
	}
}
