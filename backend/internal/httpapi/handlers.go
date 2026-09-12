package httpapi

import (
	"fmt"
	"net/http"
	"regexp"
	"strconv"
	"strings"
	"time"

	"verbum/backend/internal/dailyverse"
	"verbum/backend/internal/domain"
	"verbum/backend/internal/store"
)

type handlers struct {
	store store.Store
	now   func() time.Time
}

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
	if q == "" || len(q) > 200 {
		writeProblem(w, http.StatusBadRequest, CodeMalformedRequest, "q must be 1–200 characters")
		return
	}
	entities, err := h.store.Search(r.Context(), q)
	if err != nil {
		writeError(w, err, CodeInternal)
		return
	}
	writeJSON(w, domain.SearchResponse{Query: q, Passages: []domain.PassageReference{}, Books: []domain.BookHit{}, Entities: entities})
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
