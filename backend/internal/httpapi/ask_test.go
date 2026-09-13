package httpapi

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"verbum/backend/internal/domain"
	"verbum/backend/internal/store/memory"
)

type fakeAsker struct {
	response domain.AskResponse
	err      error
	gotQ     string
}

func (f *fakeAsker) Ask(_ context.Context, question string) (domain.AskResponse, error) {
	f.gotQ = question
	if f.err != nil {
		return domain.AskResponse{}, f.err
	}
	return f.response, nil
}

func askServer(t *testing.T, a asker) *httptest.Server {
	t.Helper()
	s, err := memory.Load("../../db/seed/fixtures.json")
	if err != nil {
		t.Fatal(err)
	}
	srv := httptest.NewServer(New(s, time.Now, nil, nil, a, nil))
	t.Cleanup(srv.Close)
	return srv
}

func postAsk(t *testing.T, srv *httptest.Server, body string) *http.Response {
	t.Helper()
	res, err := http.Post(srv.URL+"/v1/ask", "application/json", bytes.NewBufferString(body))
	if err != nil {
		t.Fatal(err)
	}
	return res
}

func TestAskNotConfigured(t *testing.T) {
	srv := askServer(t, nil)
	res := postAsk(t, srv, `{"question":"why did job suffer"}`)
	defer res.Body.Close()
	if res.StatusCode != http.StatusServiceUnavailable {
		t.Fatalf("status = %d", res.StatusCode)
	}
	var p Problem
	_ = json.NewDecoder(res.Body).Decode(&p)
	if p.Code != CodeAskUnavailable {
		t.Errorf("code = %q", p.Code)
	}
}

func TestAskSuccess(t *testing.T) {
	fake := &fakeAsker{response: domain.AskResponse{
		Answer: "...", Summary: "...", Confidence: "medium",
		PassageReferences: []domain.PassageReference{}, EntityReferences: []string{}, SourceReferences: []domain.SourceReference{},
	}}
	srv := askServer(t, fake)
	res := postAsk(t, srv, `{"question":"why did job suffer"}`)
	defer res.Body.Close()
	if res.StatusCode != http.StatusOK {
		t.Fatalf("status = %d", res.StatusCode)
	}
	if cc := res.Header.Get("Cache-Control"); cc != "no-store" {
		t.Errorf("Cache-Control = %q, want no-store", cc)
	}
	if fake.gotQ != "why did job suffer" {
		t.Errorf("question forwarded = %q", fake.gotQ)
	}
	var got domain.AskResponse
	if err := json.NewDecoder(res.Body).Decode(&got); err != nil {
		t.Fatal(err)
	}
	if got.Confidence != "medium" {
		t.Errorf("body = %+v", got)
	}
}

func TestAskRejectsEmptyOrOversizedQuestion(t *testing.T) {
	srv := askServer(t, &fakeAsker{})
	for _, body := range []string{`{"question":""}`, `{}`, `{"question":"` + strings.Repeat("a", 501) + `"}`} {
		res := postAsk(t, srv, body)
		if res.StatusCode != http.StatusBadRequest {
			t.Errorf("body %q: status = %d, want 400", body, res.StatusCode)
		}
		res.Body.Close()
	}
}

func TestAskRejectsMalformedJSON(t *testing.T) {
	srv := askServer(t, &fakeAsker{})
	res := postAsk(t, srv, `not json`)
	defer res.Body.Close()
	if res.StatusCode != http.StatusBadRequest {
		t.Fatalf("status = %d", res.StatusCode)
	}
}

func TestAskUpstreamFailureDoesNotLeakDetail(t *testing.T) {
	fake := &fakeAsker{err: errors.New("upstream exploded")}
	srv := askServer(t, fake)
	res := postAsk(t, srv, `{"question":"why did job suffer"}`)
	defer res.Body.Close()
	if res.StatusCode != http.StatusBadGateway {
		t.Fatalf("status = %d", res.StatusCode)
	}
	var p Problem
	_ = json.NewDecoder(res.Body).Decode(&p)
	if p.Code != CodeInternal || p.Message == "upstream exploded" {
		t.Errorf("problem = %#v", p)
	}
}
