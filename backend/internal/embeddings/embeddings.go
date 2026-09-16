// Package embeddings turns one query string into a vector at request time, for hybrid Scripture
// search (§27-28). This is the query side only: the corpus side (scripture_verses) is embedded
// offline by pipeline/verbum_pipeline/scripture.py. The two must agree on model and dimensions
// or cosine distance is meaningless — see db/migrations/0004_scripture_search.sql.
package embeddings

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

const endpoint = "https://api.openai.com/v1/embeddings"

// Model and Dimensions must match what pipeline/verbum_pipeline/scripture.py used to build
// scripture_verses.embedding (text-embedding-3-large at 1536 dims, chosen there because
// pgvector's HNSW index rejects more than 2000).
const (
	Model      = "text-embedding-3-large"
	Dimensions = 1536
)

type Client struct {
	apiKey   string
	endpoint string
	client   *http.Client
}

func New(apiKey string) *Client {
	return &Client{apiKey: apiKey, endpoint: endpoint, client: &http.Client{Timeout: 10 * time.Second}}
}

// Embed returns one query embedding, or an error — never a partial/zero vector on failure.
func (c *Client) Embed(ctx context.Context, text string) ([]float32, error) {
	body, err := json.Marshal(map[string]any{
		"model":      Model,
		"input":      text,
		"dimensions": Dimensions,
	})
	if err != nil {
		return nil, err
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, c.endpoint, bytes.NewReader(body))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Authorization", "Bearer "+c.apiKey)
	req.Header.Set("Content-Type", "application/json")

	usage.Attempt(ctx)
	resp, err := c.client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("embeddings request: %w", err)
	}
	defer resp.Body.Close()
	raw, err := io.ReadAll(io.LimitReader(resp.Body, 4<<20))
	if err != nil {
		return nil, fmt.Errorf("embeddings response: %w", err)
	}
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("embeddings request failed: status %d", resp.StatusCode)
	}
	var parsed struct {
		Usage *struct {
			Tokens int64 `json:"total_tokens"`
		} `json:"usage"`
		Data []struct {
			Embedding []float32 `json:"embedding"`
			Index     int       `json:"index"`
		} `json:"data"`
	}
	if err := json.Unmarshal(raw, &parsed); err != nil {
		return nil, fmt.Errorf("embeddings response: %w", err)
	}
	if len(parsed.Data) == 0 {
		return nil, fmt.Errorf("embeddings response had no data")
	}
	vector := parsed.Data[0].Embedding
	if len(vector) != Dimensions {
		return nil, fmt.Errorf("embeddings response had %d dimensions, want %d", len(vector), Dimensions)
	}
	if parsed.Usage != nil {
		usage.Record(ctx, (parsed.Usage.Tokens*13+99)/100)
	}
	return vector, nil
}
