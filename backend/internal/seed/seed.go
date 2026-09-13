// Package seed imports the explicitly labelled development fixtures. It is not
// the editorial publisher and must never be used to replace production content.
package seed

import (
	"context"
	"encoding/json"
	"fmt"
	"github.com/jackc/pgx/v5"
	"os"
	"verbum/backend/internal/domain"
)

type Fixtures struct {
	Sources        []domain.SourceReference  `json:"sources"`
	Entities       []domain.Entity           `json:"entities"`
	Relationships  []domain.Relationship     `json:"relationships"`
	Details        []domain.EntityDetail     `json:"details"`
	Timeline       []domain.TimelineEvent    `json:"timeline"`
	DailyVersePool []domain.PassageReference `json:"dailyVersePool"`
}

// Load is atomic and only accepts an empty content database. Re-running it
// fails without altering anything; reviewed content is never overwritten.
func Load(ctx context.Context, conn *pgx.Conn, path string) error {
	raw, err := os.ReadFile(path)
	if err != nil {
		return err
	}
	var f Fixtures
	if err = json.Unmarshal(raw, &f); err != nil {
		return fmt.Errorf("parse fixtures: %w", err)
	}
	if len(f.Entities) == 0 || len(f.Sources) == 0 || len(f.DailyVersePool) == 0 {
		return fmt.Errorf("fixtures must contain entities, sources and a daily verse pool")
	}
	tx, err := conn.Begin(ctx)
	if err != nil {
		return err
	}
	defer tx.Rollback(context.Background())
	// Also serializes two seed commands, before checking for existing content.
	if _, err = tx.Exec(ctx, `LOCK TABLE sources, entities, timeline_events, daily_verse_pool IN EXCLUSIVE MODE`); err != nil {
		return err
	}
	var occupied bool
	if err = tx.QueryRow(ctx, `SELECT EXISTS(SELECT 1 FROM sources) OR EXISTS(SELECT 1 FROM entities) OR EXISTS(SELECT 1 FROM timeline_events) OR EXISTS(SELECT 1 FROM daily_verse_pool)`).Scan(&occupied); err != nil {
		return err
	}
	if occupied {
		return fmt.Errorf("seed requires an empty content database; existing content was not changed")
	}
	batch := &pgx.Batch{}
	for i, s := range f.Sources {
		batch.Queue(`INSERT INTO sources(id,citation,url,position) VALUES($1,$2,$3,$4)`, s.ID, s.Citation, s.URL, i)
	}
	for i, e := range f.Entities {
		batch.Queue(`INSERT INTO entities(id,type,name,summary,position) VALUES($1,$2,$3,$4,$5)`, e.ID, e.Type, e.Name, e.Summary, i)
	}
	for _, d := range f.Details {
		batch.Queue(`INSERT INTO entity_details(entity_id,approximate_dates,role,modern_geography) VALUES($1,$2,$3,$4)`, d.Entity.ID, d.ApproximateDates, d.Role, d.ModernGeography)
		for i, a := range d.Aliases {
			batch.Queue(`INSERT INTO entity_aliases(entity_id,alias,position) VALUES($1,$2,$3)`, d.Entity.ID, a, i)
		}
		for i, p := range d.KeyPassages {
			batch.Queue(`INSERT INTO entity_key_passages(entity_id,position,book_id,chapter,verse_start,verse_end) VALUES($1,$2,$3,$4,$5,$6)`, d.Entity.ID, i, p.BookID, p.Chapter, p.VerseStart, p.VerseEnd)
		}
		for i, s := range d.Sources {
			batch.Queue(`INSERT INTO entity_detail_sources(entity_id,source_id,position) VALUES($1,$2,$3)`, d.Entity.ID, s.ID, i)
		}
	}
	for i, r := range f.Relationships {
		if len(r.SourceReferenceIDs) == 0 {
			return fmt.Errorf("relationship %s has no source", r.ID)
		}
		batch.Queue(`INSERT INTO relationships(id,source_entity_id,target_entity_id,relationship_type,confidence,position) VALUES($1,$2,$3,$4,$5,$6)`, r.ID, r.SourceID, r.TargetID, r.Type, r.Confidence, i)
		for j, id := range r.SourceReferenceIDs {
			batch.Queue(`INSERT INTO relationship_sources(relationship_id,source_id,position) VALUES($1,$2,$3)`, r.ID, id, j)
		}
	}
	for i, t := range f.Timeline {
		if len(t.SourceReferenceIDs) == 0 {
			return fmt.Errorf("timeline event %s has no source", t.ID)
		}
		batch.Queue(`INSERT INTO timeline_events(id,title,start_year,end_year,date_precision,summary,position) VALUES($1,$2,$3,$4,$5,$6,$7)`, t.ID, t.Title, t.StartYear, t.EndYear, t.DatePrecision, t.Summary, i)
		for j, id := range t.EntityIDs {
			batch.Queue(`INSERT INTO timeline_event_entities(event_id,entity_id,position) VALUES($1,$2,$3)`, t.ID, id, j)
		}
		for j, id := range t.SourceReferenceIDs {
			batch.Queue(`INSERT INTO timeline_event_sources(event_id,source_id,position) VALUES($1,$2,$3)`, t.ID, id, j)
		}
	}
	for i, p := range f.DailyVersePool {
		batch.Queue(`INSERT INTO daily_verse_pool(position,book_id,chapter,verse_start,verse_end) VALUES($1,$2,$3,$4,$5)`, i, p.BookID, p.Chapter, p.VerseStart, p.VerseEnd)
	}
	if err = tx.SendBatch(ctx, batch).Close(); err != nil {
		return fmt.Errorf("insert fixtures: %w", err)
	}
	return tx.Commit(ctx)
}
