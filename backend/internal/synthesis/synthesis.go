// Package synthesis calls OpenAI chat completions to synthesize an Ask answer (§29-31) from
// evidence internal/ask has already retrieved and validated exists. This package only ever
// talks to OpenAI; it has no opinion about what a valid answer looks like — internal/ask owns
// every guardrail and citation check on what comes back.
package synthesis

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"
	"verbum/backend/internal/usage"
)

const endpoint = "https://api.openai.com/v1/chat/completions"

// DefaultModel is the explicitly selected cost-sensitive Ask model. Pipeline extraction,
// embeddings and realtime voice keep their separate models.
const DefaultModel = "gpt-5.6-luna"
const legacyModel = "gpt-4o-mini"

type Client struct {
	apiKey   string
	model    string
	endpoint string
	client   *http.Client
}

func New(apiKey, model string) *Client {
	if model == "" {
		model = DefaultModel
	}
	return &Client{apiKey: apiKey, model: model, endpoint: endpoint, client: &http.Client{Timeout: 30 * time.Second}}
}

// Complete returns the raw JSON text of the model's one reply (response_format=json_object).
// internal/ask parses and validates it; this package does not look inside it.
func (c *Client) Complete(ctx context.Context, systemPrompt, userPrompt string) (string, error) {
	if len(systemPrompt)+len(userPrompt) > 32_000 {
		return "", fmt.Errorf("prompt exceeds bound")
	}
	if c.model != DefaultModel && c.model != legacyModel {
		return "", usage.Unavailable()
	}
	payload := map[string]any{
		"model":                 c.model,
		"max_completion_tokens": 1024,
		"response_format":       map[string]string{"type": "json_object"},
		"messages": []map[string]string{
			{"role": "system", "content": systemPrompt},
			{"role": "user", "content": userPrompt},
		},
	}
	if c.model == DefaultModel {
		payload["reasoning_effort"] = "none"
	} else {
		payload["temperature"] = 0
	}
	if uid := usage.Identity(ctx).UID; uid != "" {
		payload["safety_identifier"] = usage.Hash("openai-safety", uid)
	}
	body, err := json.Marshal(payload)
	if err != nil {
		return "", err
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, c.endpoint, bytes.NewReader(body))
	if err != nil {
		return "", err
	}
	req.Header.Set("Authorization", "Bearer "+c.apiKey)
	req.Header.Set("Content-Type", "application/json")

	usage.Attempt(ctx)
	resp, err := c.client.Do(req)
	if err != nil {
		return "", fmt.Errorf("synthesis request: %w", err)
	}
	defer resp.Body.Close()
	raw, err := io.ReadAll(io.LimitReader(resp.Body, 4<<20))
	if err != nil {
		return "", fmt.Errorf("synthesis response: %w", err)
	}
	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("synthesis request failed: status %d", resp.StatusCode)
	}
	var parsed struct {
		Usage *struct {
			Prompt     int64 `json:"prompt_tokens"`
			Completion int64 `json:"completion_tokens"`
			Details    *struct {
				Cached  int64 `json:"cached_tokens"`
				Written int64 `json:"cache_write_tokens"`
			} `json:"prompt_tokens_details"`
		} `json:"usage"`
		Choices []struct {
			Message struct {
				Content string `json:"content"`
			} `json:"message"`
		} `json:"choices"`
	}
	if err := json.Unmarshal(raw, &parsed); err != nil {
		return "", fmt.Errorf("synthesis response: %w", err)
	}
	if len(parsed.Choices) == 0 || parsed.Choices[0].Message.Content == "" {
		return "", fmt.Errorf("synthesis response had no content")
	}
	if parsed.Usage != nil {
		if parsed.Usage.Prompt >= 0 && parsed.Usage.Completion >= 0 {
			cost := (parsed.Usage.Prompt*15 + parsed.Usage.Completion*60 + 99) / 100
			if c.model == DefaultModel {
				// Bound input at the 1.25x cache-write rate when details are unavailable.
				input := parsed.Usage.Prompt * 25
				if d := parsed.Usage.Details; d != nil && d.Cached >= 0 && d.Written >= 0 && d.Cached+d.Written <= parsed.Usage.Prompt {
					input = parsed.Usage.Prompt*20 - d.Cached*18 + d.Written*5
				}
				cost = (input + parsed.Usage.Completion*120 + 99) / 100
			}
			usage.Record(ctx, cost)
		}
	}
	return parsed.Choices[0].Message.Content, nil
}
