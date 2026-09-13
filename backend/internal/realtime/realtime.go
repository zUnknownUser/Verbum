// Package realtime brokers OpenAI Realtime API session credentials so the real API key never
// reaches a client app. A client calls this backend once per voice session and gets back a
// short-lived client secret (OpenAI's `ek_...` ephemeral token); it then connects directly to
// OpenAI over WebRTC/WebSocket with that secret. No audio, transcript or model output passes
// through this server — this package only ever makes one small REST call per session request.
package realtime

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"
)

const defaultEndpoint = "https://api.openai.com/v1/realtime/client_secrets"

// DefaultModel is OpenAI's current Realtime model name. A caller can request another one
// per-session (handlers.go accepts a `model` query parameter); this is only the fallback.
const DefaultModel = "gpt-realtime"

// Session is what a client needs to connect. Only the fields required for that are kept —
// OpenAI's response is not otherwise forwarded.
type Session struct {
	ClientSecret string `json:"clientSecret"`
	ExpiresAt    int64  `json:"expiresAt"` // unix seconds; when the secret stops being usable to START a session
	Model        string `json:"model"`
}

// Broker holds the real API key; every server process should build exactly one and share it.
type Broker struct {
	apiKey   string
	endpoint string
	client   *http.Client
}

func New(apiKey string) *Broker {
	return &Broker{apiKey: apiKey, endpoint: defaultEndpoint, client: &http.Client{Timeout: 10 * time.Second}}
}

// CreateSession mints one ephemeral client secret, valid to start a session for expiresIn.
// The session it starts may run longer than that; this only bounds how long the secret itself
// can be redeemed.
func (b *Broker) CreateSession(ctx context.Context, model string, expiresIn time.Duration) (Session, error) {
	if model == "" {
		model = DefaultModel
	}
	if expiresIn <= 0 {
		expiresIn = time.Minute
	}
	body, err := json.Marshal(map[string]any{
		"session": map[string]any{"type": "realtime", "model": model},
		"expires_after": map[string]any{
			"anchor":  "created_at",
			"seconds": int(expiresIn.Seconds()),
		},
	})
	if err != nil {
		return Session{}, err
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, b.endpoint, bytes.NewReader(body))
	if err != nil {
		return Session{}, err
	}
	req.Header.Set("Authorization", "Bearer "+b.apiKey)
	req.Header.Set("Content-Type", "application/json")

	resp, err := b.client.Do(req)
	if err != nil {
		return Session{}, fmt.Errorf("realtime session request: %w", err)
	}
	defer resp.Body.Close()
	raw, err := io.ReadAll(io.LimitReader(resp.Body, 1<<20))
	if err != nil {
		return Session{}, fmt.Errorf("realtime session response: %w", err)
	}
	if resp.StatusCode != http.StatusOK {
		// Never include the response body: it echoes the request, which never contains the
		// key, but there is no reason to widen what ends up in logs from here.
		return Session{}, fmt.Errorf("realtime session request failed: status %d", resp.StatusCode)
	}
	var parsed struct {
		Value     string `json:"value"`
		ExpiresAt int64  `json:"expires_at"`
	}
	if err := json.Unmarshal(raw, &parsed); err != nil {
		return Session{}, fmt.Errorf("realtime session response: %w", err)
	}
	if parsed.Value == "" {
		return Session{}, fmt.Errorf("realtime session response had no client secret")
	}
	return Session{ClientSecret: parsed.Value, ExpiresAt: parsed.ExpiresAt, Model: model}, nil
}
