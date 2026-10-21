package postgres_test

import (
	"context"
	"testing"

	"verbum/backend/internal/store/postgres"
	"verbum/backend/internal/testdb"
)

func TestDetailDoesNotInventEditorialSource(t *testing.T) {
	ctx := context.Background()
	conn, url := testdb.Open(t, "../../../db/migrations")
	_, err := conn.Exec(ctx, `
		INSERT INTO sources (id,citation) VALUES
			('fixture.source.editorial','Demonstration source'),
			('test.actual.source','Actual linked source');
		INSERT INTO entities (id,type,name,summary) VALUES
			('test.unsourced','person','Test person','Test summary');
	`)
	if err != nil {
		t.Fatal(err)
	}
	db, err := postgres.Open(ctx, url)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(db.Close)
	detail, err := db.Detail(ctx, "test.unsourced")
	if err != nil {
		t.Fatal(err)
	}
	if detail.Sources == nil || len(detail.Sources) != 0 {
		t.Fatalf("unsourced entity must return an empty source list: %v", detail.Sources)
	}
	if _, err := conn.Exec(ctx, `
		INSERT INTO entity_sources (entity_id,source_id,position)
		VALUES ('test.unsourced','test.actual.source',0)
	`); err != nil {
		t.Fatal(err)
	}
	detail, err = db.Detail(ctx, "test.unsourced")
	if err != nil {
		t.Fatal(err)
	}
	if len(detail.Sources) != 1 || detail.Sources[0].ID != "test.actual.source" {
		t.Fatalf("explicit source association was not preserved: %v", detail.Sources)
	}
}
