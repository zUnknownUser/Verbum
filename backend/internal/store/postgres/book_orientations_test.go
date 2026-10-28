package postgres_test

import (
	"context"
	"errors"
	"testing"
	"verbum/backend/internal/store"
	"verbum/backend/internal/store/postgres"
	"verbum/backend/internal/testdb"
)

func TestBookOrientationRespectsLanguageAndChapterBounds(t *testing.T) {
	ctx := context.Background()
	conn, url := testdb.Open(t, "../../../db/migrations")
	_, err := conn.Exec(ctx, `INSERT INTO sources(id,citation) VALUES ('book.source','Book text');
 INSERT INTO book_orientations VALUES ('Heb',13,'{"en":{"genre":"Exhortation"}}');
 INSERT INTO book_orientation_sources VALUES ('Heb','book.source',0);`)
	if err != nil {
		t.Fatal(err)
	}
	db, err := postgres.Open(ctx, url)
	if err != nil {
		t.Fatal(err)
	}
	defer db.Close()
	for _, chapter := range []int{1, 13} {
		page, err := db.Context(ctx, "Heb", chapter)
		if err != nil || page.BookOrientation == nil || page.Study != nil || len(page.Sources) != 1 {
			t.Fatalf("book guide lost or mistaken for chapter study: %+v %v", page, err)
		}
	}
	for _, chapter := range []int{0, 14} {
		if _, err := db.Context(ctx, "Heb", chapter); !errors.Is(err, store.ErrNotFound) {
			t.Fatalf("invalid chapter: %v", err)
		}
	}
	if _, err := db.Context(store.WithLanguage(ctx, "pt-BR"), "Heb", 1); !errors.Is(err, store.ErrNotFound) {
		t.Fatalf("must not borrow English: %v", err)
	}
}
