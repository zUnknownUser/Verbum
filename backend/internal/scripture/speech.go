// Package scripture validates synthesis against the same open-licensed chapter source
// the apps read. No caller-controlled URL, arbitrary text or arbitrary voice is accepted.
package scripture

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"
	"unicode"
	"verbum/backend/internal/tts"
	"verbum/backend/internal/usage"
)

type Resolver struct {
	Cache   usage.Store
	Client  *http.Client
	BaseURL string
}

func New(cache usage.Store) *Resolver {
	return &Resolver{cache, &http.Client{Timeout: 10 * time.Second, CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse }}, "https://bible.helloao.org/api"}
}
func (r *Resolver) Verify(ctx context.Context, in tts.Request) (tts.Request, error) {
	invalid := func() (tts.Request, error) {
		return in, fmt.Errorf("%w: select a supported Bible chapter and refresh its text", tts.ErrInvalidInput)
	}
	book, ok := books[in.BookID]
	if !ok || in.Chapter < 1 || in.Chapter > chapters[in.BookID] {
		return invalid()
	}
	if (in.Language != "pt-BR" || in.Translation != "por_blj") && (in.Language != "en-US" || in.Translation != "BSB") {
		return invalid()
	}
	voice := tts.DefaultVoice(in.Language)
	// Playback speed belongs to the device player; it must not create a new paid artifact.
	if (in.Voice != "" && in.Voice != voice) || (in.Speed != nil && *in.Speed != 1) || in.Pitch != 0 {
		return invalid()
	}
	if r.Cache == nil {
		return in, usage.Unavailable()
	}
	key := usage.Hash("chapter-v1", in.Translation, book, fmt.Sprint(in.Chapter))
	data, err := r.Cache.Cached(ctx, key, time.Now())
	if err != nil {
		return in, usage.Unavailable()
	}
	if data == nil {
		unlock, e := r.Cache.Lock(ctx, key)
		if e != nil {
			return in, usage.Unavailable()
		}
		defer unlock()
		data, e = r.Cache.Cached(ctx, key, time.Now())
		if e != nil {
			return in, usage.Unavailable()
		}
		if data == nil {
			req, e := http.NewRequestWithContext(ctx, "GET", fmt.Sprintf("%s/%s/%s/%d.json", r.BaseURL, in.Translation, book, in.Chapter), nil)
			if e != nil {
				return in, e
			}
			resp, e := r.Client.Do(req)
			if e != nil {
				return in, tts.ErrUnavailable
			}
			defer resp.Body.Close()
			if resp.StatusCode != 200 {
				return in, tts.ErrUnavailable
			}
			data, e = io.ReadAll(io.LimitReader(resp.Body, 1<<20+1))
			if e != nil || len(data) > 1<<20 {
				return in, tts.ErrUnavailable
			}
			// Only structurally valid canonical chapters enter the shared cache.
			if _, e = decode(data, in.Translation, book, in.Chapter); e != nil {
				return in, tts.ErrUnavailable
			}
			if e = r.Cache.PutCache(ctx, key, data, time.Now().Add(30*24*time.Hour)); e != nil {
				return in, usage.Unavailable()
			}
		}
	}
	verses, err := decode(data, in.Translation, book, in.Chapter)
	if err != nil {
		return in, tts.ErrUnavailable
	}
	text := make([]string, len(verses))
	for i, v := range verses {
		text[i] = v.Text
	}
	canonical := strings.Join(text, "\n")
	if strings.Join(strings.Fields(in.Text), " ") != strings.Join(strings.Fields(canonical), " ") {
		return invalid()
	}
	if len(in.Verses) > 0 {
		if len(in.Verses) != len(verses) {
			return invalid()
		}
		for i, v := range verses {
			if in.Verses[i].Number != v.Number || strings.Join(strings.Fields(in.Verses[i].Text), " ") != strings.Join(strings.Fields(v.Text), " ") {
				return invalid()
			}
		}
		in.Verses = verses
	}
	in.Text = canonical
	return in, nil
}
func decode(raw []byte, translation, book string, chapter int) ([]tts.Verse, error) {
	var wire struct {
		Translation struct {
			ID string `json:"id"`
		} `json:"translation"`
		Book struct {
			ID string `json:"id"`
		} `json:"book"`
		Chapter struct {
			Number  int `json:"number"`
			Content []struct {
				Type    string            `json:"type"`
				Number  int               `json:"number"`
				Content []json.RawMessage `json:"content"`
			} `json:"content"`
		} `json:"chapter"`
	}
	if e := json.Unmarshal(raw, &wire); e != nil {
		return nil, e
	}
	if wire.Translation.ID != translation || wire.Book.ID != book || wire.Chapter.Number != chapter {
		return nil, fmt.Errorf("chapter mismatch")
	}
	var verses []tts.Verse
	for _, block := range wire.Chapter.Content {
		if block.Type != "verse" {
			continue
		}
		var out string
		lastPoem := -1
		for _, raw := range block.Content {
			var text string
			poem := -1
			if json.Unmarshal(raw, &text) != nil {
				var run struct {
					Text      *string         `json:"text"`
					Poem      *int            `json:"poem"`
					NoteID    json.RawMessage `json:"noteId"`
					LineBreak bool            `json:"lineBreak"`
				}
				if e := json.Unmarshal(raw, &run); e != nil {
					return nil, e
				}
				if run.NoteID != nil {
					continue
				}
				if run.Text == nil {
					if run.LineBreak {
						out += "\n"
					}
					continue
				}
				text = *run.Text
				if run.Poem != nil {
					poem = *run.Poem
				}
			}
			if poem >= 0 && lastPoem >= 0 && poem != lastPoem {
				out += "\n"
			} else if out != "" && !strings.HasSuffix(out, "\n") && !strings.HasSuffix(out, " ") && !strings.HasPrefix(text, " ") && text != "" {
				first := []rune(text)[0]
				if !unicode.IsPunct(first) {
					out += " "
				}
			}
			out += text
			if poem >= 0 {
				lastPoem = poem
			}
		}
		lines := strings.Split(out, "\n")
		for i := range lines {
			lines[i] = strings.TrimSpace(lines[i])
		}
		verses = append(verses, tts.Verse{Number: block.Number, Text: strings.Join(lines, "\n")})
	}
	if len(verses) == 0 || len(verses) > 176 {
		return nil, fmt.Errorf("empty chapter")
	}
	return verses, nil
}
