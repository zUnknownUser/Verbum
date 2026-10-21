package postgres_test

import (
	"context"
	"testing"

	"verbum/backend/internal/store"
	"verbum/backend/internal/store/postgres"
	"verbum/backend/internal/testdb"
)

func TestThemeStudyUsesRequestedLanguageAndPreservesLegacyReadings(t *testing.T) {
	ctx := context.Background()
	conn, url := testdb.Open(t, "../../../db/migrations")
	_, err := conn.Exec(ctx, `
 INSERT INTO sources(id,citation) VALUES ('study.source','Study evidence'),('legacy.source','Legacy evidence');
 INSERT INTO entities(id,type,name) VALUES ('theme.test','theme','Theme'),('person.test','person','Moses');
 INSERT INTO entity_localizations(entity_id,language,source_id,name,description) VALUES
 ('theme.test','pt-BR','legacy.source','Tema','Resumo anterior'),
 ('person.test','pt-BR','legacy.source','Moisés','Pessoa');
 INSERT INTO entity_key_passages VALUES ('theme.test',0,'Exod',14,10,31),('theme.test',1,'Heb',11,1,16);
 INSERT INTO entity_detail_sources(entity_id,source_id) VALUES ('theme.test','legacy.source');
 INSERT INTO theme_studies VALUES ('theme.test','{"en":{"overview":{"text":"English only","references":[{"bookId":"Exod","chapter":14,"verseStart":10,"verseEnd":31}]},"readings":[],"comparisons":[],"connections":[{"entityId":"person.test","reference":{"bookId":"Exod","chapter":14,"verseStart":10,"verseEnd":31},"explanation":"Moses in the crossing"}],"sourceReferenceIds":["study.source"]}}');
 INSERT INTO theme_study_sources VALUES ('theme.test','study.source',0);
 INSERT INTO theme_study_passages VALUES ('theme.test',0,'Exod',14,10,31),('theme.test',1,'Num',14,1,9);
 `)
	if err != nil {
		t.Fatal(err)
	}
	db, err := postgres.Open(ctx, url)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(db.Close)
	en, err := db.Detail(ctx, "theme.test")
	if err != nil || en.ThemeStudy == nil || len(en.ThemeStudy.Connections) != 1 || en.ThemeStudy.Connections[0].Entity.Name != "Moses" || len(en.KeyPassages) != 3 || len(en.Sources) != 2 {
		t.Fatalf("guide unavailable or duplicate readings: %+v %v", en, err)
	}
	pt := store.WithLanguage(ctx, "pt-BR")
	old, err := db.Detail(pt, "theme.test")
	if err != nil || old.ThemeStudy != nil || len(old.KeyPassages) != 2 || len(old.Sources) != 1 || old.Entity.Name != "Tema" {
		t.Fatalf("missing translation must preserve prior localized page: %+v %v", old, err)
	}
	contextPage, err := db.Context(ctx, "Num", 14)
	if err != nil || len(contextPage.Entities) != 1 || len(contextPage.Sources) != 2 {
		t.Fatalf("chapter does not link to theme with evidence: %+v %v", contextPage, err)
	}
	evidence, err := db.EntitySources(ctx, []string{"theme.test"})
	if err != nil || len(evidence) != 1 || evidence[0].ID != "study.source" {
		t.Fatalf("theme study evidence missing: %+v %v", evidence, err)
	}
	// Add a genuine Portuguese edition and resolve connected names in that language.
	_, err = conn.Exec(ctx, `UPDATE theme_studies SET presentations=presentations ||
 jsonb_build_object('pt-BR', jsonb_set(presentations->'en','{overview,text}','"Estudo em português"'))`)
	if err != nil {
		t.Fatal(err)
	}
	translated, err := db.Detail(pt, "theme.test")
	if err != nil || translated.ThemeStudy == nil || translated.ThemeStudy.Overview.Text != "Estudo em português" || translated.ThemeStudy.Connections[0].Entity.Name != "Moisés" {
		t.Fatalf("localized guide connection: %+v %v", translated, err)
	}
}
