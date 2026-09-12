package dailyverse

import (
	"testing"
	"time"

	"verbum/backend/internal/domain"
	"verbum/backend/internal/store/memory"
)

// The same dates are asserted in the iOS and Android DailyVersesTests.
func TestSameVerseOnEveryPlatform(t *testing.T) {
	s, err := memory.Load("../../db/seed/fixtures.json")
	if err != nil {
		t.Fatal(err)
	}
	pool, _ := s.DailyVersePool(t.Context())
	cases := []struct {
		date       string
		book       string
		chapter, v int
	}{
		{"2026-09-12", "2Tim", 1, 7},
		{"2026-09-13", "Ps", 121, 1},
		{"2027-01-01", "Mark", 10, 27},
	}
	for _, c := range cases {
		d, _ := time.Parse("2006-01-02", c.date)
		got := Pick(pool, EpochDay(d))
		if got.BookID != c.book || got.Chapter != c.chapter || got.VerseStart == nil || *got.VerseStart != c.v {
			t.Errorf("%s: got %s %d:%v, want %s %d:%d", c.date, got.BookID, got.Chapter, got.VerseStart, c.book, c.chapter, c.v)
		}
	}
	if got := Pick(pool, -1); got.BookID != "Eph" || got.Chapter != 2 {
		t.Errorf("epoch day -1: got %s %d", got.BookID, got.Chapter)
	}
}

func TestNoRepeatWithinACycle(t *testing.T) {
	pool := make([]domain.PassageReference, 101)
	for i := range pool {
		pool[i] = domain.PassageReference{BookID: "x", Chapter: i}
	}
	start := int64(205 * 101)
	seen := map[int]bool{}
	for d := start; d < start+101; d++ {
		seen[Pick(pool, d).Chapter] = true
	}
	if len(seen) != 101 {
		t.Errorf("expected 101 distinct verses in a cycle, got %d", len(seen))
	}
}
