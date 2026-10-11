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

func TestThemeCategoryAndLocalizedAliases(t *testing.T) {
	ctx := context.Background()
	conn, url := testdb.Open(t, "../../../db/migrations")
	_, err := conn.Exec(ctx, `INSERT INTO sources(id,citation) VALUES('themes.test','Test');
 INSERT INTO entities(id,type,name) VALUES('anxiety','theme','Anxiety'),('faith','theme','Faith');
 INSERT INTO theme_categories(entity_id,category_id) VALUES('anxiety','emotions'),('faith','with-god');
 INSERT INTO entity_localizations(entity_id,language,source_id,name,aliases) VALUES
 ('anxiety','pt-BR','themes.test','Ansiedade',ARRAY['preocupação']),('anxiety','en','themes.test','Anxiety',ARRAY['worry']);`)
	if err != nil {
		t.Fatal(err)
	}
	db, err := postgres.Open(ctx, url)
	if err != nil {
		t.Fatal(err)
	}
	defer db.Close()
	for _, tc := range []struct {
		lang, query, category string
		count                 int
	}{
		{"pt-BR", "PREOCUPACAO", "emotions", 1}, {"en", "worry", "emotions", 1},
		{"pt-BR", "worry", "emotions", 0}, {"en", "preocupacao", "emotions", 0},
		{"pt-BR", "preocupacao", "with-god", 0}, {"en", "", "with-god", 1},
	} {
		page, e := db.EntityPage(store.WithLanguage(ctx, tc.lang), domain.Theme, store.EntityPageRequest{Query: tc.query, Category: tc.category, Limit: 30})
		if e != nil || len(page.Entities) != tc.count {
			t.Fatalf("%+v: %+v %v", tc, page, e)
		}
	}
}

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

func TestEventPeriodsFilterBeforePagingAndSearch(t *testing.T) {
	ctx := context.Background()
	conn, url := testdb.Open(t, "../../../db/migrations")
	for i := 0; i < 35; i++ {
		id := fmt.Sprintf("event.%02d", i)
		if _, err := conn.Exec(ctx, "INSERT INTO entities(id,type,name) VALUES($1,'event',$2)", id, fmt.Sprintf("Event %02d", i)); err != nil {
			t.Fatal(err)
		}
		if _, err := conn.Exec(ctx, "INSERT INTO event_eras(entity_id,era_id) VALUES($1,'exodus')", id); err != nil {
			t.Fatal(err)
		}
	}
	if _, err := conn.Exec(ctx, `INSERT INTO entities(id,type,name) VALUES('king','event','A king'),('unclassified','event','Unknown');
 INSERT INTO event_eras(entity_id,era_id) VALUES('king','monarchy');
 INSERT INTO sources(id,citation) VALUES('periods.test','Test');
 INSERT INTO entity_localizations(entity_id,language,source_id,name,aliases) VALUES('event.00','pt-BR','periods.test','Êxodo',ARRAY['libertação']);`); err != nil {
		t.Fatal(err)
	}
	db, err := postgres.Open(ctx, url)
	if err != nil {
		t.Fatal(err)
	}
	defer db.Close()
	first, err := db.EntityPage(ctx, domain.Event, store.EntityPageRequest{Category: "exodus", Limit: 30})
	if err != nil || len(first.Entities) != 30 || first.NextOffset == nil || *first.NextOffset != 30 || len(first.Letters) != 1 || first.Letters[0] != "E" {
		t.Fatalf("first: %+v %v", first, err)
	}
	second, err := db.EntityPage(ctx, domain.Event, store.EntityPageRequest{Category: "exodus", Offset: 30, Limit: 30})
	if err != nil || len(second.Entities) != 5 || second.NextOffset != nil || first.Entities[29].ID == second.Entities[0].ID {
		t.Fatalf("second: %+v %v", second, err)
	}
	for _, category := range []string{"exodus", "monarchy"} {
		page, err := db.EntityPage(store.WithLanguage(ctx, "pt-BR"), domain.Event, store.EntityPageRequest{Category: category, Query: "libertacao", Letter: "E", Limit: 30})
		want := 0
		if category == "exodus" {
			want = 1
		}
		if err != nil || len(page.Entities) != want {
			t.Fatalf("localized: %+v %v", page, err)
		}
	}
	all, err := db.EntityPage(ctx, domain.Event, store.EntityPageRequest{Limit: 100})
	if err != nil || len(all.Entities) != 37 {
		t.Fatalf("all: %+v %v", all, err)
	}
}
