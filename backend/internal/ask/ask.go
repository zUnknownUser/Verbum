// Package ask implements Task 12 — Ask Scripture (§29-31): retrieval-grounded synthesis with a
// hard citation gate. The model never gets to name a Bible reference itself; it only picks
// indexes into a list of passages this package already retrieved and verified exist. An
// out-of-range or missing index is silently dropped, never trusted — this is what makes
// "reject or regenerate on a fabricated verse" (§31) structural rather than best-effort.
//
// Pipeline (§29): hybrid retrieval -> evidence text -> prompt assembly -> LLM synthesis ->
// citation validation -> entity linking -> response. Intent/reference extraction is folded
// into the existing hybrid retrieval (internal/store.SearchPassages) rather than a separate
// stage — see backend/README.md for why. Entity linking only ever names an entity that has a
// curated key passage or sourced occurrence covering a passage the model actually cited
// (never natural-language matching against the question), so it inherits the same fabrication guarantees as passage citations.
package ask

import (
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"sort"
	"strings"
	"time"

	"verbum/backend/internal/domain"
	"verbum/backend/internal/reqid"
	"verbum/backend/internal/store"
)

// Retriever is the slice of store.Store this package needs — never the whole interface, so a
// test double stays small.
type Retriever interface {
	SearchPassages(ctx context.Context, queryText string, queryEmbedding []float32, limit int) ([]domain.PassageReference, error)
	PassageText(ctx context.Context, translation string, refs []domain.PassageReference) (map[string]string, error)
	EntitiesForPassages(ctx context.Context, refs []domain.PassageReference) ([]string, error)
}

// Source metadata is optional for legacy/fixture retrievers. It attributes structured
// entity links; it never lets non-Scripture text pass the numbered citation gate.
type entitySourceReader interface {
	EntitySources(ctx context.Context, ids []string) ([]domain.SourceReference, error)
}

type Embedder interface {
	Embed(ctx context.Context, text string) ([]float32, error)
}

type Synthesizer interface {
	Complete(ctx context.Context, systemPrompt, userPrompt string) (string, error)
}

// Service holds everything Ask needs. Embedder may be nil (degrades to lexical-only
// retrieval, same as /v1/search); Store and Synthesizer must not be.
type Service struct {
	Store         Retriever
	Embedder      Embedder
	Synthesizer   Synthesizer
	Translation   string // e.g. "WEB" — must match what the pipeline embedded (§34: English only until PT-BR is licensed)
	EvidenceLimit int    // passages retrieved per question; <=0 defaults to 6
}

type evidence struct {
	ref  domain.PassageReference
	text string
}

func evidenceKey(ref domain.PassageReference) string { return ref.Key() }

// modelReply is the only shape the model is trusted to produce. Notably absent: any field that
// would let it name a passage, entity or source directly — see the package doc.
type modelReply struct {
	Answer               string `json:"answer"`
	Summary              string `json:"summary"`
	CitedPassageIndexes  []int  `json:"citedPassageIndexes"`
	Confidence           string `json:"confidence"`
	InterpretiveVariance bool   `json:"interpretiveVariance"`
}

func noEvidenceResponse(languages ...string) domain.AskResponse {
	summary := "No sufficiently relevant Scripture passages were found for this question."
	if len(languages) > 0 && languages[0] == "pt-BR" {
		summary = "Não foram encontradas passagens bíblicas suficientemente relevantes para esta pergunta."
	}
	return domain.AskResponse{
		Answer:               "",
		Summary:              summary,
		PassageReferences:    []domain.PassageReference{},
		EntityReferences:     []string{},
		SourceReferences:     []domain.SourceReference{},
		Confidence:           "low",
		InterpretiveVariance: false,
	}
}

// Ask never begins generation before retrieval has something to ground it on (§73): a query
// with no evidence returns noEvidenceResponse() without ever calling the Synthesizer.
func (s *Service) Ask(ctx context.Context, question string) (domain.AskResponse, error) {
	q := strings.TrimSpace(question)
	if q == "" {
		return domain.AskResponse{}, fmt.Errorf("question must not be empty")
	}
	translation := s.Translation
	if translation == "" {
		translation = "WEB"
	}
	limit := s.EvidenceLimit
	if limit <= 0 {
		limit = 6
	}

	id := reqid.From(ctx)

	// A failed/unconfigured embedder degrades to lexical-only retrieval, same policy as
	// /v1/search (internal/httpapi.search) — never a failed Ask over this alone.
	var embedding []float32
	if s.Embedder != nil {
		embedStart := time.Now()
		v, err := s.Embedder.Embed(ctx, q)
		slog.Info("ask: query embedding", "reqID", id, "ms", time.Since(embedStart).Milliseconds(), "ok", err == nil)
		if err != nil {
			slog.Error("ask: query embedding failed", "reqID", id, "err", err)
		} else {
			embedding = v
		}
	}

	retrievalStart := time.Now()
	refs, err := s.Store.SearchPassages(ctx, q, embedding, limit)
	if err != nil {
		return domain.AskResponse{}, fmt.Errorf("ask: retrieve evidence: %w", err)
	}
	anchors := selectedPassages(ctx)
	if len(anchors) > limit {
		limit = len(anchors)
	}
	if len(anchors) > 0 {
		combined := append([]domain.PassageReference{}, anchors...)
		seen := map[string]bool{}
		for _, ref := range anchors {
			seen[ref.Key()] = true
		}
		for _, ref := range refs {
			if !seen[ref.Key()] {
				combined = append(combined, ref)
				seen[ref.Key()] = true
			}
		}
		if len(combined) > limit {
			combined = combined[:limit]
		}
		refs = combined
	}
	if len(refs) == 0 {
		slog.Info("ask: retrieval", "reqID", id, "ms", time.Since(retrievalStart).Milliseconds(), "hits", 0)
		return noEvidenceResponse(store.Language(ctx)), nil
	}
	texts, err := s.Store.PassageText(ctx, translation, refs)
	if err != nil {
		return domain.AskResponse{}, fmt.Errorf("ask: load evidence text: %w", err)
	}
	for _, ref := range anchors {
		if texts[ref.Key()] == "" {
			return noEvidenceResponse(store.Language(ctx)), nil
		}
	}
	items := make([]evidence, 0, len(refs))
	for _, ref := range refs {
		if text, ok := texts[evidenceKey(ref)]; ok && text != "" {
			items = append(items, evidence{ref: ref, text: text})
		}
	}
	slog.Info("ask: retrieval", "reqID", id, "ms", time.Since(retrievalStart).Milliseconds(), "hits", len(items))
	if len(items) == 0 {
		// Retrieval named passages but the corpus has no text for them — never happens with a
		// healthy scripture_verses table, but §3.5 says never fabricate around a gap either.
		return noEvidenceResponse(store.Language(ctx)), nil
	}

	synthStart := time.Now()
	prompt := buildUserPrompt(q, items)
	if len(anchors) > 0 {
		prompt += fmt.Sprintf("\nThe reader selected the first %d excerpt(s). Interpret the question in that specific passage context; use other excerpts only when relevant. Do not invent who is speaking or historical context absent from the evidence.", len(anchors))
	}
	raw, err := s.Synthesizer.Complete(ctx, localizedSystemPrompt(store.Language(ctx)), prompt)
	slog.Info("ask: synthesis", "reqID", id, "ms", time.Since(synthStart).Milliseconds(), "ok", err == nil)
	if err != nil {
		return domain.AskResponse{}, fmt.Errorf("ask: synthesize: %w", err)
	}
	var reply modelReply
	if err := json.Unmarshal([]byte(raw), &reply); err != nil {
		return domain.AskResponse{}, fmt.Errorf("ask: parse synthesis response: %w", err)
	}

	cited, dropped := validateCitations(reply.CitedPassageIndexes, items)
	if dropped > 0 {
		slog.Warn("ask: citation validation dropped out-of-range indexes", "reqID", id,
			"dropped", dropped, "modelCited", len(reply.CitedPassageIndexes), "evidenceSize", len(items))
	}
	if len(cited) == 0 || strings.TrimSpace(reply.Answer) == "" {
		// §31: cite relevant passages is not optional. An answer that cites nothing verifiable
		// is treated as no answer, not shown as if it were grounded.
		return noEvidenceResponse(store.Language(ctx)), nil
	}

	confidence := reply.Confidence
	switch confidence {
	case "low", "medium", "high":
	default:
		confidence = "low"
	}
	if len(items) < 2 {
		confidence = "low" // thin evidence caps confidence regardless of the model's own claim
	}

	answer := reply.Answer
	if needsProfessionalHelpNote(q) && !mentionsProfessionalHelp(answer) {
		if store.Language(ctx) == "pt-BR" {
			answer += professionalHelpNotePT
		} else {
			answer += professionalHelpNote
		}
	}

	// Entity linking rides on citations already validated above — an entity is only ever named
	// here because a curated key passage or sourced occurrence covers a cited passage,
	// rather than a name match in the question. A failure here is an enrichment lost,
	// not an answer lost: it never turns a good answer into an error.
	entityIDs, err := s.Store.EntitiesForPassages(ctx, cited)
	if err != nil {
		slog.Error("ask: entity linking failed", "err", err)
		entityIDs = nil
	}
	sort.Strings(entityIDs)
	if entityIDs == nil {
		entityIDs = []string{}
	}

	sources := translationSource(translation)
	if store.Language(ctx) == "pt-BR" && translation == "WEB" {
		sources[0].Citation = "World English Bible (domínio público) — fonte dos trechos; resposta em português por paráfrase"
	}
	if reader, ok := s.Store.(entitySourceReader); ok && len(entityIDs) > 0 {
		extra, sourceErr := reader.EntitySources(ctx, entityIDs)
		if sourceErr != nil {
			slog.Error("ask: entity provenance unavailable", "err", sourceErr)
		} else {
			seen := map[string]bool{}
			for _, source := range sources {
				seen[source.ID] = true
			}
			for _, source := range extra {
				if !seen[source.ID] {
					sources = append(sources, source)
					seen[source.ID] = true
				}
			}
		}
	}
	return domain.AskResponse{
		Answer:               answer,
		Summary:              reply.Summary,
		PassageReferences:    cited,
		EntityReferences:     entityIDs,
		SourceReferences:     sources,
		Confidence:           confidence,
		InterpretiveVariance: reply.InterpretiveVariance,
	}, nil
}

// validateCitations keeps only in-range, deduplicated indexes, mapped back to the trusted
// evidence this package retrieved — never the model's own text. Order follows the model's
// citation order (relevance-first), not evidence-retrieval order. dropped counts indexes that
// were out of range — a signal §54 wants logged ("failed citation validation"), not just acted on.
func validateCitations(indexes []int, items []evidence) (cited []domain.PassageReference, dropped int) {
	seen := map[string]bool{}
	cited = make([]domain.PassageReference, 0, len(indexes))
	for _, idx := range indexes {
		if idx < 0 || idx >= len(items) {
			dropped++
			continue
		}
		key := evidenceKey(items[idx].ref)
		if seen[key] {
			continue
		}
		seen[key] = true
		cited = append(cited, items[idx].ref)
	}
	return cited, dropped
}

func translationSource(translation string) []domain.SourceReference {
	if translation != "WEB" {
		return []domain.SourceReference{}
	}
	url := "https://ebible.org/web/"
	return []domain.SourceReference{{ID: "web-translation", Citation: "World English Bible (public domain)", URL: &url}}
}
