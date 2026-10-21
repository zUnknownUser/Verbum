package postgres_test

import (
	"context"
	"testing"

	"verbum/backend/internal/domain"
	"verbum/backend/internal/store"
	"verbum/backend/internal/store/postgres"
	"verbum/backend/internal/testdb"
)

func TestProfileTranslationFallbackAndSearchPreserveExistingContent(t *testing.T) {
	ctx := context.Background()
	conn, url := testdb.Open(t, "../../../db/migrations")
	_, err := conn.Exec(ctx, `
 INSERT INTO entities(id,type,name,summary) VALUES ('person.test','person','Original','Original summary');
 INSERT INTO sources(id,citation) VALUES ('profile.source','Editorial'),('legacy.source','Legacy');
 INSERT INTO entity_details(entity_id,role) VALUES ('person.test','Original role');
 INSERT INTO entity_localizations(entity_id,language,source_id,name,description,fields)
 VALUES ('person.test','pt-BR','legacy.source','Nome anterior','Resumo anterior','{"role":"Função anterior"}');
 INSERT INTO entity_profiles VALUES ('person.test','{"en":{"name":"Curated","summary":"English study","aliases":["New alias"],"role":"English role","modernGeography":null}}');
 INSERT INTO entity_profile_sources VALUES ('person.test','profile.source',0);
 INSERT INTO entity_profile_passages VALUES ('person.test',0,'Exod',2,1,10);
 INSERT INTO entity_key_passages VALUES ('person.test',0,'Exod',3,1,12);
 `)
	if err != nil {
		t.Fatal(err)
	}
	db, err := postgres.Open(ctx, url)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(db.Close)
	en, err := db.Detail(ctx, "person.test")
	if err != nil || en.Entity.Name != "Curated" || len(en.KeyPassages) != 2 {
		t.Fatalf("profile plus existing readings: %+v %v", en, err)
	}
	pt := store.WithLanguage(ctx, "pt-BR")
	page, err := db.Detail(pt, "person.test")
	if err != nil || page.Entity.Name != "Nome anterior" || page.Role == nil || *page.Role != "Função anterior" || len(page.KeyPassages) != 1 || len(page.Sources) != 1 || page.Sources[0].ID != "legacy.source" {
		t.Fatalf("missing translation must retain the existing language without English: %+v %v", page, err)
	}
	matches, err := db.Search(ctx, "New alias")
	if err != nil || len(matches) != 1 || matches[0].ID != "person.test" {
		t.Fatalf("profile alias search: %+v %v", matches, err)
	}
	catalog, err := db.EntityPage(ctx, domain.Person, store.EntityPageRequest{Query: "new alias", Limit: 30})
	if err != nil || len(catalog.Entities) != 1 {
		t.Fatalf("profile catalog search: %+v %v", catalog, err)
	}
	sources, err := db.EntitySources(ctx, []string{"person.test"})
	if err != nil || len(sources) != 1 || sources[0].ID != "profile.source" {
		t.Fatalf("profile evidence: %+v %v", sources, err)
	}
	sources, err = db.EntitySources(pt, []string{"person.test"})
	if err != nil || len(sources) != 0 {
		t.Fatalf("unused English evidence leaked: %+v %v", sources, err)
	}
}
