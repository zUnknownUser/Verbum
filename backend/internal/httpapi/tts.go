package httpapi

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"log/slog"
	"mime"
	"net/http"
	"time"

	"verbum/backend/internal/reqid"
	"verbum/backend/internal/tts"
)

// TextToSpeech is the existing client-injection pattern, not a second service registry.
// Chapter caching and segmentation stay inside the existing TTS service.
type TextToSpeech interface {
	Synthesize(context.Context, tts.Request) ([]byte, error)
}

func (h *handlers) synthesizeSpeech(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Cache-Control", "no-store")
	if h.tts == nil {
		writeProblem(w, 503, CodeTTSUnavailable, "text-to-speech is not configured")
		return
	}
	media, _, err := mime.ParseMediaType(r.Header.Get("Content-Type"))
	if err != nil || media != "application/json" {
		writeProblem(w, 415, CodeMalformedRequest, "Content-Type must be application/json")
		return
	}
	r.Body = http.MaxBytesReader(w, r.Body, 1<<20)
	decoder := json.NewDecoder(r.Body)
	decoder.DisallowUnknownFields()
	var input tts.Request
	if err = decoder.Decode(&input); err == nil {
		var extra any
		if decoder.Decode(&extra) != io.EOF {
			err = errors.New("expected one JSON object")
		}
	}
	if err != nil {
		writeProblem(w, 400, CodeMalformedRequest, "expected one speech request JSON object (maximum 1 MiB)")
		return
	}
	// Override the short server write deadline only for whole-chapter generation.
	_ = http.NewResponseController(w).SetWriteDeadline(time.Now().Add(tts.GenerationTimeout + 30*time.Second))
	ctx, cancel := context.WithTimeout(r.Context(), tts.GenerationTimeout)
	defer cancel()
	audio, err := h.tts.Synthesize(ctx, input)
	if err != nil {
		status, code, message := http.StatusBadGateway, CodeTTSFailed, "speech generation failed"
		switch {
		case errors.Is(err, tts.ErrInvalidInput):
			status, code, message = 400, CodeMalformedRequest, err.Error()
		case errors.Is(err, tts.ErrCredentials), errors.Is(err, tts.ErrUnavailable):
			status, code, message = 503, CodeTTSUnavailable, "speech provider unavailable; check server configuration"
		case errors.Is(err, tts.ErrQuota):
			status, code, message = 429, CodeTTSRateLimited, "speech provider quota exhausted"
		case errors.Is(err, tts.ErrTimeout), errors.Is(err, context.DeadlineExceeded):
			status, code, message = 504, CodeTTSTimeout, "speech generation timed out"
		case errors.Is(err, context.Canceled):
			status, code, message = 408, CodeTTSFailed, "speech request cancelled"
		}
		slog.Warn("tts failed", "reqID", reqid.From(r.Context()), "code", code, "status", status)
		writeProblem(w, status, code, message)
		return
	}
	if len(audio) == 0 {
		writeProblem(w, 502, CodeTTSFailed, "speech provider returned no audio")
		return
	}
	w.Header().Set("Content-Type", "audio/mpeg")
	w.Header().Set("Content-Disposition", `attachment; filename="speech.mp3"`)
	w.Header().Set("X-Content-Type-Options", "nosniff")
	_, _ = w.Write(audio)
}
