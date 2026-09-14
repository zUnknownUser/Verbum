package httpapi

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"verbum/backend/internal/tts"
)

func TestSpeechConfigurationWithoutProvider(t *testing.T) {
	h := &handlers{}
	w := httptest.NewRecorder()
	h.speechConfiguration(w, httptest.NewRequest(http.MethodGet, "/v1/tts/config?language=pt-BR", nil))
	var body struct {
		Version string `json:"version"`
	}
	want, _ := tts.AudioVersion("pt-BR")
	if w.Code != 200 || json.Unmarshal(w.Body.Bytes(), &body) != nil || body.Version != want {
		t.Fatal("invalid manifest", w.Code, w.Body.String())
	}
	if w.Header().Get("Cache-Control") != "public, max-age=3600" {
		t.Fatal("missing cache policy")
	}
	w = httptest.NewRecorder()
	h.speechConfiguration(w, httptest.NewRequest(http.MethodGet, "/v1/tts/config", nil))
	if w.Code != 400 {
		t.Fatal("missing language accepted")
	}
}
