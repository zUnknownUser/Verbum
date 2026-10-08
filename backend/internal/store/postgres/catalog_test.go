package postgres_test

import (
	"context"
	"fmt"
	"testing"
	"verbum/backend/internal/domain"
	"verbum/backend/internal/store"
	"verbum/backend/internal/store/postgres"
	"verbum/backend/internal/testdb"
)

func TestCatalogPagesFilterBeforeLimitingAndKeepDuplicateNames(t *testing.T) {
	ctx := context.Background()
	conn, url := testdb.Open(t, "../../../db/migrations")
	for i := 0; i < 65; i++ {
		_, err := conn.Exec(ctx, "INSERT INTO entities(id,type,name) VALUES($1,'person',$2)", fmt.Sprintf("person.%03d", i), fmt.Sprintf("Ábel %03d", i))
		if err != nil {
			t.Fatal(err)
		}
	}
	_, err := conn.Exec(ctx, `INSERT INTO entities(id,type,name) VALUES ('john.1','person','João'),('john.2','person','João'),('place','place','João'),('symbol','person','123'),('greek','person','Ωμέγα')`)
	if err != nil {
		t.Fatal(err)
	}
	db, err := postgres.Open(ctx, url)
	if err != nil {
		t.Fatal(err)
	}
	defer db.Close()
	seen := map[string]bool{}
	offset := 0
	for {
		page, err := db.EntityPage(ctx, domain.Person, store.EntityPageRequest{Limit: 30, Offset: offset})
		if err != nil {
			t.Fatal(err)
		}
		if len(page.Entities) > 30 {
			t.Fatal("unbounded page")
		}
		for _, e := range page.Entities {
			if seen[e.ID] {
				t.Fatal("duplicate", e.ID)
			}
			seen[e.ID] = true
		}
		if page.NextOffset == nil {
			break
		}
		offset = *page.NextOffset
	}
	if len(seen) != 69 {
		t.Fatalf("got %d rows", len(seen))
	}
	for _, q := range []string{"joao", "JOÃO", "  João  "} {
		page, err := db.EntityPage(ctx, domain.Person, store.EntityPageRequest{Query: q, Letter: "J", Limit: 1})
		if err != nil {
			t.Fatal(err)
		}
		if len(page.Entities) != 1 || page.NextOffset == nil || *page.NextOffset != 1 || len(page.Letters) != 1 || page.Letters[0] != "J" {
			t.Fatalf("bad page: %+v", page)
		}
	}
	if _, err := conn.Exec(ctx, `INSERT INTO sources(id,citation) VALUES ('catalog.test','Test fixture');
    INSERT INTO entity_localizations(entity_id,language,source_id,name,description)
    VALUES ('john.1','pt-BR','catalog.test','João localizado','Descrição localizada')`); err != nil {
		t.Fatal(err)
	}
	localized, err := db.EntityPage(store.WithLanguage(ctx, "pt-BR"), domain.Person, store.EntityPageRequest{Query: "joao", Letter: "J", Limit: 30})
	if err != nil || len(localized.Entities) != 1 || localized.Entities[0].Name != "João localizado" || localized.Entities[0].Summary == nil || *localized.Entities[0].Summary != "Descrição localizada" {
		t.Fatalf("localized filter: %+v %v", localized, err)
	}

	page, err := db.EntityPage(ctx, domain.Person, store.EntityPageRequest{Letter: "A", Limit: 30})
	if err != nil || len(page.Entities) != 30 || page.Entities[0].Name != "Ábel 000" {
		t.Fatalf("accent index: %+v %v", page, err)
	}
	page, err = db.EntityPage(ctx, domain.Person, store.EntityPageRequest{Query: "missing", Limit: 30})
	if err != nil || len(page.Entities) != 0 || page.NextOffset != nil {
		t.Fatalf("empty: %+v %v", page, err)
	}
	page, err = db.EntityPage(ctx, domain.Person, store.EntityPageRequest{Offset: 1000, Limit: 30})
	if err != nil || len(page.Entities) != 0 || page.NextOffset != nil {
		t.Fatalf("past end: %+v %v", page, err)
	}
}
