package httpapi

import (
	"encoding/json"
	"errors"
	"log/slog"
	"net/http"

	"verbum/backend/internal/store"
)

// Problem is the one error shape (§52). Apps switch on Code; Message is for logs.
type Problem struct {
	Code    string `json:"code"`
	Message string `json:"message"`
}

const (
	CodeUnknownEntity       = "unknown_entity"
	CodeUnknownBook         = "unknown_book"
	CodeContentUnavailable  = "content_unavailable"
	CodeMalformedRequest    = "malformed_request"
	CodeInternal            = "internal"
	CodeRealtimeUnavailable = "realtime_unavailable"
	CodeAskUnavailable      = "ask_unavailable"
	CodeTTSUnavailable      = "tts_unavailable"
	CodeTTSRateLimited      = "tts_rate_limited"
	CodeTTSTimeout          = "tts_timeout"
	CodeTTSFailed           = "tts_failed"
	CodeUnauthenticated     = "unauthenticated"
	CodeAuthUnavailable     = "auth_unavailable"
	CodeRateLimited         = "rate_limited"
)

func writeProblem(w http.ResponseWriter, status int, code, message string) {
	w.Header().Set("Content-Type", "application/problem+json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(Problem{Code: code, Message: message})
}

// writeError maps store errors to Problems. Anything unexpected is a 500 with
// the detail logged, never sent (§52 "never show raw backend errors").
func writeError(w http.ResponseWriter, err error, notFoundCode string) {
	if errors.Is(err, store.ErrNotFound) {
		writeProblem(w, http.StatusNotFound, notFoundCode, err.Error())
		return
	}
	slog.Error("request failed", "err", err)
	writeProblem(w, http.StatusInternalServerError, CodeInternal, "internal error")
}

func writeJSON(w http.ResponseWriter, v any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	// Content is editorial and changes rarely; let clients and CDNs keep it a while.
	w.Header().Set("Cache-Control", "public, max-age=3600")
	_ = json.NewEncoder(w).Encode(v)
}

// writeJSONNoStore is writeJSON for responses that must never be cached or shared — today,
// only the realtime ephemeral credential.
func writeJSONNoStore(w http.ResponseWriter, v any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.Header().Set("Cache-Control", "no-store")
	_ = json.NewEncoder(w).Encode(v)
}
