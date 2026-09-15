// Package httpapi serves api/openapi.yaml. One file per concern: routes here,
// one handler per route in handlers.go, the error shape in problem.go.
// Standard library only — net/http's router (Go ≥ 1.22) is enough for this API.
package httpapi

import (
	"log/slog"
	"net/http"
	"time"

	"verbum/backend/internal/reqid"
	"verbum/backend/internal/store"
)

// New builds the handler. Everything the routes need comes in through here.
// rt, embedder and asker may all be nil: the realtime/ask endpoints then answer 503 instead of
// panicking, and search stays lexical/entity-only (OPENAI_API_KEY is optional configuration,
// unlike the store). Pass a literal nil at the call site for any of them, never a nil-valued
// variable of the concrete pointer type — see cmd/api/main.go's comment on why.
func New(s store.Store, now func() time.Time, rt realtimeBroker, embedder queryEmbedder, asker asker, speech TextToSpeech) http.Handler {
	h := &handlers{store: s, now: now, realtime: rt, embedder: embedder, asker: asker, tts: speech}
	mux := http.NewServeMux()
	mux.HandleFunc("GET /healthz", func(w http.ResponseWriter, _ *http.Request) { w.Write([]byte("ok\n")) })
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

// logging writes one structured line per request, tagged with a request id (internal/reqid)
// that handlers.go's own latency/failure log lines carry too, so the two can be correlated
// without a tracing backend (§54; see internal/reqid's doc for why this, not OpenTelemetry, yet).
func logging(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		ctx := store.WithLanguage(reqid.NewContext(r.Context()), r.URL.Query().Get("lang"))
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
