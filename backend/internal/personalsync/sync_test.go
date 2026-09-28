package personalsync

import (
	"context"
	"errors"
	"fmt"
	"testing"
	"time"
	"verbum/backend/internal/testdb"
)

func TestMergeIndependentFieldsAndConcurrentNotes(t *testing.T) {
	base := &Value{Book: "Job", Chapter: 38, Verse: 4, Note: "original"}
	local := *base
	local.Note = "device A"
	local.Bookmarked = true
	remote := *base
	remote.Note = "device B"
	remote.Highlight = "gold"
	got := Merge("annotation:Job.38.4", base, &local, &remote, true)
	if got.Note != "device B\n\n---\n\ndevice A" || !got.Bookmarked || got.Highlight != "gold" {
		t.Fatalf("lost edit: %+v", got)
	}
	again := Merge("annotation:Job.38.4", base, &local, got, true)
	if again.Note != got.Note {
		t.Fatal("retry duplicated note")
	}
	if Merge("annotation:Job.38.4", base, nil, &remote, true) != nil {
		t.Fatal("deletion must win")
	}
}
func TestOldOfflinePositionDoesNotReplaceNewerVisit(t *testing.T) {
	base := &Value{Book: "Job", Chapter: 1, Time: 10}
	old := &Value{Book: "Job", Chapter: 2, Time: 20}
	recent := &Value{Book: "John", Chapter: 3, Time: 30}
	if got := Merge("position:last", base, old, recent, true); got != recent {
		t.Fatal("old reading replaced newer position")
	}
}
func TestClearingNoteDoesNotDiscardConcurrentEdit(t *testing.T) {
	base := &Value{Book: "Job", Chapter: 38, Verse: 4, Note: "original"}
	local := *base
	local.Note = ""
	remote := *base
	remote.Note = "edited elsewhere"
	if got := Merge("annotation:Job.38.4", base, &local, &remote, true); got.Note != remote.Note {
		t.Fatal("concurrent note lost")
	}
	if got := Merge("annotation:Job.38.4", base, &local, base, true); got.Note != "" {
		t.Fatal("intentional clear ignored")
	}
}
func TestValidate(t *testing.T) {
	now := time.Now()
	valid := Change{ID: "annotation:Job.38.4", MutationID: "01234567-89ab-cdef-0123", Value: &Value{Book: "Job", Chapter: 38, Verse: 4, Note: "Onde estavas?"}}
	if err := Validate(Request{Changes: []Change{valid}}, now); err != nil {
		t.Fatal(err)
	}
	for _, id := range []string{"annotation:Unknown.1.1", "annotation:Job.99.4", "annotation:Job.38.0", "day:2026-13-01", "position:other"} {
		bad := valid
		bad.ID = id
		if Validate(Request{Changes: []Change{bad}}, now) == nil {
			t.Errorf("accepted %s", id)
		}
	}
	if Validate(Request{Changes: []Change{{ID: "day:2026-09-28", MutationID: valid.MutationID}}}, now) == nil {
		t.Fatal("accepted history deletion")
	}
	other := valid
	other.ID = "annotation:Job.38.5"
	other.Value = &Value{Book: "Job", Chapter: 38, Verse: 5, Note: "second"}
	if Validate(Request{Changes: []Change{valid, other}}, now) == nil {
		t.Fatal("accepted reused mutation ID in batch")
	}
}
func TestPostgresIsolationRetryPaginationDeletion(t *testing.T) {
	_, url := testdb.Open(t, "../../db/migrations")
	ctx := context.Background()
	s, err := Open(ctx, url)
	if err != nil {
		t.Fatal(err)
	}
	defer s.Close()
	change := Change{ID: "annotation:Job.38.4", MutationID: "operation-one-0001", Value: &Value{Book: "Job", Chapter: 38, Verse: 4, Note: "first"}}
	first, err := s.Exchange(ctx, "alice", Request{Changes: []Change{change}})
	if err != nil {
		t.Fatal(err)
	}
	bob, err := s.Exchange(ctx, "bob", Request{})
	if err != nil || len(bob.Records) != 0 {
		t.Fatal("cross-account leak", err)
	}
	retry, err := s.Exchange(ctx, "alice", Request{Changes: []Change{change}})
	if err != nil || retry.Cursor != first.Cursor {
		t.Fatal("retry changed revision", err)
	}
	// Concurrent deletion, then a stale initial import from a second device.
	del := Change{ID: change.ID, MutationID: "operation-delete-0001", Base: change.Value, BaseRevision: first.Cursor}
	deleted, err := s.Exchange(ctx, "alice", Request{Changes: []Change{del}})
	if err != nil {
		t.Fatal(err)
	}
	stale := change
	stale.MutationID = "operation-stale-0001"
	result, err := s.Exchange(ctx, "alice", Request{Changes: []Change{stale}})
	if err != nil || result.Accepted[0].Value != nil {
		t.Fatal("resurrected deletion", err)
	}
	// An intentional edit after seeing the deletion can create a new annotation.
	stale.BaseRevision = deleted.Cursor
	stale.MutationID = "operation-recreate-01"
	result, err = s.Exchange(ctx, "alice", Request{Changes: []Change{stale}})
	if err != nil || result.Accepted[0].Value == nil {
		t.Fatal("explicit recreation failed", err)
	}
	for batch := 0; batch < 3; batch++ {
		changes := []Change{}
		for i := 0; i < 80; i++ {
			day := time.Date(2020, 1, 1, 0, 0, 0, 0, time.UTC).AddDate(0, 0, batch*80+i).Format("2006-01-02")
			changes = append(changes, Change{ID: "day:" + day, MutationID: fmt.Sprintf("day-operation-%04d", batch*80+i), Value: &Value{Day: day}})
		}
		if _, err = s.Exchange(ctx, "alice", Request{Changes: changes}); err != nil {
			t.Fatal(err)
		}
	}
	page, err := s.Exchange(ctx, "alice", Request{})
	if err != nil || !page.More || len(page.Records) != PageSize {
		t.Fatal("pagination", err)
	}
	next, err := s.Exchange(ctx, "alice", Request{Cursor: page.Cursor})
	if err != nil || next.More || len(next.Records) != 41 {
		t.Fatal("pagination missing records", len(next.Records), err)
	}
	if err = s.Delete(ctx, "alice"); err != nil {
		t.Fatal(err)
	}
	if err = s.Delete(ctx, "alice"); err != nil {
		t.Fatal("delete retry", err)
	}
	if _, err = s.Exchange(ctx, "alice", Request{Changes: []Change{change}}); !errors.Is(err, ErrDeleted) {
		t.Fatal("write after deletion", err)
	}
}
