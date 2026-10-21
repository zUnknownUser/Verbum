package postgres_test

import (
	"context"
	"slices"
	"testing"

	"verbum/backend/internal/domain"
	"verbum/backend/internal/store"
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

func TestSourcesAttributeOnlyTheDisplayedLocalization(t *testing.T) {
	ctx := context.Background()
	conn, url := testdb.Open(t, "../../../db/migrations")
	_, err := conn.Exec(ctx, `
 INSERT INTO sources(id,citation) VALUES
 ('scripture.test','Biblical reference'),('editorial.current','Current bilingual prose'),
 ('verbum.localization.pt-BR','Old translated prose');
 INSERT INTO entities(id,type,name) VALUES ('event.test','event','Event');
 INSERT INTO entity_key_passages(entity_id,position,book_id,chapter) VALUES ('event.test',0,'Exod',14);
 INSERT INTO entity_detail_sources(entity_id,source_id) VALUES ('event.test','scripture.test');
 INSERT INTO entity_localizations(entity_id,language,source_id,name,description) VALUES
 ('event.test','en','editorial.current','Current event','Current English prose'),
 ('event.test','pt-BR','editorial.current','Evento atual','Texto atual'),
 ('event.test','pt-BR','verbum.localization.pt-BR','Evento antigo','Texto antigo');
 `)
	if err != nil {
		t.Fatal(err)
	}
	db, err := postgres.Open(ctx, url)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(db.Close)
	for _, language := range []string{"en", "pt-BR"} {
		localized := store.WithLanguage(ctx, language)
		detail, err := db.Detail(localized, "event.test")
		if err != nil {
			t.Fatal(err)
		}
		page, err := db.Context(localized, "Exod", 14)
		if err != nil {
			t.Fatal(err)
		}
		for _, sources := range [][]string{sourceIDs(detail.Sources), sourceIDs(page.Sources)} {
			if len(sources) != 2 || !slices.Contains(sources, "editorial.current") || !slices.Contains(sources, "scripture.test") {
				t.Fatalf("%s attribution includes unused translations or omits evidence: %v", language, sources)
			}
		}
	}
}

func sourceIDs(sources []domain.SourceReference) []string {
	ids := make([]string, len(sources))
	for i, source := range sources {
		ids[i] = source.ID
	}
	return ids
}
