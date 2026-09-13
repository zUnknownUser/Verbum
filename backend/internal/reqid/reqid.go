// Package reqid gives every request a short, log-correlatable id. This is deliberately not a
// full OpenTelemetry trace: there is no exporter/collector for anyone to send spans to yet, and
// wiring one in would be a real new infrastructure dependency nobody has chosen (a backend, a
// vendor). A request id threaded through context and into every structured log line for that
// request already satisfies §54's concrete asks — correlating retrieval/AI latency and failures
// back to one request — without that dependency. Swapping this for real otel spans later is a
// contained change: the same call sites that read a request id today would read a span instead.
package reqid

import (
	"context"
	"crypto/rand"
	"encoding/hex"
)

type contextKey struct{}

// NewContext attaches a fresh id. Call this once, at the edge (httpapi's logging middleware).
func NewContext(ctx context.Context) context.Context {
	var b [6]byte
	_, _ = rand.Read(b[:])
	return context.WithValue(ctx, contextKey{}, hex.EncodeToString(b[:]))
}

// From returns the request id, or "-" if none was attached (e.g. a test calling a handler
// method directly, or a background job with no HTTP request behind it).
func From(ctx context.Context) string {
	if v, ok := ctx.Value(contextKey{}).(string); ok {
		return v
	}
	return "-"
}
