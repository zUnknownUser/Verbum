package postgres_test

import (
	"context"
	"strings"
	"testing"

	"verbum/backend/internal/ask"
	"verbum/backend/internal/domain"
	"verbum/backend/internal/store"
	"verbum/backend/internal/store/postgres"
	"verbum/backend/internal/testdb"
)

type stepSynth struct{ prompt string }

func (s *stepSynth) Complete(_ context.Context, _, user string) (string, error) {
	s.prompt = user
	return `{"answer":"David struck the Philistine.","summary":"David","citedPassageIndexes":[0,999],"confidence":"high","interpretiveVariance":false}`, nil
}

func TestStructuredEnrichmentRetrievalAndLocalization(t *testing.T) {
	ctx := context.Background()
	conn, url := testdb.Open(t, "../../../db/migrations")
	_, err := conn.Exec(ctx, `
 INSERT INTO sources(id,citation,url,position) VALUES
 ('step.TIPNR','STEP Bible / Tyndale House — TIPNR (CC BY 4.0)','https://example.test/pinned-source',0),
 ('editorial.pt','Synthetic Portuguese localization',NULL,1);
 INSERT INTO source_datasets VALUES('step.TIPNR','https://github.com/STEPBible/STEPBible-Data',repeat('a',40),'TIPNR.tsv',repeat('b',64),'https://creativecommons.org/licenses/by/4.0/','STEP Bible','Selected structured fields');
 INSERT INTO entities(id,type,name,summary,position) VALUES
 ('existing.david','person','David','Existing curated summary',0),
 ('step.lexeme.H1732','originalTerm','דָּוִד',NULL,1),
 ('different.david','person','David',NULL,2);
 INSERT INTO entity_source_records(id,entity_id,source_id,revision,external_id,source_line,identifiers,lexical) VALUES
 ('step.TIPNR.H1732','existing.david','step.TIPNR',repeat('a',40),'H1732',161,'{"uStrong":["H1732"],"originalForm":["דָּוִד"]}',NULL),
 ('step.lexical.H1732','step.lexeme.H1732','step.TIPNR',repeat('a',40),'lexeme.H1732',55,'{"dStrong":["H1732"]}',
 '{"language":"he","original":"דָּוִד","transliteration":"dawid","morphology":"N:N-M-P","gloss":"David","glossLanguage":"en","extendedStrong":"H1732","disambiguatedStrong":"H1732 =","unifiedStrong":"H1732"}');
 INSERT INTO entity_localizations VALUES
 ('existing.david','en','step.TIPNR','David',ARRAY['Dawid'],NULL),
 ('existing.david','pt-BR','editorial.pt','Davi',ARRAY['Rei Davi'],'Descrição editorial em português'),
 ('step.lexeme.H1732','en','step.TIPNR','דָּוִד',ARRAY['dawid'],'David');
 INSERT INTO entity_occurrences VALUES
 ('step.TIPNR.H1732','1Sa.17.49a','1Sam',17,49),
 ('step.TIPNR.H1732','1Sa.17.49b','1Sam',17,49);
 INSERT INTO relationships(id,source_entity_id,target_entity_id,relationship_type,position) VALUES
 ('step.name-lexeme.H1732','existing.david','step.lexeme.H1732','relatedTo',0);
 INSERT INTO relationship_sources(relationship_id,source_id,position) VALUES
 ('step.name-lexeme.H1732','step.TIPNR',0);
 `)
	if err != nil {
		t.Fatal(err)
	}
	seedThreeVerses(t, ctx, conn)
	db, err := postgres.Open(ctx, url)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(db.Close)
	pt := store.WithLanguage(ctx, "pt-BR")
	detail, err := db.Detail(pt, "existing.david")
	if err != nil {
		t.Fatal(err)
	}
	if detail.Entity.Name != "Davi" || detail.Entity.NameLanguage != "pt-BR" || len(detail.SourceRecords) != 1 || detail.SourceRecords[0].OccurrenceCount != 2 {
		t.Fatalf("detail: %+v", detail)
	}
	if len(detail.Sources) != 2 {
		t.Fatal("missing localization attribution")
	}
	if detail.SourceRecords[0].Dataset.Revision != strings.Repeat("a", 40) {
		t.Fatal("missing pinned provenance")
	}
	lex, err := db.Detail(pt, "step.lexeme.H1732")
	if err != nil {
		t.Fatal(err)
	}
	if lex.Entity.Name != "דָּוִד" || lex.Entity.NameLanguage != "en" || lex.SourceRecords[0].Lexical.Language != "he" {
		t.Fatalf("original/fallback changed: %+v", lex)
	}
	for _, q := range []string{"H1732", "h1732", "Dawid", "דָּוִד", "Rei Davi"} {
		hits, err := db.Search(pt, q)
		if err != nil {
			t.Fatal(err)
		}
		if len(hits) == 0 {
			t.Errorf("no search hits for %q", q)
		}
		for _, hit := range hits {
			if hit.ID == "different.david" {
				t.Errorf("conflated homonym for %q", q)
			}
		}
	}
	people, err := db.Entities(pt, domain.Person)
	if err != nil {
		t.Fatal(err)
	}
	if len(people) != 2 {
		t.Fatalf("people=%+v", people)
	}
	refs, err := db.SearchPassages(pt, "H1732", nil, 6)
	if err != nil {
		t.Fatal(err)
	}
	if len(refs) != 1 || refs[0].Key() != "1Sam.17.49" {
		t.Fatalf("structured references=%+v", refs)
	}
	aliases, err := db.SearchPassages(pt, "Rei Davi", nil, 6)
	if err != nil || len(aliases) != 1 {
		t.Fatalf("PT retrieval: %v %v", aliases, err)
	}
	ids, err := db.EntitiesForPassages(ctx, refs)
	if err != nil || len(ids) != 1 || ids[0] != "existing.david" {
		t.Fatalf("occurrences: %v %v", ids, err)
	}
	pc, err := db.Context(pt, "1Sam", 17)
	if err != nil {
		t.Fatal(err)
	}
	if len(pc.Entities) != 1 || pc.Entities[0].Name != "Davi" || len(pc.Sources) != 2 {
		t.Fatalf("context: %+v", pc)
	}
	graph, err := db.Neighbors(pt, "existing.david", 12)
	if err != nil {
		t.Fatal(err)
	}
	if graph.Root.Name != "Davi" || len(graph.Nodes) != 1 {
		t.Fatalf("graph: %+v", graph)
	}
	synth := &stepSynth{}
	service := ask.Service{Store: db, Synthesizer: synth}
	response, err := service.Ask(ctx, "H1732")
	if err != nil {
		t.Fatal(err)
	}
	if len(response.PassageReferences) != 1 || len(response.EntityReferences) != 1 || len(response.SourceReferences) != 2 {
		t.Fatalf("Ask: %+v", response)
	}
	if response.SourceReferences[1].ID != "step.TIPNR" {
		t.Fatal("missing STEP attribution")
	}
	if !strings.Contains(synth.prompt, "David struck the Philistine") || strings.Contains(synth.prompt, "Existing curated summary") {
		t.Fatal("Scripture citation boundary changed")
	}
}

func TestPortuguesePresentationCoversDetailsTimelineAndSources(t *testing.T) {
	ctx := context.Background()
	conn, url := testdb.Open(t, "../../../db/migrations")
	_, err := conn.Exec(ctx, `
 INSERT INTO sources(id,citation,position) VALUES ('source','Editorial notes',0),('translation','Translation attribution',1);
 INSERT INTO entities(id,type,name,summary,position) VALUES ('moses','person','Moses','Led Israel out of Egypt.',0);
 INSERT INTO entity_details(entity_id,approximate_dates,role,modern_geography) VALUES ('moses','Dates uncertain','Prophet','Egypt');
 INSERT INTO entity_aliases(entity_id,alias,position) VALUES ('moses','Moses the prophet',0);
 INSERT INTO entity_sources(entity_id,source_id,position) VALUES ('moses','source',0);
 INSERT INTO entity_localizations(entity_id,language,source_id,name,aliases,description,fields) VALUES
 ('moses','pt-BR','translation','Moisés',ARRAY['Profeta Moisés'],'Conduziu Israel para fora do Egito.',
 '{"approximateDates":"Datas incertas","role":"Profeta","modernGeography":"Egito"}');
 INSERT INTO timeline_events(id,title,start_year,date_precision,summary,position) VALUES ('exodus','Exodus',-1400,'approximate','Departure from Egypt.',0);
 INSERT INTO timeline_event_entities(event_id,entity_id,position) VALUES ('exodus','moses',0);
 INSERT INTO timeline_localizations VALUES ('exodus','pt-BR','translation','{"title":"Êxodo","summary":"Saída do Egito."}','hash');
 INSERT INTO source_localizations VALUES ('source','pt-BR','translation','{"citation":"Notas editoriais"}','hash');
 `)
	if err != nil {
		t.Fatal(err)
	}
	db, err := postgres.Open(ctx, url)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(db.Close)
	pt := store.WithLanguage(ctx, "pt-BR")
	detail, err := db.Detail(pt, "moses")
	if err != nil {
		t.Fatal(err)
	}
	if detail.Entity.Name != "Moisés" || *detail.Role != "Profeta" || *detail.ApproximateDates != "Datas incertas" || *detail.ModernGeography != "Egito" || len(detail.Aliases) != 1 || detail.Aliases[0] != "Profeta Moisés" {
		t.Fatalf("PT detail: %+v", detail)
	}
	if detail.Sources[0].Citation != "Notas editoriais" {
		t.Fatalf("sources: %+v", detail.Sources)
	}
	en, err := db.Detail(ctx, "moses")
	if err != nil || en.Entity.Name != "Moses" || *en.Role != "Prophet" || len(en.Sources) != 1 {
		t.Fatalf("EN detail: %+v %v", en, err)
	}
	timeline, err := db.Timeline(pt, "")
	if err != nil || timeline.Events[0].Title != "Êxodo" || timeline.EntityNames["moses"] != "Moisés" {
		t.Fatalf("timeline: %+v %v", timeline, err)
	}
}

func TestVerseReferencesReuseAttestedEntityOccurrences(t *testing.T) {
	ctx := context.Background()
	conn, url := testdb.Open(t, "../../../db/migrations")
	_, err := conn.Exec(ctx, `
 INSERT INTO sources(id,citation,position) VALUES ('step','STEP TIPNR CC BY 4.0',0);
 INSERT INTO source_datasets VALUES('step','https://github.com/STEPBible/STEPBible-Data',repeat('a',40),'TIPNR.tsv',repeat('b',64),'https://creativecommons.org/licenses/by/4.0/','STEP Bible','Structured occurrences');
 INSERT INTO entities(id,type,name,position) VALUES ('david','person','David',0),('saul','person','Saul',1);
 INSERT INTO entity_source_records(id,entity_id,source_id,revision,external_id,source_line,identifiers) VALUES
 ('record.david','david','step',repeat('a',40),'david',1,'{}'),('record.saul','saul','step',repeat('a',40),'saul',2,'{}');
 INSERT INTO entity_occurrences VALUES
 ('record.david','1Sa.17.49a','1Sam',17,49),('record.david','1Sa.17.49b','1Sam',17,49),
 ('record.david','1Sa.18.1a','1Sam',18,1),('record.david','1Sa.18.1b','1Sam',18,1),
 ('record.saul','1Sa.17.55','1Sam',17,55),('record.saul','1Sa.19.1','1Sam',19,1);
 `)
	if err != nil {
		t.Fatal(err)
	}
	db, err := postgres.Open(ctx, url)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(db.Close)
	got, err := db.Context(store.WithVerse(ctx, 49), "1Sam", 17)
	if err != nil {
		t.Fatal(err)
	}
	if len(got.RelatedPassages) != 1 || got.RelatedPassages[0].Key() != "1Sam.18.1" {
		t.Fatalf("unrelated or duplicate references: %+v", got.RelatedPassages)
	}
	if len(got.Sources) != 1 || got.Sources[0].ID != "step" {
		t.Fatal("lost provenance")
	}
	chapter, err := db.Context(ctx, "1Sam", 17)
	if err != nil {
		t.Fatal(err)
	}
	if len(chapter.RelatedPassages) != 0 {
		t.Fatal("chapter-only API contract changed")
	}
}
