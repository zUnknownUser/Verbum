package postgres_test

import (
	"context"
	"testing"
	"verbum/backend/internal/store"
	"verbum/backend/internal/store/postgres"
	"verbum/backend/internal/testdb"
)

func TestTimelineStudyOrderAndLocalization(t *testing.T) {
	ctx := context.Background()
	conn, url := testdb.Open(t, "../../../db/migrations")
	_, err := conn.Exec(ctx, `INSERT INTO timeline_events(id,title,start_year,date_precision,position,discovery) VALUES
 ('study.first','fallback',NULL,'unknown',0,'{"en":{"title":"Creation","summary":"English summary","eraId":"origins","eraTitle":"Origins","eraSummary":"Beginning","kind":"event","context":"English context","keyPassages":[{"bookId":"Gen","chapter":1}]},"pt-BR":{"title":"Criação","summary":"Resumo","eraId":"origins","eraTitle":"Origens","eraSummary":"Início","kind":"event","context":"Contexto","keyPassages":[{"bookId":"Gen","chapter":1}]}}'),
 ('study.second','second',-1000,'approximate',1,'{"en":{"title":"Later","summary":"Later","eraId":"next","eraTitle":"Next","eraSummary":"Next","kind":"period","context":"Context","keyPassages":[{"bookId":"Gen","chapter":2}]}}')`)
	if err != nil {
		t.Fatal(err)
	}
	db, err := postgres.Open(ctx, url)
	if err != nil {
		t.Fatal(err)
	}
	defer db.Close()
	en, err := db.Timeline(ctx, "")
	if err != nil {
		t.Fatal(err)
	}
	if len(en.Events) != 2 || en.Events[0].ID != "study.first" || en.Events[0].Discovery.KeyPassages[0].BookID != "Gen" {
		t.Fatalf("narrative order/metadata lost: %+v", en)
	}
	pt, err := db.Timeline(store.WithLanguage(ctx, "pt-BR"), "")
	if err != nil {
		t.Fatal(err)
	}
	if len(pt.Events) != 1 || pt.Events[0].Title != "Criação" || pt.Events[0].Discovery.Context != "Contexto" {
		t.Fatalf("localized metadata missing: %+v", pt)
	}
	filtered, err := db.Timeline(ctx, "missing")
	if err != nil || len(filtered.Events) != 0 {
		t.Fatalf("entity filter: %+v %v", filtered, err)
	}
}

func TestCanonicalEventBindingIsBidirectionalWithoutDuplicateParticipants(t *testing.T) {
	ctx := context.Background()
	conn, url := testdb.Open(t, "../../../db/migrations")
	_, err := conn.Exec(ctx, `INSERT INTO entities(id,type,name,position) VALUES ('event.canonical','event','Study',0);
 INSERT INTO timeline_events(id,title,date_precision,position,discovery) VALUES ('timeline.original','Episode','unknown',0,'{"en":{"eraId":"origins","eraTitle":"Origins","eraSummary":"Beginning","kind":"event","context":"Context","keyPassages":[{"bookId":"Gen","chapter":1}]}}');
 INSERT INTO timeline_event_catalog(event_id,entity_id) VALUES ('timeline.original','event.canonical');
 INSERT INTO timeline_event_entities(event_id,entity_id,position) VALUES ('timeline.original','event.canonical',0)`)
	if err != nil {
		t.Fatal(err)
	}
	db, err := postgres.Open(ctx, url)
	if err != nil {
		t.Fatal(err)
	}
	defer db.Close()
	result, err := db.Timeline(ctx, "event.canonical")
	if err != nil {
		t.Fatal(err)
	}
	if len(result.Events) != 1 || len(result.Events[0].EntityIDs) != 1 || result.Events[0].Discovery.EventEntityID == nil || *result.Events[0].Discovery.EventEntityID != "event.canonical" || result.EntityNames["event.canonical"] != "Study" {
		t.Fatalf("binding lost/duplicated: %+v", result)
	}
}
