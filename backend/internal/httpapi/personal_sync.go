package httpapi

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"time"
	"verbum/backend/internal/personalsync"
	"verbum/backend/internal/usage"
)

type PersonalDataStore interface {
	Exchange(context.Context, string, personalsync.Request) (personalsync.Response, error)
	Delete(context.Context, string) error
}

func (h *handlers) personalSync(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Cache-Control", "no-store")
	p := usage.Identity(r.Context())
	if p.UID == "" {
		writeProblem(w, 401, CodeUnauthenticated, "Authentication required")
		return
	}
	if p.Anonymous {
		writeProblem(w, 403, "account_required", "Sign in to sync personal data")
		return
	}
	if h.personal == nil {
		writeProblem(w, 503, "sync_unavailable", "Personal storage unavailable")
		return
	}
	var request personalsync.Request
	decoder := json.NewDecoder(http.MaxBytesReader(w, r.Body, 4*1024*1024))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(&request); err != nil {
		writeProblem(w, 400, CodeMalformedRequest, "Invalid sync request")
		return
	}
	if decoder.Decode(&struct{}{}) != io.EOF || personalsync.Validate(request, h.now()) != nil {
		writeProblem(w, 400, CodeMalformedRequest, "Invalid sync request")
		return
	}
	ctx, cancel := context.WithTimeout(r.Context(), 10*time.Second)
	defer cancel()
	result, err := h.personal.Exchange(ctx, p.UID, request)
	if err != nil {
		personalError(w, err)
		return
	}
	writeJSONNoStore(w, result)
}
func (h *handlers) deletePersonalData(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Cache-Control", "no-store")
	p := usage.Identity(r.Context())
	if p.UID == "" {
		writeProblem(w, 401, CodeUnauthenticated, "Authentication required")
		return
	}
	if !p.Anonymous && (p.AuthenticatedAt <= 0 || h.now().Unix()-p.AuthenticatedAt > 300) {
		writeProblem(w, 403, "recent_login_required", "Reauthenticate before deleting personal data")
		return
	}

	if h.personal == nil {
		writeProblem(w, 503, "sync_unavailable", "Personal storage unavailable")
		return
	}
	ctx, cancel := context.WithTimeout(r.Context(), 10*time.Second)
	defer cancel()
	if err := h.personal.Delete(ctx, p.UID); err != nil {
		personalError(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}
func personalError(w http.ResponseWriter, err error) {
	switch {
	case errors.Is(err, personalsync.ErrDeleted):
		writeProblem(w, 403, "account_data_deleted", "Account deletion is pending or complete")
	case errors.Is(err, personalsync.ErrInvalid):
		writeProblem(w, 400, CodeMalformedRequest, "Invalid sync cursor")
	case errors.Is(err, personalsync.ErrCapacity):
		writeProblem(w, 409, "personal_storage_full", "Personal storage limit reached; local changes remain on device")
	default:
		writeProblem(w, 503, "sync_unavailable", "Personal storage unavailable")
	}
}
