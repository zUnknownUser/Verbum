package scripture

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestLibraryChapters(t *testing.T) {
	all := Chapters()
	if len(all) != 1189 {
		t.Fatalf("chapters: %d", len(all))
	}
	seen := map[Chapter]bool{}
	for _, ch := range all {
		if seen[ch] || !ValidChapter(ch.BookID, ch.Number) {
			t.Fatalf("invalid or duplicate: %+v", ch)
		}
		seen[ch] = true
	}
	if all[0] != (Chapter{"Gen", 1}) || all[len(all)-1] != (Chapter{"Rev", 22}) {
		t.Fatal("canonical order")
	}
}

func TestLoadCanonicalWithoutGeneration(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/por_blj/GEN/1.json" {
			t.Errorf("path: %s", r.URL.Path)
		}
		w.Write([]byte(`{"translation":{"id":"por_blj"},"book":{"id":"GEN"},"chapter":{"number":1,"content":[{"type":"heading","content":["Ignored"]},{"type":"verse","number":1,"content":["First verse."]},{"type":"verse","number":2,"content":["Second verse."]}]}}`))
	}))
	defer server.Close()
	r := New(nil)
	r.BaseURL = server.URL
	r.Client = server.Client()
	in, err := r.Load(context.Background(), "pt-BR", Chapter{"Gen", 1})
	if err != nil {
		t.Fatal(err)
	}
	if in.Text != "First verse.\nSecond verse." || len(in.Verses) != 2 || in.Translation != "por_blj" {
		t.Fatalf("bad canonical request: %+v", in)
	}
	if _, err = r.Load(context.Background(), "pt-BR", Chapter{"Gen", 51}); err == nil {
		t.Fatal("accepted invalid chapter")
	}
	if _, err = r.Load(context.Background(), "fr-FR", Chapter{"Gen", 1}); err == nil {
		t.Fatal("accepted unsupported language")
	}
}
