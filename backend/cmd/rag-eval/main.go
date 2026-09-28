// rag-eval evaluates the production retrieval/Ask pipeline against reviewed questions.
// Database connections are read-only. By default it makes NO provider calls. Live
// embeddings and synthesis require separate explicit flags; input is capped at 50 cases.
package main

import (
	"context"
	"encoding/json"
	"flag"
	"fmt"
	"log"
	"net/url"
	"os"
	"strings"
	"time"

	"verbum/backend/internal/ask"
	"verbum/backend/internal/domain"
	"verbum/backend/internal/embeddings"
	"verbum/backend/internal/store"
	"verbum/backend/internal/store/postgres"
	"verbum/backend/internal/synthesis"
)

type question struct {
	Question    string                    `json:"question"`
	Language    string                    `json:"language"`
	Expected    []domain.PassageReference `json:"expected"`
	Unsupported bool                      `json:"unsupported"`
}

type observedStore struct {
	*postgres.Store
	seeds, evidence []domain.PassageReference
}

func (s *observedStore) SearchPassages(ctx context.Context, q string, v []float32, n int) ([]domain.PassageReference, error) {
	refs, err := s.Store.SearchPassages(ctx, q, v, n)
	s.seeds = refs
	return refs, err
}
func (s *observedStore) PassageText(ctx context.Context, translation string, refs []domain.PassageReference) (map[string]string, error) {
	texts, err := s.Store.PassageText(ctx, translation, refs)
	for _, r := range refs {
		if texts[r.Key()] != "" {
			s.evidence = append(s.evidence, r)
		}
	}
	return texts, err
}

type cachedEmbedder map[string][]float32

func (c cachedEmbedder) Embed(_ context.Context, q string) ([]float32, error) {
	if v := c[q]; len(v) == embeddings.Dimensions {
		return v, nil
	}
	return nil, fmt.Errorf("no cached query vector")
}

type noSynthesis struct{}

func (noSynthesis) Complete(context.Context, string, string) (string, error) {
	return `{"answer":"","summary":"retrieval-only evaluation","citedPassageIndexes":[],"confidence":"low"}`, nil
}

func matches(refs, wanted []domain.PassageReference) bool {
	for _, r := range refs {
		for _, w := range wanted {
			if r.BookID == w.BookID && r.Chapter == w.Chapter && r.VerseStart != nil && w.VerseStart != nil {
				end := *w.VerseStart
				if w.VerseEnd != nil {
					end = *w.VerseEnd
				}
				if *r.VerseStart >= *w.VerseStart && *r.VerseStart <= end {
					return true
				}
			}
		}
	}
	return false
}
func keys(refs []domain.PassageReference) []string {
	out := []string{}
	for _, r := range refs {
		out = append(out, r.Key())
	}
	return out
}

func main() {
	path := flag.String("cases", "internal/retrieval/testdata/questions.json", "reviewed evaluation cases")
	vectors := flag.String("vectors", "", "cached query vectors JSON; no provider calls")
	live := flag.Bool("live-embeddings", false, "explicitly allow one paid embedding per question")
	answers := flag.Bool("answers", false, "explicitly allow one paid synthesis per question")
	only := flag.String("only", "", "run only questions containing this text")
	limit := flag.Int("limit", 50, "maximum questions (1–50)")
	flag.Parse()
	if *limit < 1 || *limit > 50 {
		log.Fatal("limit must be 1–50")
	}
	raw, err := os.ReadFile(*path)
	if err != nil {
		log.Fatal(err)
	}
	var cases []question
	if err = json.Unmarshal(raw, &cases); err != nil {
		log.Fatal(err)
	}
	var embedder ask.Embedder
	if *vectors != "" {
		raw, err = os.ReadFile(*vectors)
		if err != nil {
			log.Fatal(err)
		}
		var cached cachedEmbedder
		if err = json.Unmarshal(raw, &cached); err != nil {
			log.Fatal(err)
		}
		embedder = cached
	}
	if *live || *answers {
		if os.Getenv("OPENAI_API_KEY") == "" {
			log.Fatal("OPENAI_API_KEY required for explicitly enabled live evaluation")
		}
	}
	if *live {
		embedder = embeddings.New(os.Getenv("OPENAI_API_KEY"))
	}
	var synth ask.Synthesizer = noSynthesis{}
	if *answers {
		synth = synthesis.New(os.Getenv("OPENAI_API_KEY"), os.Getenv("VERBUM_ASK_MODEL"))
	}
	uri, err := url.Parse(os.Getenv("DATABASE_URL"))
	if err != nil || uri.Host == "" {
		log.Fatal("DATABASE_URL must be a PostgreSQL URL")
	}
	params := uri.Query()
	params.Set("default_transaction_read_only", "on")
	params.Set("statement_timeout", "20000")
	uri.RawQuery = params.Encode()
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	db, err := postgres.Open(ctx, uri.String())
	cancel()
	if err != nil {
		log.Fatal("read-only database connection failed")
	}
	defer db.Close()
	encoder := json.NewEncoder(os.Stdout)
	ran := 0
	for _, q := range cases {
		if *only != "" && !strings.Contains(strings.ToLower(q.Question), strings.ToLower(*only)) {
			continue
		}
		if ran >= *limit {
			break
		}
		ran++
		if len(q.Question) > 2048 {
			log.Fatal("evaluation question exceeds bound")
		}
		observed := &observedStore{Store: db}
		service := ask.Service{Store: observed, Embedder: embedder, Synthesizer: synth}
		ctx, cancel := context.WithTimeout(store.WithLanguage(context.Background(), q.Language), 30*time.Second)
		start := time.Now()
		result, err := service.Ask(ctx, q.Question)
		cancel()
		row := map[string]any{"question": q.Question, "language": q.Language, "candidates": keys(observed.seeds), "top6": keys(observed.seeds[:min(6, len(observed.seeds))]), "evidenceCount": len(observed.evidence), "ms": time.Since(start).Milliseconds()}
		if len(q.Expected) > 0 {
			row["seedHit"] = matches(observed.seeds[:min(6, len(observed.seeds))], q.Expected)
			row["contextHit"] = matches(observed.evidence, q.Expected)
		}
		if *answers {
			row["answer"] = result.Answer
			row["citations"] = keys(result.PassageReferences)
			row["confidence"] = result.Confidence
			if q.Unsupported {
				row["refusedUnsupportedClaim"] = result.Answer == ""
			}
		}
		if err != nil {
			row["error"] = err.Error()
		}
		if err = encoder.Encode(row); err != nil {
			log.Fatal(err)
		}
	}
}
