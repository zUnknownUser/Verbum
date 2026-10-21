package postgres_test

import (
	"context"
	"errors"
	"testing"

	"verbum/backend/internal/store"
	"verbum/backend/internal/store/postgres"
	"verbum/backend/internal/testdb"
)

func TestChapterStudyDoesNotBorrowMissingTranslationOrLoseLegacyContext(t *testing.T) {
	ctx := context.Background()
	conn, url := testdb.Open(t, "../../../db/migrations")
	_, err := conn.Exec(ctx, `
 INSERT INTO sources(id,citation) VALUES ('study.source','Editorial evidence');
 INSERT INTO chapter_studies(book_id,chapter,presentations)
 VALUES ('Exod',2,'{"en":{"title":"English only"}}');
 INSERT INTO chapter_study_sources VALUES ('Exod',2,'study.source',0);
 `)
	if err != nil {
		t.Fatal(err)
	}
	db, err := postgres.Open(ctx, url)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(db.Close)
	page, err := db.Context(ctx, "Exod", 2)
	if err != nil || page.Study == nil || len(page.Sources) != 1 || len(page.Entities) != 0 {
		t.Fatalf("standalone chapter study unavailable: %+v, %v", page, err)
	}
	pt := store.WithLanguage(ctx, "pt-BR")
	if _, err := db.Context(pt, "Exod", 2); !errors.Is(err, store.ErrNotFound) {
		t.Fatalf("missing translation must not silently display English: %v", err)
	}
	_, err = conn.Exec(ctx, `
 INSERT INTO sources(id,citation) VALUES ('entity.source','Entity evidence');
 INSERT INTO entities(id,type,name) VALUES ('person.test','person','Person');
 INSERT INTO entity_key_passages(entity_id,position,book_id,chapter) VALUES ('person.test',0,'Exod',2);
 INSERT INTO entity_detail_sources(entity_id,source_id) VALUES ('person.test','entity.source');
 `)
	if err != nil {
		t.Fatal(err)
	}
	page, err = db.Context(pt, "Exod", 2)
	if err != nil || page.Study != nil || len(page.Entities) != 1 || len(page.Sources) != 1 || page.Sources[0].ID != "entity.source" {
		t.Fatalf("missing study must preserve legacy context without unused attribution: %+v, %v", page, err)
	}
}
