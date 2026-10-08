package scripture

import (
	"context"
	"fmt"
	"io"
	"net/http"
	"strings"
	"verbum/backend/internal/tts"
)

// Load retrieves canonical text without generating audio or reserving money.
func (r *Resolver) Load(ctx context.Context, language string, ch Chapter) (tts.Request, error) {
	in := tts.Request{Language: language, BookID: ch.BookID, Chapter: ch.Number}
	if !ValidChapter(ch.BookID, ch.Number) {
		return in, tts.ErrInvalidInput
	}
	switch language {
	case "pt-BR":
		in.Translation = "por_blj"
	case "en-US":
		in.Translation = "BSB"
	default:
		return in, tts.ErrInvalidInput
	}
	req, err := http.NewRequestWithContext(ctx, "GET", fmt.Sprintf("%s/%s/%s/%d.json", r.BaseURL, in.Translation, books[ch.BookID], ch.Number), nil)
	if err != nil {
		return in, err
	}
	resp, err := r.Client.Do(req)
	if err != nil {
		return in, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != 200 {
		return in, fmt.Errorf("canonical chapter HTTP %d", resp.StatusCode)
	}
	raw, err := io.ReadAll(io.LimitReader(resp.Body, (1<<20)+1))
	if err != nil {
		return in, err
	}
	if len(raw) > 1<<20 {
		return in, tts.ErrInvalidInput
	}
	in.Verses, err = decode(raw, in.Translation, books[ch.BookID], ch.Number)
	if err != nil {
		return in, err
	}
	lines := make([]string, len(in.Verses))
	for i, v := range in.Verses {
		lines[i] = v.Text
	}
	in.Text = strings.Join(lines, "\n")
	return in, nil
}
