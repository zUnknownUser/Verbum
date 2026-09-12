// Package httpapi serves api/openapi.yaml. One file per concern: routes here,
// one handler per route in handlers.go, the error shape in problem.go.
// Standard library only — net/http's router (Go ≥ 1.22) is enough for this API.
package httpapi

import (
	"log/slog"
	"net/http"
	"time"

	"verbum/backend/internal/store"
)

// New builds the handler. Everything the routes need comes in through here.
func New(s store.Store, now func() time.Time) http.Handler {
	h := &handlers{store: s, now: now}
	mux := http.NewServeMux()
	mux.HandleFunc("GET /healthz", func(w http.ResponseWriter, _ *http.Request) { w.Write([]byte("ok\n")) })
	mux.HandleFunc("GET /v1/entities", h.listEntities)
	mux.HandleFunc("GET /v1/entities/{id}", h.entityDetail)
	mux.HandleFunc("GET /v1/entities/{id}/graph", h.entityGraph)
	mux.HandleFunc("GET /v1/passages/{reference}/context", h.passageContext)
	mux.HandleFunc("GET /v1/timeline", h.timeline)
	mux.HandleFunc("GET /v1/search", h.search)
	mux.HandleFunc("GET /v1/daily-verse", h.dailyVerse)
	return logging(mux)
}

// logging writes one line per request. Swap for OpenTelemetry later (§54).
func logging(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()
		rec := &statusRecorder{ResponseWriter: w, status: http.StatusOK}
		next.ServeHTTP(rec, r)
		slog.Info("request", "method", r.Method, "path", r.URL.Path, "status", rec.status, "ms", time.Since(start).Milliseconds())
	})
}

type statusRecorder struct {
	http.ResponseWriter
	status int
}

func (r *statusRecorder) WriteHeader(code int) {
	r.status = code
	r.ResponseWriter.WriteHeader(code)
}
