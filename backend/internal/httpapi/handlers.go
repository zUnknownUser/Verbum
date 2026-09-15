package httpapi

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"mime"
	"net/http"
	"regexp"
	"strconv"
	"strings"
	"time"
	"unicode/utf8"

	"verbum/backend/internal/dailyverse"
	"verbum/backend/internal/domain"
	"verbum/backend/internal/realtime"
	"verbum/backend/internal/reference"
	"verbum/backend/internal/reqid"
	"verbum/backend/internal/store"
)

type handlers struct {
	store    store.Store
	now      func() time.Time
	realtime realtimeBroker
	embedder queryEmbedder
	asker    asker
	tts      TextToSpeech
}

// realtimeBroker is the small slice of *realtime.Broker these handlers need — a seam so tests
// never make a real OpenAI call, the same reason store.Store is an interface.
type realtimeBroker interface {
	CreateSession(ctx context.Context, model string, expiresIn time.Duration) (realtime.Session, error)
}

// queryEmbedder is the small slice of *embeddings.Client the search handler needs — nil when
// no OPENAI_API_KEY is configured, in which case search stays lexical/entity-only (§27-28).
type queryEmbedder interface {
	Embed(ctx context.Context, text string) ([]float32, error)
}

// passageSearchLimit caps Scripture hits per query; keep it small — this is a search result
// list, not a retrieval-for-synthesis budget (that is Task 12's concern, not this endpoint's).
const passageSearchLimit = 5

// asker is *ask.Service's one entry point — a seam so tests never call OpenAI for real, the
// same reason store.Store is an interface.
type asker interface {
	Ask(ctx context.Context, question string) (domain.AskResponse, error)
}

const maxAskQuestionLength = 500

// GET /v1/entities/{id}
func (h *handlers) entityDetail(w http.ResponseWriter, r *http.Request) {
	d, err := h.store.Detail(r.Context(), r.PathValue("id"))
	if err != nil {
		writeError(w, err, CodeUnknownEntity)
		return
	}
	writeJSON(w, d)
}

// GET /v1/entities?type=person
func (h *handlers) listEntities(w http.ResponseWriter, r *http.Request) {
	kind := domain.EntityType(r.URL.Query().Get("type"))
	switch kind {
	case domain.Person, domain.Place, domain.Event, domain.Theme, domain.Passage, domain.Book, domain.Prophecy, domain.OriginalTerm, domain.HistoricalPeriod:
	default:
		writeProblem(w, http.StatusBadRequest, CodeMalformedRequest, "type must be a BibleEntityType")
		return
	}
	entities, err := h.store.Entities(r.Context(), kind)
	if err != nil {
		writeError(w, err, CodeUnknownEntity)
		return
	}
	writeJSON(w, struct {
		Entities []domain.Entity `json:"entities"`
	}{entities})
}

// GET /v1/entities/{id}/graph?depth=1&limit=12
func (h *handlers) entityGraph(w http.ResponseWriter, r *http.Request) {
	limit := 12
	if v := r.URL.Query().Get("limit"); v != "" {
		n, err := strconv.Atoi(v)
		if err != nil || n < 1 || n > 48 {
			writeProblem(w, http.StatusBadRequest, CodeMalformedRequest, "limit must be 1–48")
			return
		}
		limit = n
	}
	if v := r.URL.Query().Get("depth"); v != "" && v != "1" {
		writeProblem(w, http.StatusBadRequest, CodeMalformedRequest, "only depth=1 is served")
		return
	}
	g, err := h.store.Neighbors(r.Context(), r.PathValue("id"), limit)
	if err != nil {
		writeError(w, err, CodeUnknownEntity)
		return
	}
	writeJSON(w, g)
}

var chapterRef = regexp.MustCompile(`^([1-3]?[A-Za-z]+)\.([0-9]+)$`)

// GET /v1/passages/{1Sam.17}/context
func (h *handlers) passageContext(w http.ResponseWriter, r *http.Request) {
	m := chapterRef.FindStringSubmatch(r.PathValue("reference"))
	if m == nil {
		writeProblem(w, http.StatusBadRequest, CodeMalformedRequest, "reference must be Book.Chapter, e.g. 1Sam.17")
		return
	}
	chapter, _ := strconv.Atoi(m[2])
	c, err := h.store.Context(r.Context(), m[1], chapter)
	if err != nil {
		writeError(w, err, CodeContentUnavailable)
		return
	}
	writeJSON(w, c)
}

// GET /v1/timeline?entity=
func (h *handlers) timeline(w http.ResponseWriter, r *http.Request) {
	t, err := h.store.Timeline(r.Context(), r.URL.Query().Get("entity"))
	if err != nil {
		writeError(w, err, CodeInternal)
		return
	}
	writeJSON(w, t)
}

// GET /v1/search?q=
func (h *handlers) search(w http.ResponseWriter, r *http.Request) {
	q := strings.TrimSpace(r.URL.Query().Get("q"))
	if q == "" || !utf8.ValidString(q) || utf8.RuneCountInString(q) > 200 {
		writeProblem(w, http.StatusBadRequest, CodeMalformedRequest, "q must be 1–200 characters")
		return
	}
	entities, err := h.store.Search(r.Context(), q)
	if err != nil {
		writeError(w, err, CodeInternal)
		return
	}

	passages, err := h.searchPassages(r.Context(), q)
	if err != nil {
		writeError(w, err, CodeInternal)
		return
	}
	writeJSON(w, domain.SearchResponse{Query: q, Passages: passages, Books: []domain.BookHit{}, Entities: entities})
}

// searchPassages is §28's "a direct reference match wins outright": a query that parses as an
// exact, existing verse reference (internal/reference — a narrower parser than the apps' own)
// returns just that verse, skipping hybrid retrieval (and the embedding call) entirely. A
// reference-shaped query that does not exist in the corpus (typo, wrong verse count) falls
// through to hybrid search rather than erroring — §54 logs it either way as a parse/lookup miss.
func (h *handlers) searchPassages(ctx context.Context, q string) ([]domain.PassageReference, error) {
	id := reqid.From(ctx)
	if ref, ok := reference.ParseVerse(q); ok {
		text, err := h.store.PassageText(ctx, "WEB", []domain.PassageReference{ref})
		if err != nil {
			return nil, err
		}
		if _, exists := text[ref.Key()]; exists {
			return []domain.PassageReference{ref}, nil
		}
		// §54 "failed reference parsing": the query looked like a reference but named nothing
		// this corpus has — a typo, a wrong verse count, or a book outside this translation.
		slog.Warn("search: reference parsed but not found in the corpus", "reqID", id, "book", ref.BookID, "chapter", ref.Chapter)
	}

	// A failed/unconfigured embedder degrades to lexical-only passage search, not a failed
	// request: semantic search is an enhancement on top of search that already works (§3.5
	// spirit — never let optional AI-assisted retrieval take down a request that doesn't need it).
	var embedding []float32
	lexicalOnly, _ := ctx.Value(accessContextKey{}).(bool)
	if h.embedder != nil && !lexicalOnly {
		embedStart := time.Now()
		v, err := h.embedder.Embed(ctx, q)
		slog.Info("search: query embedding", "reqID", id, "ms", time.Since(embedStart).Milliseconds(), "ok", err == nil)
		if err != nil {
			slog.Error("query embedding", "reqID", id, "err", err)
		} else {
			embedding = v
		}
	}
	retrievalStart := time.Now()
	passages, err := h.store.SearchPassages(ctx, q, embedding, passageSearchLimit)
	slog.Info("search: passage retrieval", "reqID", id, "ms", time.Since(retrievalStart).Milliseconds(), "hits", len(passages))
	return passages, err
}

// GET /v1/daily-verse?from=2026-09-13&days=7
func (h *handlers) dailyVerse(w http.ResponseWriter, r *http.Request) {
	from := h.now().UTC()
	if v := r.URL.Query().Get("from"); v != "" {
		d, err := time.Parse("2006-01-02", v)
		if err != nil {
			writeProblem(w, http.StatusBadRequest, CodeMalformedRequest, "from must be YYYY-MM-DD")
			return
		}
		from = d
	}
	days := 1
	if v := r.URL.Query().Get("days"); v != "" {
		n, err := strconv.Atoi(v)
		if err != nil || n < 1 || n > 31 {
			writeProblem(w, http.StatusBadRequest, CodeMalformedRequest, "days must be 1–31")
			return
		}
		days = n
	}
	pool, err := h.store.DailyVersePool(r.Context())
	if err != nil || len(pool) == 0 {
		writeError(w, fmt.Errorf("daily verse pool: %w", err), CodeInternal)
		return
	}
	verses := make([]domain.DailyVerse, 0, days)
	for i := 0; i < days; i++ {
		day := from.AddDate(0, 0, i)
		verses = append(verses, domain.DailyVerse{Date: day.Format("2006-01-02"), Reference: dailyverse.Pick(pool, dailyverse.EpochDay(day))})
	}
	writeJSON(w, struct {
		Verses []domain.DailyVerse `json:"verses"`
	}{verses})
}

// POST /v1/realtime/session?model=
//
// Mints one short-lived OpenAI Realtime client secret so a client can connect directly to
// OpenAI (WebRTC/WebSocket) without ever holding the real API key. Not part of the content
// contract (§45): this brokers a third-party credential, it does not read the store.
func (h *handlers) realtimeSession(w http.ResponseWriter, r *http.Request) {
	if h.realtime == nil {
		writeProblem(w, http.StatusServiceUnavailable, CodeRealtimeUnavailable, "realtime is not configured on this server")
		return
	}
	_ = http.NewResponseController(w).SetWriteDeadline(time.Now().Add(20 * time.Second))
	ctx, cancel := context.WithTimeout(r.Context(), 15*time.Second)
	defer cancel()
	session, err := h.realtime.CreateSession(ctx, r.URL.Query().Get("model"), time.Minute)
	if err != nil {
		slog.Error("realtime session", "reqID", reqid.From(ctx), "err", err)
		writeProblem(w, http.StatusBadGateway, CodeInternal, "could not create a realtime session")
		return
	}
	writeJSONNoStore(w, session)
}

// POST /v1/ask {"question": "..."}
//
// Task 12 (§29-31): retrieval-grounded synthesis with a hard citation gate — internal/ask never
// lets the model name a Bible reference itself, only pick from evidence already verified to
// exist. 503 when no OpenAI key is configured; there is no offline/fixture fallback for this
// endpoint, same policy as realtime. The response must never be cached: it is one answer to one
// question, not editorial content (contrast writeJSON's Cache-Control on every other route).
func (h *handlers) ask(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Cache-Control", "no-store")
	if h.asker == nil {
		writeProblem(w, http.StatusServiceUnavailable, CodeAskUnavailable, "ask is not configured on this server")
		return
	}
	media, _, err := mime.ParseMediaType(r.Header.Get("Content-Type"))
	if err != nil || media != "application/json" {
		writeProblem(w, http.StatusUnsupportedMediaType, CodeMalformedRequest, "Content-Type must be application/json")
		return
	}
	var body struct {
		Question string `json:"question"`
	}
	decoder := json.NewDecoder(http.MaxBytesReader(w, r.Body, 8<<10))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(&body); err != nil {
		writeProblem(w, http.StatusBadRequest, CodeMalformedRequest, "body must be JSON {\"question\": string}")
		return
	}
	if err := decoder.Decode(new(any)); err != io.EOF {
		writeProblem(w, http.StatusBadRequest, CodeMalformedRequest, "body must contain one JSON object")
		return
	}
	q := strings.TrimSpace(body.Question)
	if q == "" || !utf8.ValidString(q) || utf8.RuneCountInString(q) > maxAskQuestionLength {
		writeProblem(w, http.StatusBadRequest, CodeMalformedRequest, fmt.Sprintf("question must be 1–%d characters", maxAskQuestionLength))
		return
	}
	ctx, cancel := context.WithTimeout(r.Context(), 30*time.Second)
	defer cancel()
	// Ask's 30-second operation must fit inside the connection's write deadline.
	_ = http.NewResponseController(w).SetWriteDeadline(time.Now().Add(35 * time.Second))
	answer, err := h.asker.Ask(ctx, q)
	if err != nil {
		slog.Error("ask", "reqID", reqid.From(ctx), "err", err)
		writeProblem(w, http.StatusBadGateway, CodeInternal, "could not answer this question")
		return
	}
	writeJSONNoStore(w, answer)
}
