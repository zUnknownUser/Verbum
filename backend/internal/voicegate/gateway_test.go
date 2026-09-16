package voicegate

import (
	"context"
	"github.com/gorilla/websocket"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync/atomic"
	"testing"
	"time"
	"verbum/backend/internal/testdb"
	"verbum/backend/internal/usage"
)

func TestRelayMetersProviderAndEnforcesDurationAndTicketReplay(t *testing.T) {
	_, url := testdb.Open(t, "../../db/migrations")
	db, err := usage.Open(context.Background(), url)
	if err != nil {
		t.Fatal(err)
	}
	defer db.Close()
	policy := usage.Defaults()
	policy.Free.VoiceSeconds = 1
	svc := usage.New(db, policy)
	g := New(svc, db, "private-provider-key")
	var calls atomic.Int64
	upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("Authorization") != "Bearer private-provider-key" {
			t.Error("missing server credential")
		}
		c, e := (&websocket.Upgrader{}).Upgrade(w, r, nil)
		if e != nil {
			return
		}
		defer c.Close()
		calls.Add(1)
		for {
			var event map[string]any
			if c.ReadJSON(&event) != nil {
				return
			}
			if event["type"] == "session.update" {
				s := event["session"].(map[string]any)
				if s["max_output_tokens"] != float64(384) {
					t.Error("unbounded response")
				}
			}
			if event["type"] == "response.create" {
				_ = c.WriteJSON(map[string]any{"type": "response.done", "response": map[string]any{"usage": map[string]any{"input_tokens": 10, "output_tokens": 10}}})
			}
		}
	}))
	defer upstream.Close()
	g.Endpoint = "ws" + strings.TrimPrefix(upstream.URL, "http")
	relay := httptest.NewServer(g)
	defer relay.Close()
	ctx := usage.WithPrincipal(context.Background(), usage.Principal{UID: "reader", IP: "127.0.0.1", Device: "installation"})
	session, e := g.CreateSession(ctx, "", time.Minute)
	if e != nil {
		t.Fatal(e)
	}
	if session.ClientSecret == g.APIKey || session.RelayPath != "/v1/realtime/connect" {
		t.Fatal("provider credential exposed")
	}
	headers := http.Header{"Authorization": []string{"Bearer " + session.ClientSecret}}
	c, _, e := websocket.DefaultDialer.Dial("ws"+strings.TrimPrefix(relay.URL, "http"), headers)
	if e != nil {
		t.Fatal(e)
	}
	defer c.Close()
	_ = c.SetReadDeadline(time.Now().Add(4 * time.Second))
	_ = c.WriteJSON(map[string]any{"type": "session.update", "session": map[string]any{"instructions": "Scripture study", "max_output_tokens": "inf"}})
	_ = c.WriteJSON(map[string]any{"type": "response.create"})
	var response map[string]any
	if e = c.ReadJSON(&response); e != nil {
		t.Fatal(e)
	}
	if response["type"] != "response.done" {
		t.Fatal(response)
	}
	if e = c.ReadJSON(&response); e != nil {
		t.Fatal(e)
	}
	if response["type"] != "verbum.limit" || response["code"] != "session_limit" {
		t.Fatal(response)
	}
	if next, res, e := websocket.DefaultDialer.Dial("ws"+strings.TrimPrefix(relay.URL, "http"), headers); e == nil {
		next.Close()
		t.Fatal("ticket replay accepted")
	} else if res == nil || res.StatusCode != 401 {
		t.Fatal("wrong replay status", res, e)
	}
	if calls.Load() != 1 {
		t.Fatal("provider reconnected", calls.Load())
	}
	var turns int
	var cost int64
	if e = db.Pool.QueryRow(ctx, `SELECT count(*),COALESCE(sum(charged_micros),0) FROM usage_operations WHERE kind='voice_turn' AND settled`).Scan(&turns, &cost); e != nil {
		t.Fatal(e)
	}
	if turns != 1 || cost != 960 {
		t.Fatal("incorrect settlement", turns, cost)
	}
}
