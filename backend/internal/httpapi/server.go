// Package httpapi serves api/openapi.yaml. One file per concern: routes here,
// one handler per route in handlers.go, the error shape in problem.go.
// Standard library only — net/http's router (Go ≥ 1.22) is enough for this API.
package httpapi

import (
	"bufio"
	"context"
	"log/slog"
	"net"
	"net/http"
	"strings"
	"time"

	"verbum/backend/internal/reqid"
	"verbum/backend/internal/store"
)

// New builds the handler. Everything the routes need comes in through here.
// rt, embedder and asker may all be nil: the realtime/ask endpoints then answer 503 instead of
// panicking, and search stays lexical/entity-only (OPENAI_API_KEY is optional configuration,
// unlike the store). Pass a literal nil at the call site for any of them, never a nil-valued
// variable of the concrete pointer type — see cmd/api/main.go's comment on why.
func New(s store.Store, now func() time.Time, rt realtimeBroker, embedder queryEmbedder, asker asker, speech TextToSpeech, economic ...EconomicOptions) http.Handler {
	h := &handlers{store: s, now: now, realtime: rt, embedder: embedder, asker: asker, tts: speech}
	mux := http.NewServeMux()
	if len(economic) > 0 {
		e := economic[0]
		h.asker = e.WrapAsk(asker)
		h.embedder = e.WrapEmbeddings(embedder)
		h.tts = e.WrapSpeech(speech)
		if e.VoiceTickets != nil {
			h.realtime = e.VoiceTickets
		} else {
			h.realtime = nil
		}
		mux.HandleFunc("GET /v1/me/usage", func(w http.ResponseWriter, r *http.Request) {
			status, err := e.Usage.Status(r.Context())
			if err != nil {
				writeUsageProblem(w, err)
				return
			}
			writeJSONNoStore(w, status)
		})
		if e.VoiceRelay != nil {
			mux.Handle("GET /v1/realtime/connect", e.VoiceRelay)
		}
	}
	mux.HandleFunc("GET /healthz", func(w http.ResponseWriter, _ *http.Request) { w.Write([]byte("ok\n")) })
	mux.HandleFunc("GET /readyz", h.ready)
	mux.HandleFunc("GET /v1/entities", h.listEntities)
	mux.HandleFunc("GET /v1/entities/{id}", h.entityDetail)
	mux.HandleFunc("GET /v1/entities/{id}/graph", h.entityGraph)
	mux.HandleFunc("GET /v1/passages/{reference}/context", h.passageContext)
	mux.HandleFunc("GET /v1/timeline", h.timeline)
	mux.HandleFunc("GET /v1/search", h.search)
	mux.HandleFunc("GET /v1/daily-verse", h.dailyVerse)
	mux.HandleFunc("POST /v1/realtime/session", h.realtimeSession)
	mux.HandleFunc("POST /v1/ask", h.ask)
	mux.HandleFunc("POST /v1/tts", h.synthesizeSpeech)
	mux.HandleFunc("GET /v1/tts/config", h.speechConfiguration)
	return logging(mux)
}

// Deployment readiness exercises the existing content store without paid providers.
// An empty database must not replace a working API during a rollout.
func (h *handlers) ready(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Cache-Control", "no-store")
	ctx, cancel := context.WithTimeout(r.Context(), 2*time.Second)
	defer cancel()
	pool, err := h.store.DailyVersePool(ctx)
	if err != nil || len(pool) == 0 {
		http.Error(w, "not ready", http.StatusServiceUnavailable)
		return
	}
	w.Write([]byte("ready\n"))
}

// logging writes one structured line per request, tagged with a request id (internal/reqid)
// that handlers.go's own latency/failure log lines carry too, so the two can be correlated
// without a tracing backend (§54; see internal/reqid's doc for why this, not OpenTelemetry, yet).
func logging(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		language := r.URL.Query().Get("lang")
		if language == "" {
			language = strings.Split(strings.Split(r.Header.Get("Accept-Language"), ",")[0], ";")[0]
		}
		w.Header().Add("Vary", "Accept-Language")
		ctx := store.WithLanguage(reqid.NewContext(r.Context()), language)
		ctx = context.WithValue(ctx, idempotencyKey{}, r.Header.Get("Idempotency-Key"))
		w.Header().Set("Content-Language", store.Language(ctx))
		start := time.Now()
		rec := &statusRecorder{ResponseWriter: w, status: http.StatusOK}
		next.ServeHTTP(rec, r.WithContext(ctx))
		slog.Info("request", "reqID", reqid.From(ctx), "method", r.Method, "path", r.URL.Path, "status", rec.status, "ms", time.Since(start).Milliseconds())
	})
}

type statusRecorder struct {
	http.ResponseWriter
	status int
}

func (r *statusRecorder) Unwrap() http.ResponseWriter { return r.ResponseWriter }

func (r *statusRecorder) WriteHeader(code int) {
	r.status = code
	r.ResponseWriter.WriteHeader(code)
}

func (r *statusRecorder) Hijack() (net.Conn, *bufio.ReadWriter, error) {
	return http.NewResponseController(r.ResponseWriter).Hijack()
}
