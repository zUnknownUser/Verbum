package httpapi

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"reflect"
	"testing"
	"time"

	"verbum/backend/internal/store/memory"
)

// Every example in api/examples/ is what this server must answer for the
// fixtures. The same files are decoded by the iOS and Android contract tests,
// so all three agree on one wire shape.
func TestContractExamples(t *testing.T) {
	s, err := memory.Load("../../db/seed/fixtures.json")
	if err != nil {
		t.Fatal(err)
	}
	fixed := func() time.Time { return time.Date(2026, 9, 13, 12, 0, 0, 0, time.UTC) }
	srv := httptest.NewServer(New(s, fixed))
	defer srv.Close()

	cases := map[string]string{
		"/v1/entities/fixture.person.david":                "../../../api/examples/entities/david.json",
		"/v1/entities?type=person":                         "../../../api/examples/entities/people.json",
		"/v1/entities/fixture.person.david/graph?limit=24": "../../../api/examples/graph/david.json",
		"/v1/passages/1Sam.17/context":                     "../../../api/examples/context/1Sam.17.json",
		"/v1/timeline":                                     "../../../api/examples/timeline/all.json",
		"/v1/search?q=David":                               "../../../api/examples/search/david.json",
		"/v1/daily-verse?from=2026-09-13&days=7":           "../../../api/examples/daily-verse/week.json",
	}
	for path, example := range cases {
		t.Run(path, func(t *testing.T) {
			want := readJSON(t, example)
			res, err := http.Get(srv.URL + path)
			if err != nil {
				t.Fatal(err)
			}
			defer res.Body.Close()
			if res.StatusCode != http.StatusOK {
				t.Fatalf("status %d", res.StatusCode)
			}
			var got any
			if err := json.NewDecoder(res.Body).Decode(&got); err != nil {
				t.Fatal(err)
			}
			if !reflect.DeepEqual(got, want) {
				g, _ := json.MarshalIndent(got, "", "  ")
				t.Errorf("response differs from %s:\n%s", example, g)
			}
		})
	}
}

func TestProblems(t *testing.T) {
	s, _ := memory.Load("../../db/seed/fixtures.json")
	srv := httptest.NewServer(New(s, time.Now))
	defer srv.Close()
	cases := map[string]struct {
		status int
		code   string
	}{
		"/v1/entities/nope":              {404, CodeUnknownEntity},
		"/v1/passages/Neh.9/context":     {404, CodeContentUnavailable},
		"/v1/passages/garbage/context":   {400, CodeMalformedRequest},
		"/v1/entities?type=dragon":       {400, CodeMalformedRequest},
		"/v1/entities/x/graph?limit=999": {400, CodeMalformedRequest},
		"/v1/search?q=":                  {400, CodeMalformedRequest},
		"/v1/daily-verse?days=40":        {400, CodeMalformedRequest},
	}
	for path, want := range cases {
		res, err := http.Get(srv.URL + path)
		if err != nil {
			t.Fatal(err)
		}
		var p Problem
		json.NewDecoder(res.Body).Decode(&p)
		res.Body.Close()
		if res.StatusCode != want.status || p.Code != want.code {
			t.Errorf("%s: got %d %q, want %d %q", path, res.StatusCode, p.Code, want.status, want.code)
		}
	}
}

func readJSON(t *testing.T, path string) any {
	t.Helper()
	raw, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	var v any
	if err := json.Unmarshal(raw, &v); err != nil {
		t.Fatal(err)
	}
	return v
}
