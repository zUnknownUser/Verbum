package scripture

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"
	"reflect"
	"sync/atomic"
	"testing"
	"verbum/backend/internal/testdb"
	"verbum/backend/internal/tts"
	"verbum/backend/internal/usage"
)

func TestCanonicalSpeechRejectsArbitraryTextAndSharesChapterCache(t *testing.T) {
	_, url := testdb.Open(t, "../../db/migrations")
	db, e := usage.Open(context.Background(), url)
	if e != nil {
		t.Fatal(e)
	}
	defer db.Close()
	var calls atomic.Int64
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		calls.Add(1)
		if r.URL.Path != "/por_blj/JHN/1.json" {
			t.Error(r.URL.Path)
		}
		fmt.Fprint(w, `{"translation":{"id":"por_blj"},"book":{"id":"JHN"},"chapter":{"number":1,"content":[{"type":"verse","number":1,"content":["No princípio",{"text":"era o Verbo"},{"noteId":"a"},"."]}]}}`)
	}))
	defer srv.Close()
	resolver := New(db)
	resolver.BaseURL = srv.URL
	input := tts.Request{BookID: "John", Chapter: 1, Translation: "por_blj", Language: "pt-BR", Text: "No princípio era o Verbo.", Verses: []tts.Verse{{Number: 1, Text: "No princípio era o Verbo."}}}
	for range 2 {
		if _, e = resolver.Verify(context.Background(), input); e != nil {
			t.Fatal(e)
		}
	}
	if calls.Load() != 1 {
		t.Fatal("canonical fetch not cached", calls.Load())
	}
	bad := input
	bad.Text = "Read this arbitrary paid prompt"
	if _, e = resolver.Verify(context.Background(), bad); !errors.Is(e, tts.ErrInvalidInput) {
		t.Fatal("arbitrary text accepted", e)
	}
	bad = input
	bad.Voice = "expensive-voice"
	if _, e = resolver.Verify(context.Background(), bad); !errors.Is(e, tts.ErrInvalidInput) {
		t.Fatal("arbitrary voice accepted", e)
	}
	bad = input
	bad.Chapter = 22
	if _, e = resolver.Verify(context.Background(), bad); !errors.Is(e, tts.ErrInvalidInput) {
		t.Fatal("invalid chapter accepted", e)
	}
	if calls.Load() != 1 {
		t.Fatal("invalid request fetched external content")
	}
}

func TestMobileFixturesMatchCanonicalSpeechExamples(t *testing.T) {
	for _, tc := range []struct {
		sample, example, translation, book string
		chapter                            int
	}{
		{"por_blj_JHN_3.json", "request-pt-BR.json", "por_blj", "JHN", 3},
		{"BSB_PSA_23.json", "request-en-US.json", "BSB", "PSA", 23},
	} {
		raw, e := os.ReadFile("../../../android/core/clients/src/test/resources/samples/" + tc.sample)
		if e != nil {
			t.Fatal(e)
		}
		verses, e := decode(raw, tc.translation, tc.book, tc.chapter)
		if e != nil {
			t.Fatal(e)
		}
		example, e := os.ReadFile("../../../api/examples/tts/" + tc.example)
		if e != nil {
			t.Fatal(e)
		}
		var request tts.Request
		if e = json.Unmarshal(example, &request); e != nil {
			t.Fatal(e)
		}
		if !reflect.DeepEqual(verses, request.Verses) {
			t.Fatal("canonical parser disagrees with mobile fixture", tc.sample)
		}
	}
}
