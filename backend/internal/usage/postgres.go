package usage

import (
	"context"
	"crypto/sha256"
	"encoding/binary"
	"errors"
	"fmt"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

type Postgres struct{ Pool *pgxpool.Pool }

func Open(ctx context.Context, url string) (*Postgres, error) {
	p, e := pgxpool.New(ctx, url)
	if e != nil {
		return nil, e
	}
	if e = p.Ping(ctx); e != nil {
		p.Close()
		return nil, e
	}
	return &Postgres{p}, nil
}
func (s *Postgres) Close() { s.Pool.Close() }
func (s *Postgres) Ready(ctx context.Context) error {
	var n int
	return s.Pool.QueryRow(ctx, `SELECT count(*) FROM usage_entitlements`).Scan(&n)
}
func (s *Postgres) Plan(ctx context.Context, p Principal, now time.Time) (string, error) {
	if p.Anonymous {
		return "guest", nil
	}
	var plan string
	e := s.Pool.QueryRow(ctx, `SELECT plan FROM usage_entitlements WHERE uid=$1 AND expires_at>$2`, p.UID, now).Scan(&plan)
	if errors.Is(e, pgx.ErrNoRows) {
		return "free", nil
	}
	return plan, e
}

type counter struct {
	subject, bucket string
	start           time.Time
	delta, limit    int64
	code            string
	retry           time.Time
}

func counters(p Principal, pol Policy, l Limits, kind string, cost int64, now time.Time) []counter {
	day := Day(now)
	hour := now.UTC().Truncate(time.Hour)
	minute := now.UTC().Truncate(time.Minute)
	c := []counter{
		{"global", "money", day, cost, pol.GlobalDailyMicros, "budget_exhausted", day.Add(24 * time.Hour)},
		{Hash("uid", p.UID), "minute", minute, 1, 20, "rate_limited", minute.Add(time.Minute)},
	}
	// Standard narration belongs to the shared library, never to a reader's quota.
	if kind != "tts" {
		c = append(c,
			counter{Hash("uid", p.UID), "money", day, cost, l.DailyMicros, "quota_exceeded", day.Add(24 * time.Hour)},
			counter{Hash("uid", p.UID), kind, day, 1, l.Quota(kind), "quota_exceeded", day.Add(24 * time.Hour)},
			counter{Hash("uid", p.UID), "hour", hour, 1, l.Hourly, "rate_limited", hour.Add(time.Hour)},
		)
	}
	if p.IP != "" {
		c = append(c, counter{Hash("ip", p.IP), "minute", minute, 1, 60, "rate_limited", minute.Add(time.Minute)})
		c = append(c, counter{Hash("ip", p.IP), "hour", hour, 1, 300, "rate_limited", hour.Add(time.Hour)})
	}
	if p.Device != "" {
		c = append(c, counter{Hash("device", p.Device), "minute", minute, 1, 40, "rate_limited", minute.Add(time.Minute)})
		c = append(c, counter{Hash("device", p.Device), "hour", hour, 1, 150, "rate_limited", hour.Add(time.Hour)})
	}
	return c
}
func (s *Postgres) Reserve(ctx context.Context, p Principal, pol Policy, kind string, cost int64, now time.Time) (Charge, error) {
	if p.UID == "" || cost < 0 || cost > 100_000_000 {
		return Charge{}, Unavailable()
	}
	plan, e := s.Plan(ctx, p, now)
	if e != nil {
		return Charge{}, e
	}
	l := pol.Limits(plan)
	if kind != "tts" && l.Quota(kind) == 0 {
		return Charge{}, &Denial{Code: "plan_required"}
	}
	tx, e := s.Pool.Begin(ctx)
	if e != nil {
		return Charge{}, e
	}
	defer tx.Rollback(context.Background())
	// Serializes the short reservation/settlement transactions across every API replica.
	// Provider work is never performed inside this transaction.
	if _, e = tx.Exec(ctx, `SELECT pg_advisory_xact_lock(870316001)`); e != nil {
		return Charge{}, e
	}
	for _, c := range counters(p, pol, l, kind, cost, now) {
		_, e = tx.Exec(ctx, `INSERT INTO usage_counters(subject,bucket,starts_at) VALUES($1,$2,$3) ON CONFLICT DO NOTHING`, c.subject, c.bucket, c.start)
		if e != nil {
			return Charge{}, e
		}
		var amount int64
		e = tx.QueryRow(ctx, `SELECT amount FROM usage_counters WHERE subject=$1 AND bucket=$2 AND starts_at=$3 FOR UPDATE`, c.subject, c.bucket, c.start).Scan(&amount)
		if e != nil {
			return Charge{}, e
		}
		if c.limit == 0 || amount > c.limit-c.delta {
			return Charge{}, &Denial{Code: c.code, RetryAt: c.retry}
		}
		_, e = tx.Exec(ctx, `UPDATE usage_counters SET amount=amount+$4 WHERE subject=$1 AND bucket=$2 AND starts_at=$3`, c.subject, c.bucket, c.start, c.delta)
		if e != nil {
			return Charge{}, e
		}
	}
	charge := Charge{ID: ID(), UID: p.UID, Kind: kind, Reserved: cost, Created: now}
	_, e = tx.Exec(ctx, `INSERT INTO usage_operations(id,uid,kind,reserved_micros,charged_micros,created_at) VALUES($1,$2,$3,$4,$4,$5)`, charge.ID, p.UID, kind, cost, now)
	if e != nil {
		return Charge{}, e
	}
	return charge, tx.Commit(ctx)
}
func (s *Postgres) Settle(ctx context.Context, c Charge, actual int64, success bool) error {
	if actual < 0 {
		return fmt.Errorf("invalid actual cost")
	}
	tx, e := s.Pool.Begin(ctx)
	if e != nil {
		return e
	}
	defer tx.Rollback(context.Background())
	if _, e = tx.Exec(ctx, `SELECT pg_advisory_xact_lock(870316001)`); e != nil {
		return e
	}
	var charged int64
	var settled bool
	e = tx.QueryRow(ctx, `SELECT charged_micros,settled FROM usage_operations WHERE id=$1 FOR UPDATE`, c.ID).Scan(&charged, &settled)
	if e != nil {
		return e
	}
	if settled {
		return nil
	}
	// Record overruns too; never hide a pricing mismatch by clamping usage to the estimate.
	subjects := []string{"global"}
	if c.Kind != "tts" {
		subjects = append(subjects, Hash("uid", c.UID))
	}
	for _, subject := range subjects {
		_, e = tx.Exec(ctx, `UPDATE usage_counters SET amount=GREATEST(0,amount+$3) WHERE subject=$1 AND bucket='money' AND starts_at=$2`, subject, Day(c.Created), actual-charged)
		if e != nil {
			return e
		}
	}
	_, e = tx.Exec(ctx, `UPDATE usage_operations SET charged_micros=$2,settled=true,succeeded=$3 WHERE id=$1`, c.ID, actual, success)
	if e != nil {
		return e
	}
	return tx.Commit(ctx)
}
func (s *Postgres) Cached(ctx context.Context, key string, now time.Time) ([]byte, error) {
	var v []byte
	e := s.Pool.QueryRow(ctx, `SELECT payload FROM usage_cache WHERE key=$1 AND expires_at>$2`, key, now).Scan(&v)
	if errors.Is(e, pgx.ErrNoRows) {
		return nil, nil
	}
	return v, e
}
func (s *Postgres) PutCache(ctx context.Context, key string, payload []byte, until time.Time) error {
	if len(payload) > 90<<20 {
		return fmt.Errorf("cache artifact too large")
	}
	_, e := s.Pool.Exec(ctx, `INSERT INTO usage_cache(key,payload,expires_at) VALUES($1,$2,CASE WHEN $3::timestamptz = '0001-01-01 00:00:00+00'::timestamptz THEN 'infinity'::timestamptz ELSE $3 END) ON CONFLICT(key) DO UPDATE SET payload=EXCLUDED.payload,expires_at=EXCLUDED.expires_at,created_at=now()`, key, payload, until)
	return e
}
func (s *Postgres) Lock(ctx context.Context, key string) (func(), error) {
	conn, e := pgx.ConnectConfig(ctx, s.Pool.Config().ConnConfig.Copy())
	if e != nil {
		return nil, e
	}
	h := sha256.Sum256([]byte("usage/" + key))
	id := int64(binary.BigEndian.Uint64(h[:8]))
	timer := time.NewTicker(50 * time.Millisecond)
	defer timer.Stop()
	for {
		var locked bool
		e = conn.QueryRow(ctx, `SELECT pg_try_advisory_lock($1)`, id).Scan(&locked)
		if e != nil {
			conn.Close(context.Background())
			return nil, e
		}
		if locked {
			return func() {
				ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
				defer cancel()
				if _, e := conn.Exec(ctx, `SELECT pg_advisory_unlock($1)`, id); e != nil {
					conn.Close(ctx)
				} else {
					conn.Close(context.Background())
				}
			}, nil
		}
		select {
		case <-ctx.Done():
			conn.Close(context.Background())
			return nil, ctx.Err()
		case <-timer.C:
		}
	}
}
func (s *Postgres) Bind(ctx context.Context, key, fingerprint string, until time.Time) error {
	var got string
	e := s.Pool.QueryRow(ctx, `INSERT INTO usage_idempotency(key,fingerprint,expires_at) VALUES($1,$2,$3)
 ON CONFLICT(key) DO UPDATE SET fingerprint=CASE WHEN usage_idempotency.expires_at<now() THEN EXCLUDED.fingerprint ELSE usage_idempotency.fingerprint END,
 expires_at=CASE WHEN usage_idempotency.expires_at<now() THEN EXCLUDED.expires_at ELSE usage_idempotency.expires_at END RETURNING fingerprint`, key, fingerprint, until).Scan(&got)
	if e != nil {
		return e
	}
	if got != fingerprint {
		return &Denial{Code: "idempotency_conflict"}
	}
	return nil
}
func (s *Postgres) Summary(ctx context.Context, p Principal, pol Policy, now time.Time) (Summary, error) {
	plan, e := s.Plan(ctx, p, now)
	if e != nil {
		return Summary{}, e
	}
	l := pol.Limits(plan)
	v := Summary{StandardNarration: "free", Plan: plan, ResetsAt: Day(now).Add(24 * time.Hour), Remaining: map[string]int64{}, VoiceSeconds: l.VoiceSeconds}
	for _, kind := range []string{"ask", "embedding", "voice"} {
		var count int64
		e = s.Pool.QueryRow(ctx, `SELECT COALESCE((SELECT amount FROM usage_counters WHERE subject=$1 AND bucket=$2 AND starts_at=$3),0)`, Hash("uid", p.UID), kind, Day(now)).Scan(&count)
		if e != nil {
			return v, e
		}
		v.Remaining[kind] = max(0, l.Quota(kind)-count)
	}
	var global, user int64
	e = s.Pool.QueryRow(ctx, `SELECT COALESCE(sum(amount) FILTER(WHERE subject='global'),0),COALESCE(sum(amount) FILTER(WHERE subject=$1),0) FROM usage_counters WHERE bucket='money' AND starts_at=$2`, Hash("uid", p.UID), Day(now)).Scan(&global, &user)
	v.Restricted = global >= pol.GlobalDailyMicros || user >= l.DailyMicros
	return v, e
}

// Cleanup only expired cache/keys/tickets and old ledgers. Outstanding reservations
// stay charged for their original day; a crash never permits the same spend again.
func (s *Postgres) Cleanup(ctx context.Context) error {
	for _, q := range []string{`DELETE FROM usage_cache WHERE expires_at<now()`, `DELETE FROM usage_idempotency WHERE expires_at<now()`, `DELETE FROM usage_voice_tickets WHERE expires_at<now()-interval '1 day'`, `DELETE FROM usage_counters WHERE starts_at<now()-interval '35 days'`, `DELETE FROM usage_operations WHERE created_at<now()-interval '35 days'`} {
		if _, e := s.Pool.Exec(ctx, q); e != nil {
			return e
		}
	}
	return nil
}

func (s *Postgres) PutTicket(ctx context.Context, hash string, p Principal, until time.Time) error {
	_, e := s.Pool.Exec(ctx, `INSERT INTO usage_voice_tickets(token_hash,uid,anonymous,device,ip,expires_at) VALUES($1,$2,$3,$4,$5,$6)`, hash, p.UID, p.Anonymous, p.Device, p.IP, until)
	return e
}
func (s *Postgres) TakeTicket(ctx context.Context, hash string, now time.Time) (Principal, error) {
	var p Principal
	e := s.Pool.QueryRow(ctx, `UPDATE usage_voice_tickets SET consumed=true WHERE token_hash=$1 AND expires_at>$2 AND NOT consumed RETURNING uid,anonymous,device,ip`, hash, now).Scan(&p.UID, &p.Anonymous, &p.Device, &p.IP)
	return p, e
}

// Preserve promotes previously generated audio without rewriting its potentially large payload.
func (s *Postgres) Preserve(ctx context.Context, key string) error {
	_, err := s.Pool.Exec(ctx, `UPDATE usage_cache SET expires_at='infinity' WHERE key=$1 AND expires_at<>'infinity'`, key)
	return err
}
