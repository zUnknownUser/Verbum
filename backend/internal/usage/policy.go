// Package usage enforces persistent entitlements, quotas and conservative cost reservations.
// USD amounts are integer microdollars; no floating point accounting or client-supplied plan.
package usage

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"os"
	"strconv"
	"sync"
	"time"
)

type Principal struct {
	UID       string
	Anonymous bool
	Device    string
	IP        string
}
type principalKey struct{}

func WithPrincipal(ctx context.Context, p Principal) context.Context {
	return context.WithValue(ctx, principalKey{}, p)
}
func Identity(ctx context.Context) Principal { p, _ := ctx.Value(principalKey{}).(Principal); return p }
func Hash(parts ...string) string {
	h := sha256.New()
	for _, p := range parts {
		fmt.Fprintf(h, "%d:%s", len(p), p)
	}
	return hex.EncodeToString(h.Sum(nil))
}

type Denial struct {
	Code    string
	RetryAt time.Time
}

func (e *Denial) Error() string { return e.Code }
func IsDenied(err error) bool   { var d *Denial; return errors.As(err, &d) }
func Unavailable() error        { return &Denial{Code: "usage_unavailable"} }

type Limits struct {
	Ask, Audio, Embedding, Voice, Hourly int64
	DailyMicros                          int64
	VoiceSeconds                         int
}
type Policy struct {
	GlobalDailyMicros    int64
	Guest, Free, Premium Limits
	Revision             string
}

func Defaults() Policy {
	return Policy{
		GlobalDailyMicros: 5_000_000, Revision: "cost-v1",
		Guest:   Limits{Ask: 3, Audio: 1, Embedding: 20, Voice: 0, Hourly: 20, DailyMicros: 100_000, VoiceSeconds: 60},
		Free:    Limits{Ask: 10, Audio: 3, Embedding: 60, Voice: 1, Hourly: 40, DailyMicros: 500_000, VoiceSeconds: 60},
		Premium: Limits{Ask: 100, Audio: 20, Embedding: 300, Voice: 5, Hourly: 120, DailyMicros: 3_000_000, VoiceSeconds: 180},
	}
}
func FromEnvironment() (Policy, error) {
	p := Defaults()
	vars := map[string]*int64{"VERBUM_DAILY_BUDGET_MICROS": &p.GlobalDailyMicros}
	for name, l := range map[string]*Limits{"GUEST": &p.Guest, "FREE": &p.Free, "PREMIUM": &p.Premium} {
		for field, v := range map[string]*int64{"ASK": &l.Ask, "AUDIO": &l.Audio, "EMBEDDING": &l.Embedding, "VOICE": &l.Voice, "HOURLY": &l.Hourly, "DAILY_MICROS": &l.DailyMicros} {
			vars["VERBUM_"+name+"_"+field] = v
		}
	}
	for name, dst := range vars {
		if raw, ok := os.LookupEnv(name); ok {
			v, e := strconv.ParseInt(raw, 10, 64)
			if e != nil || v < 0 || v > 1_000_000_000 {
				return p, fmt.Errorf("invalid %s", name)
			}
			*dst = v
		}
	}
	if v := os.Getenv("VERBUM_CONTENT_REVISION"); v != "" {
		p.Revision = v
	}
	return p, nil
}
func (p Policy) Limits(plan string) Limits {
	if plan == "guest" {
		return p.Guest
	}
	if plan == "premium" {
		return p.Premium
	}
	return p.Free
}
func (l Limits) Quota(kind string) int64 {
	switch kind {
	case "ask":
		return l.Ask
	case "tts":
		return l.Audio
	case "embedding":
		return l.Embedding
	case "voice":
		return l.Voice
	case "voice_turn":
		return l.Voice * 12
	}
	return 0
}
func Day(t time.Time) time.Time {
	t = t.UTC()
	return time.Date(t.Year(), t.Month(), t.Day(), 0, 0, 0, 0, time.UTC)
}

// Meter is scoped to exactly one reservation. Unknown provider outcomes keep the full
// reservation: a timeout or process crash is never assumed to be free.
type meter struct {
	mu        sync.Mutex
	attempted bool
	known     bool
	micros    int64
}
type meterKey struct{}

func Attempt(ctx context.Context) {
	if m, ok := ctx.Value(meterKey{}).(*meter); ok {
		m.mu.Lock()
		m.attempted = true
		m.known = false
		m.mu.Unlock()
	}
}
func Record(ctx context.Context, micros int64) {
	if m, ok := ctx.Value(meterKey{}).(*meter); ok && micros >= 0 {
		m.mu.Lock()
		m.micros += micros
		m.known = true
		m.mu.Unlock()
	}
}
