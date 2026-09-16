package usage

import (
	"context"
	"errors"
	"sync"
	"sync/atomic"
	"testing"
	"time"
	"verbum/backend/internal/testdb"
)

func database(t *testing.T) *Postgres {
	t.Helper()
	_, url := testdb.Open(t, "../../db/migrations")
	db, e := Open(context.Background(), url)
	if e != nil {
		t.Fatal(e)
	}
	t.Cleanup(db.Close)
	return db
}
func TestConcurrentGlobalReservationsAndPersistentRestart(t *testing.T) {
	db := database(t)
	ctx := context.Background()
	p := Defaults()
	p.GlobalDailyMicros = 100
	p.Free.DailyMicros = 1000
	now := time.Date(2026, 9, 16, 12, 0, 0, 0, time.UTC)
	var successes atomic.Int64
	var wg sync.WaitGroup
	for i := 0; i < 30; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			_, e := db.Reserve(ctx, Principal{UID: ID()}, p, "ask", 10, now)
			if e == nil {
				successes.Add(1)
			} else if !IsDenied(e) {
				t.Error(e)
			}
		}()
	}
	wg.Wait()
	if successes.Load() != 10 {
		t.Fatalf("overspend or missing reservation: %d", successes.Load())
	}
	restarted := &Postgres{Pool: db.Pool}
	_, e := restarted.Reserve(ctx, Principal{UID: "restart"}, p, "ask", 1, now)
	if !IsDenied(e) {
		t.Fatalf("restart reset budget: %v", e)
	}
	if _, e = db.Reserve(ctx, Principal{UID: "tomorrow"}, p, "ask", 10, now.Add(24*time.Hour)); e != nil {
		t.Fatal(e)
	}
}
func TestQuotasEntitlementsAndSettlementAreIndependent(t *testing.T) {
	db := database(t)
	ctx := context.Background()
	now := time.Now().UTC()
	p := Defaults()
	p.Free.Ask = 1
	who := Principal{UID: "reader"}
	c, e := db.Reserve(ctx, who, p, "ask", 10000, now)
	if e != nil {
		t.Fatal(e)
	}
	if e = db.Settle(ctx, c, 500, true); e != nil {
		t.Fatal(e)
	}
	if e = db.Settle(ctx, c, 0, true); e != nil {
		t.Fatal(e)
	}
	var cost int64
	if e = db.Pool.QueryRow(ctx, `SELECT charged_micros FROM usage_operations WHERE id=$1`, c.ID).Scan(&cost); e != nil || cost != 500 {
		t.Fatalf("settlement is not idempotent: %d %v", cost, e)
	}
	if _, e = db.Reserve(ctx, who, p, "ask", 10, now); !IsDenied(e) {
		t.Fatal("daily quota bypass")
	}
	_, e = db.Pool.Exec(ctx, `INSERT INTO usage_entitlements(uid,plan,expires_at,source) VALUES('reader','premium',$1,'test')`, now.Add(time.Hour))
	if e != nil {
		t.Fatal(e)
	}
	if _, e = db.Reserve(ctx, who, p, "ask", 10, now); e != nil {
		t.Fatal(e)
	}
	if plan, e := db.Plan(ctx, who, now.Add(2*time.Hour)); e != nil || plan != "free" {
		t.Fatal("expired entitlement retained", plan, e)
	}
	if plan, e := db.Plan(ctx, Principal{UID: "reader", Anonymous: true}, now); e != nil || plan != "guest" {
		t.Fatal("guest elevated", plan, e)
	}
}
func TestSharedCacheCoalescesAcrossServicesAndSurvivesQuota(t *testing.T) {
	db := database(t)
	p := Defaults()
	p.Free.Ask = 1
	service := New(db, p)
	other := New(db, p)
	ctx := WithPrincipal(context.Background(), Principal{UID: "reader"})
	op := Operation{Kind: "ask", Key: "same", Estimate: 1000, TTL: time.Hour}
	var calls atomic.Int64
	f := func(ctx context.Context) ([]byte, error) {
		calls.Add(1)
		Attempt(ctx)
		time.Sleep(20 * time.Millisecond)
		Record(ctx, 42)
		return []byte("answer"), nil
	}
	var wg sync.WaitGroup
	for i := 0; i < 8; i++ {
		wg.Add(1)
		go func(i int) {
			defer wg.Done()
			s := service
			if i%2 == 0 {
				s = other
			}
			v, e := s.Do(ctx, op, f)
			if e != nil || string(v) != "answer" {
				t.Errorf("%q %v", v, e)
			}
		}(i)
	}
	wg.Wait()
	if calls.Load() != 1 {
		t.Fatalf("duplicate provider charge: %d", calls.Load())
	}
	if _, e := other.Do(ctx, op, f); e != nil {
		t.Fatal("cache incorrectly gated by quota", e)
	}
	op.Key = "different"
	if _, e := service.Do(ctx, op, f); !IsDenied(e) {
		t.Fatal("quota bypass", e)
	}
}
func TestUnknownProviderFailureKeepsReservation(t *testing.T) {
	db := database(t)
	p := Defaults()
	ctx := WithPrincipal(context.Background(), Principal{UID: "reader"})
	s := New(db, p)
	_, e := s.Do(ctx, Operation{Kind: "ask", Key: "failure", Estimate: 1000}, func(ctx context.Context) ([]byte, error) { Attempt(ctx); return nil, errors.New("timeout") })
	if e == nil {
		t.Fatal("missing provider error")
	}
	var cost int64
	if e = db.Pool.QueryRow(ctx, `SELECT charged_micros FROM usage_operations WHERE uid='reader'`).Scan(&cost); e != nil || cost != 1000 {
		t.Fatal("unknown usage refunded", cost, e)
	}
}
func TestIdempotencyBindingAndSingleUseVoiceTickets(t *testing.T) {
	db := database(t)
	ctx := context.Background()
	until := time.Now().Add(time.Hour)
	if e := db.Bind(ctx, "key", "request-a", until); e != nil {
		t.Fatal(e)
	}
	if e := db.Bind(ctx, "key", "request-a", until); e != nil {
		t.Fatal(e)
	}
	if e := db.Bind(ctx, "key", "request-b", until); !IsDenied(e) {
		t.Fatal("conflicting replay accepted")
	}
	if e := db.PutTicket(ctx, "ticket", Principal{UID: "reader", Device: "installation"}, until); e != nil {
		t.Fatal(e)
	}
	var count atomic.Int64
	var wg sync.WaitGroup
	for i := 0; i < 8; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			if _, e := db.TakeTicket(ctx, "ticket", time.Now()); e == nil {
				count.Add(1)
			}
		}()
	}
	wg.Wait()
	if count.Load() != 1 {
		t.Fatal("ticket replay", count.Load())
	}
}
func TestDeviceAndHourlyLimitsSurviveChangingUID(t *testing.T) {
	db := database(t)
	ctx := context.Background()
	p := Defaults()
	now := time.Now().UTC()
	device := "same-installation"
	_, e := db.Pool.Exec(ctx, `INSERT INTO usage_counters(subject,bucket,starts_at,amount) VALUES($1,'hour',$2,150)`, Hash("device", device), now.Truncate(time.Hour))
	if e != nil {
		t.Fatal(e)
	}
	if _, e = db.Reserve(ctx, Principal{UID: "new-account", Device: device}, p, "ask", 10, now); !IsDenied(e) {
		t.Fatal("device budget bypass")
	}
}

func TestAmbiguousFailureCannotImmediatelyChargeAgain(t *testing.T) {
	db := database(t)
	s := New(db, Defaults())
	ctx := WithPrincipal(context.Background(), Principal{UID: "reader"})
	op := Operation{Kind: "ask", Key: "ambiguous", Estimate: 1000, TTL: time.Hour}
	var calls int
	f := func(ctx context.Context) ([]byte, error) {
		calls++
		Attempt(ctx)
		return nil, errors.New("lost response")
	}
	_, _ = s.Do(ctx, op, f)
	_, err := New(db, Defaults()).Do(ctx, op, f)
	var denial *Denial
	if !errors.As(err, &denial) || denial.Code != "request_in_progress" || denial.RetryAt.IsZero() || calls != 1 {
		t.Fatal("duplicate charge after timeout", err, calls)
	}
}
