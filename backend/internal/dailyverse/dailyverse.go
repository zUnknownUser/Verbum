// Package dailyverse picks the verse of a day. It is the same algorithm the
// apps run on device (DailyVerses in Swift and Kotlin), so a server answer and
// an offline answer never disagree: the pool is shuffled once per cycle of
// len(pool) days with a seeded generator, then walked in order. No verse
// repeats within a cycle.
package dailyverse

import (
	"time"

	"verbum/backend/internal/domain"
)

// EpochDay is days since 1970-01-01 for a civil date (java.time.LocalDate.toEpochDay).
func EpochDay(d time.Time) int64 {
	y, m, day := d.Date()
	return int64(time.Date(y, m, day, 0, 0, 0, 0, time.UTC).Unix() / 86400)
}

// Pick returns the verse for an epoch day.
func Pick(pool []domain.PassageReference, epochDay int64) domain.PassageReference {
	n := int64(len(pool))
	cycle := floorDiv(epochDay, n)
	position := int(floorMod(epochDay, n))
	return pool[permutation(len(pool), uint64(cycle))[position]]
}

// permutation is Fisher–Yates over the indices, driven by splitmix64 from seed.
func permutation(count int, seed uint64) []int {
	idx := make([]int, count)
	for i := range idx {
		idx[i] = i
	}
	state := seed
	for i := count - 1; i > 0; i-- {
		state += 0x9E3779B97F4A7C15
		z := state
		z = (z ^ (z >> 30)) * 0xBF58476D1CE4E5B9
		z = (z ^ (z >> 27)) * 0x94D049BB133111EB
		z ^= z >> 31
		j := int(z % uint64(i+1))
		idx[i], idx[j] = idx[j], idx[i]
	}
	return idx
}

func floorDiv(a, b int64) int64 {
	q := a / b
	if (a%b != 0) && ((a < 0) != (b < 0)) {
		q--
	}
	return q
}

func floorMod(a, b int64) int64 { return a - floorDiv(a, b)*b }
