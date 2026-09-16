package usage

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"errors"
	"time"
)

type Charge struct {
	ID, UID, Kind string
	Reserved      int64
	Created       time.Time
}
type Operation struct {
	Kind, Key string
	Estimate  int64
	TTL       time.Duration
}
type Summary struct {
	Plan         string           `json:"plan"`
	ResetsAt     time.Time        `json:"resetsAt"`
	Remaining    map[string]int64 `json:"remaining"`
	VoiceSeconds int              `json:"voiceSeconds"`
	Restricted   bool             `json:"restricted"`
}
type Store interface {
	Plan(context.Context, Principal, time.Time) (string, error)
	Reserve(context.Context, Principal, Policy, string, int64, time.Time) (Charge, error)
	Settle(context.Context, Charge, int64, bool) error
	Cached(context.Context, string, time.Time) ([]byte, error)
	PutCache(context.Context, string, []byte, time.Time) error
	Lock(context.Context, string) (func(), error)
	Bind(context.Context, string, string, time.Time) error
	Summary(context.Context, Principal, Policy, time.Time) (Summary, error)
}
type Service struct {
	Store  Store
	Policy Policy
	Now    func() time.Time
}

func New(store Store, policy Policy) *Service {
	return &Service{Store: store, Policy: policy, Now: time.Now}
}
func ID() string {
	var b [24]byte
	if _, err := rand.Read(b[:]); err != nil {
		panic(err)
	}
	return hex.EncodeToString(b[:])
}
func (s *Service) Do(ctx context.Context, op Operation, f func(context.Context) ([]byte, error)) ([]byte, error) {
	if s == nil || s.Store == nil || Identity(ctx).UID == "" {
		return nil, Unavailable()
	}
	if op.Estimate < 0 || op.Estimate > 100_000_000 {
		return nil, Unavailable()
	}
	key := Hash(s.Policy.Revision, op.Kind, op.Key)
	if op.TTL > 0 {
		v, e := s.Store.Cached(ctx, key, s.Now())
		if e != nil {
			return nil, Unavailable()
		}
		if v != nil {
			return v, nil
		}
	}
	unlock, e := s.Store.Lock(ctx, key)
	if e != nil {
		return nil, Unavailable()
	}
	defer unlock()
	if op.TTL > 0 {
		v, e := s.Store.Cached(ctx, key, s.Now())
		if e != nil {
			return nil, Unavailable()
		}
		if v != nil {
			return v, nil
		}
	}
	pendingKey := Hash("pending", key)
	if pending, err := s.Store.Cached(ctx, pendingKey, s.Now()); err != nil {
		return nil, Unavailable()
	} else if pending != nil {
		until, _ := time.Parse(time.RFC3339Nano, string(pending))
		return nil, &Denial{Code: "request_in_progress", RetryAt: until}
	}
	charge, e := s.Store.Reserve(ctx, Identity(ctx), s.Policy, op.Kind, op.Estimate, s.Now())
	if e != nil {
		if IsDenied(e) {
			return nil, e
		}
		return nil, Unavailable()
	}
	// Persist before contacting the provider: a timeout or process crash must not
	// immediately launch another potentially billable generation.
	pendingUntil := s.Now().Add(15 * time.Minute)
	if e = s.Store.PutCache(ctx, pendingKey, []byte(pendingUntil.Format(time.RFC3339Nano)), pendingUntil); e != nil {
		settleCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		_ = s.Store.Settle(settleCtx, charge, 0, false)
		return nil, Unavailable()
	}
	m := &meter{}
	result, err := f(context.WithValue(ctx, meterKey{}, m))
	m.mu.Lock()
	actual := charge.Reserved
	if !m.attempted {
		actual = 0
	} else if m.known {
		actual = m.micros
	}
	m.mu.Unlock()
	// A bounded background context completes accounting even when the caller disconnected.
	settleCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	if e = s.Store.Settle(settleCtx, charge, actual, err == nil); e != nil {
		return nil, Unavailable()
	}
	if err != nil {
		if !m.attempted {
			_ = s.Store.PutCache(settleCtx, pendingKey, []byte{}, s.Now())
		}
		return nil, err
	}
	if op.TTL > 0 {
		if e = s.Store.PutCache(settleCtx, key, result, s.Now().Add(op.TTL)); e != nil {
			return nil, Unavailable()
		}
	}
	_ = s.Store.PutCache(settleCtx, pendingKey, []byte{}, s.Now())
	return result, nil
}
func (s *Service) Bind(ctx context.Context, key, fingerprint string) error {
	if key == "" {
		return nil
	}
	if len(key) > 128 {
		return &Denial{Code: "idempotency_conflict"}
	}
	if s == nil || s.Store == nil {
		return Unavailable()
	}
	if err := s.Store.Bind(ctx, Hash(Identity(ctx).UID, key), fingerprint, s.Now().Add(24*time.Hour)); err != nil {
		if IsDenied(err) {
			return err
		}
		return Unavailable()
	}
	return nil
}
func (s *Service) Status(ctx context.Context) (Summary, error) {
	if s == nil || s.Store == nil {
		return Summary{}, Unavailable()
	}
	v, e := s.Store.Summary(ctx, Identity(ctx), s.Policy, s.Now())
	if e != nil {
		return v, Unavailable()
	}
	return v, nil
}

var ErrMissing = errors.New("not found")
