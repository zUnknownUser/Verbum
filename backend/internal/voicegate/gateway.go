// Package voicegate keeps paid Realtime connections behind server-enforced budgets.
// Mobile receives a one-use Verbum ticket, never a provider credential.
package voicegate

import (
	"context"
	"encoding/json"
	"errors"
	"github.com/gorilla/websocket"
	"net/http"
	"strings"
	"time"
	"verbum/backend/internal/realtime"
	"verbum/backend/internal/usage"
)

type Tickets interface {
	PutTicket(context.Context, string, usage.Principal, time.Time) error
	TakeTicket(context.Context, string, time.Time) (usage.Principal, error)
}
type Gateway struct {
	Usage    *usage.Service
	Tickets  Tickets
	APIKey   string
	Endpoint string
}

func New(s *usage.Service, t Tickets, key string) *Gateway {
	return &Gateway{s, t, key, "wss://api.openai.com/v1/realtime?model=" + realtime.DefaultModel}
}
func (g *Gateway) CreateSession(ctx context.Context, model string, _ time.Duration) (realtime.Session, error) {
	if g == nil || g.Usage == nil || g.Tickets == nil || g.APIKey == "" {
		return realtime.Session{}, usage.Unavailable()
	}
	if model != "" && model != realtime.DefaultModel {
		return realtime.Session{}, usage.Unavailable()
	}
	status, e := g.Usage.Status(ctx)
	if e != nil {
		return realtime.Session{}, e
	}
	if status.Remaining["voice"] == 0 {
		code := "quota_exceeded"
		if status.Plan == "guest" {
			code = "plan_required"
		}
		return realtime.Session{}, &usage.Denial{Code: code, RetryAt: status.ResetsAt}
	}
	if status.Restricted {
		return realtime.Session{}, &usage.Denial{Code: "budget_exhausted", RetryAt: status.ResetsAt}
	}
	token := usage.ID()
	until := time.Now().Add(time.Minute)
	if e = g.Tickets.PutTicket(ctx, usage.Hash(token), usage.Identity(ctx), until); e != nil {
		return realtime.Session{}, usage.Unavailable()
	}
	return realtime.Session{ClientSecret: token, ExpiresAt: until.Unix(), Model: realtime.DefaultModel, RelayPath: "/v1/realtime/connect", MaxDurationSeconds: status.VoiceSeconds}, nil
}
func (g *Gateway) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Cache-Control", "no-store")
	auth := strings.Fields(r.Header.Get("Authorization"))
	if len(auth) != 2 || !strings.EqualFold(auth[0], "Bearer") || len(auth[1]) != 48 {
		http.Error(w, "unauthorized", 401)
		return
	}
	p, e := g.Tickets.TakeTicket(r.Context(), usage.Hash(auth[1]), time.Now())
	if e != nil {
		http.Error(w, "unauthorized", 401)
		return
	}
	ctx := usage.WithPrincipal(r.Context(), p)
	status, e := g.Usage.Status(ctx)
	if e != nil {
		http.Error(w, "temporarily unavailable", 503)
		return
	}
	// One live conversation per UID across replicas. Waiting is bounded to one second.
	lockCtx, cancel := context.WithTimeout(ctx, time.Second)
	unlock, e := g.Usage.Store.Lock(lockCtx, "voice-active/"+p.UID)
	cancel()
	if e != nil {
		http.Error(w, "conversation already active", 429)
		return
	}
	defer unlock()
	charge, e := g.Usage.Store.Reserve(ctx, p, g.Usage.Policy, "voice", 20_000, time.Now())
	if e != nil {
		http.Error(w, "voice limit reached", 429)
		return
	}
	// Reserve transcript cost up front. Unknown/aborted sessions keep this small reserve.
	defer func() {
		c, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		_ = g.Usage.Store.Settle(c, charge, 20_000, true)
	}()
	dialer := websocket.Dialer{HandshakeTimeout: 10 * time.Second}
	upstream, _, e := dialer.DialContext(ctx, g.Endpoint, http.Header{"Authorization": []string{"Bearer " + g.APIKey}})
	if e != nil {
		http.Error(w, "voice unavailable", 503)
		return
	}
	defer upstream.Close()
	upgrader := websocket.Upgrader{HandshakeTimeout: 5 * time.Second}
	peer, e := upgrader.Upgrade(w, r, nil)
	if e != nil {
		return
	}
	defer peer.Close()
	_ = peer.UnderlyingConn().SetDeadline(time.Time{})
	_ = upstream.UnderlyingConn().SetDeadline(time.Time{})
	ctx, cancel = context.WithTimeout(ctx, time.Duration(status.VoiceSeconds)*time.Second)
	defer cancel()
	g.bridge(ctx, p, peer, upstream, status)
}

type frame struct {
	raw    []byte
	err    error
	client bool
}

func (g *Gateway) bridge(ctx context.Context, p usage.Principal, peer, upstream *websocket.Conn, status usage.Summary) {
	frames := make(chan frame, 16)
	done := make(chan struct{})
	defer close(done)
	read := func(c *websocket.Conn, client bool) {
		c.SetReadLimit(128 << 10)
		for {
			kind, b, e := c.ReadMessage()
			if e == nil && kind != websocket.TextMessage {
				e = errors.New("unsupported frame")
			}
			select {
			case frames <- frame{b, e, client}:
			case <-done:
				return
			}
			if e != nil {
				return
			}
		}
	}
	go read(peer, true)
	go read(upstream, false)
	write := func(c *websocket.Conn, v any) error {
		_ = c.SetWriteDeadline(time.Now().Add(5 * time.Second))
		return c.WriteJSON(v)
	}
	limited := func(code string) {
		event := map[string]any{"type": "verbum.limit", "code": code}
		if code == "quota_exceeded" || code == "budget_exhausted" {
			event["retryAt"] = status.ResetsAt
		}
		_ = write(peer, event)
	}
	guard := Guard{Started: time.Now(), Seconds: status.VoiceSeconds}
	var active *usage.Charge
	defer func() {
		if active != nil {
			c, cancel := context.WithTimeout(context.Background(), 5*time.Second)
			defer cancel()
			_ = g.Usage.Store.Settle(c, *active, active.Reserved, false)
		}
	}()
	respond := func(event map[string]any) bool {
		if active != nil {
			return true
		}
		if guard.Responses >= 12 {
			limited("quota_exceeded")
			return false
		}
		c, e := g.Usage.Store.Reserve(ctx, p, g.Usage.Policy, "voice_turn", 250_000, time.Now())
		if e != nil {
			code := "usage_unavailable"
			var d *usage.Denial
			if errors.As(e, &d) {
				code = d.Code
			}
			limited(code)
			return false
		}
		active = &c
		guard.Responses++
		if event == nil {
			event = map[string]any{"type": "response.create"}
		}
		return write(upstream, event) == nil
	}
	for {
		select {
		case <-ctx.Done():
			limited("session_limit")
			return
		case f := <-frames:
			if f.err != nil {
				return
			}
			var event map[string]any
			if json.Unmarshal(f.raw, &event) != nil {
				return
			}
			kind, _ := event["type"].(string)
			if f.client {
				event, e := guard.Client(event)
				if e != nil {
					limited("request_limited")
					return
				}
				if event == nil {
					continue
				}
				if kind == "response.create" {
					if !respond(event) {
						return
					}
					continue
				}
				if write(upstream, event) != nil {
					return
				}
			} else {
				if kind == "input_audio_buffer.committed" {
					if !respond(nil) {
						return
					}
				}
				if kind == "response.done" && active != nil {
					cost, known := responseCost(event)
					if !known {
						cost = active.Reserved
					}
					c, cancel := context.WithTimeout(context.Background(), 5*time.Second)
					e := g.Usage.Store.Settle(c, *active, cost, true)
					cancel()
					active = nil
					if e != nil {
						limited("usage_unavailable")
						return
					}
				}
				if write(peer, event) != nil {
					return
				}
			}
		}
	}
}

// Upper-priced uncached tokens; discounts are deliberately not needed for admission.
func responseCost(event map[string]any) (int64, bool) {
	response, ok := event["response"].(map[string]any)
	if !ok {
		return 0, false
	}
	u, ok := response["usage"].(map[string]any)
	if !ok {
		return 0, false
	}
	number := func(m map[string]any, k string) int64 { v, _ := m[k].(float64); return max(0, int64(v)) }
	in, ok1 := u["input_token_details"].(map[string]any)
	out, ok2 := u["output_token_details"].(map[string]any)
	if !ok1 || !ok2 {
		if _, ok := u["input_tokens"].(float64); !ok {
			return 0, false
		}
		if _, ok := u["output_tokens"].(float64); !ok {
			return 0, false
		}
		return number(u, "input_tokens")*32 + number(u, "output_tokens")*64, true
	}
	return number(in, "text_tokens")*4 + number(in, "audio_tokens")*32 + number(out, "text_tokens")*16 + number(out, "audio_tokens")*64, true
}
