package postgres_test

import (
	"context"
	"encoding/json"
	"errors"
	"os"
	"reflect"
	"testing"
	"verbum/backend/internal/domain"
	"verbum/backend/internal/seed"
	"verbum/backend/internal/store"
	"verbum/backend/internal/store/memory"
	"verbum/backend/internal/store/postgres"
	"verbum/backend/internal/testdb"
)

func TestFixtureParity(t *testing.T) {
	ctx := context.Background()
	conn, url := testdb.Open(t, "../../../db/migrations")
	path := "../../../db/seed/fixtures.json"
	if err := seed.Load(ctx, conn, path); err != nil {
		t.Fatal(err)
	}
	db, err := postgres.Open(ctx, url)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(db.Close)
	mem, err := memory.Load(path)
	if err != nil {
		t.Fatal(err)
	}
	raw, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	var f seed.Fixtures
	if err = json.Unmarshal(raw, &f); err != nil {
		t.Fatal(err)
	}
	compare := func(label string, want, got any, werr, gerr error) {
		t.Helper()
		if !errors.Is(gerr, werr) || !reflect.DeepEqual(got, want) {
			t.Errorf("%s: got %#v (%v), want %#v (%v)", label, got, gerr, want, werr)
		}
	}
	for _, e := range f.Entities {
		t.Run(e.ID, func(t *testing.T) {
			want, we := mem.Entity(ctx, e.ID)
			got, ge := db.Entity(ctx, e.ID)
			compare("entity", want, got, we, ge)
			wd, we := mem.Detail(ctx, e.ID)
			gd, ge := db.Detail(ctx, e.ID)
			compare("detail", wd, gd, we, ge)
			for _, limit := range []int{1, 12, 24, 48} {
				wg, we := mem.Neighbors(ctx, e.ID, limit)
				gg, ge := db.Neighbors(ctx, e.ID, limit)
				compare("neighbors", wg, gg, we, ge)
			}
			wt, we := mem.Timeline(ctx, e.ID)
			gt, ge := db.Timeline(ctx, e.ID)
			compare("timeline", wt, gt, we, ge)
		})
	}
	for _, kind := range []domain.EntityType{domain.Person, domain.Place, domain.Event, domain.Theme, domain.Passage, domain.Book, domain.Prophecy, domain.OriginalTerm, domain.HistoricalPeriod} {
		want, we := mem.Entities(ctx, kind)
		got, ge := db.Entities(ctx, kind)
		compare("entities", want, got, we, ge)
	}
	for _, q := range []string{"David", " david ", "a", "", "%", "_", "' OR true --", "not present"} {
		want, we := mem.Search(ctx, q)
		got, ge := db.Search(ctx, q)
		compare("search "+q, want, got, we, ge)
	}
	refs := map[domain.PassageReference]bool{{BookID: "Neh", Chapter: 9}: true}
	for _, d := range f.Details {
		for _, p := range d.KeyPassages {
			refs[domain.PassageReference{BookID: p.BookID, Chapter: p.Chapter}] = true
		}
	}
	for p := range refs {
		want, we := mem.Context(ctx, p.BookID, p.Chapter)
		got, ge := db.Context(ctx, p.BookID, p.Chapter)
		compare("context", want, got, we, ge)
	}
	if _, err := db.Entity(ctx, "missing"); !errors.Is(err, store.ErrNotFound) {
		t.Errorf("missing entity: %v", err)
	}
	if _, err := db.Detail(ctx, "missing"); !errors.Is(err, store.ErrNotFound) {
		t.Errorf("missing detail: %v", err)
	}
	if _, err := db.Neighbors(ctx, "missing", 12); !errors.Is(err, store.ErrNotFound) {
		t.Errorf("missing graph: %v", err)
	}
	cancelled, cancel := context.WithCancel(ctx)
	cancel()
	if _, err := db.Search(cancelled, "David"); !errors.Is(err, context.Canceled) {
		t.Errorf("cancelled request: %v", err)
	}
	// Reads must reflect committed data, not a fixture cache loaded on startup.
	if _, err = conn.Exec(ctx, `UPDATE entities SET name='David (updated)' WHERE id='fixture.person.david'`); err != nil {
		t.Fatal(err)
	}
	updated, err := db.Entity(ctx, "fixture.person.david")
	if err != nil || updated.Name != "David (updated)" {
		t.Fatalf("database update not visible: %v %v", updated, err)
	}
}

func TestEmptyDatabase(t *testing.T) {
	_, url := testdb.Open(t, "../../../db/migrations")
	ctx := context.Background()
	db, err := postgres.Open(ctx, url)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(db.Close)
	entities, err := db.Entities(ctx, domain.Person)
	if err != nil || entities == nil || len(entities) != 0 {
		t.Fatalf("empty entities: %v %v", entities, err)
	}
	timeline, err := db.Timeline(ctx, "")
	if err != nil || timeline.Events == nil || timeline.EntityNames == nil {
		t.Fatalf("empty timeline: %v %v", timeline, err)
	}
	pool, err := db.DailyVersePool(ctx)
	if err != nil || pool == nil || len(pool) != 0 {
		t.Fatalf("empty daily pool: %v %v", pool, err)
	}
	if _, err = db.Context(ctx, "1Sam", 17); !errors.Is(err, store.ErrNotFound) {
		t.Fatalf("empty context: %v", err)
	}
}
