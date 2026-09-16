package httpapi

import (
	"context"
	"encoding/json"
	"net/http/httptest"
	"testing"
	"time"
	"verbum/backend/internal/domain"
	"verbum/backend/internal/store"
	"verbum/backend/internal/store/memory"
	"verbum/backend/internal/usage"
)

func TestLimitedAskReturnsIndexedPassagesWithoutSyntheticAnswer(t *testing.T) {
	for _, code := range []string{"quota_exceeded", "budget_exhausted", "usage_unavailable"} {
		t.Run(code, func(t *testing.T) {
			base, e := memory.Load("../../db/seed/fixtures.json")
			if e != nil {
				t.Fatal(e)
			}
			srv := httptest.NewServer(New(lexicalFallbackStore{base}, time.Now, nil, forbiddenEmbedder{}, &fakeAsker{err: &usage.Denial{Code: code, RetryAt: time.Now().Add(time.Hour)}}, nil))
			defer srv.Close()
			res := postAsk(t, srv, `{"question":"1 Samuel 17:49"}`)
			defer res.Body.Close()
			if res.StatusCode != 200 {
				t.Fatal(res.StatusCode)
			}
			var answer domain.AskResponse
			if e := json.NewDecoder(res.Body).Decode(&answer); e != nil {
				t.Fatal(e)
			}
			if answer.Fallback == nil || answer.Fallback.Code != code || answer.Answer != "" || len(answer.PassageReferences) == 0 {
				t.Fatalf("misleading or missing fallback: %+v", answer)
			}
		})
	}
}

type lexicalFallbackStore struct{ store.Store }

func (s lexicalFallbackStore) SearchPassages(context.Context, string, []float32, int) ([]domain.PassageReference, error) {
	return []domain.PassageReference{{BookID: "John", Chapter: 3}}, nil
}

type forbiddenEmbedder struct{}

func (forbiddenEmbedder) Embed(context.Context, string) ([]float32, error) {
	panic("fallback must not call paid embeddings")
}
