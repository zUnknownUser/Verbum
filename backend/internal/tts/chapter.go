package tts

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"unicode"
	"unicode/utf8"
)

const maxAudioBytes = 64 << 20

// splitText preserves every UTF-8 byte, preferring sentence and word boundaries.
func splitText(text string) []string {
	var parts []string
	for len(text) > segmentBytes {
		end := segmentBytes
		for !utf8.RuneStart(text[end]) {
			end--
		}
		word, sentence := 0, 0
		var previous rune
		for i, r := range text[:end] {
			if unicode.IsSpace(r) {
				word = i + utf8.RuneLen(r)
				if strings.ContainsRune(".!?;\n", previous) {
					sentence = word
				}
			}
			previous = r
		}
		if sentence >= end/2 {
			end = sentence
		} else if word > 0 {
			end = word
		}
		parts = append(parts, text[:end])
		text = text[end:]
	}
	if text != "" {
		parts = append(parts, text)
	}
	return parts
}

func cacheKey(input Request) string {
	raw, _ := json.Marshal(input)
	hash := sha256.Sum256(append([]byte("google-tts/chapter-v3/1000bytes-wav24k-mp3-128k\n"), raw...))
	return hex.EncodeToString(hash[:]) + ".mp3"
}

func readAudio(path string) ([]byte, error) {
	f, err := os.Open(path)
	if err != nil {
		return nil, err
	}
	defer f.Close()
	stat, err := f.Stat()
	if err != nil || stat.Size() <= 0 || stat.Size() > maxAudioBytes {
		return nil, ErrResponse
	}
	audio, err := io.ReadAll(io.LimitReader(f, maxAudioBytes+1))
	if err != nil || len(audio) == 0 || len(audio) > maxAudioBytes {
		return nil, ErrResponse
	}
	return audio, nil
}

// Synthesize caches the complete supplied chapter by exact text and normalized
// settings. The endpoint never needs chapter storage or a second service wrapper.
// A per-instance gate bounds paid generation/encoding and coalesces identical misses.
func (s *TextToSpeechService) Synthesize(ctx context.Context, input Request) ([]byte, error) {
	input, err := input.normalized()
	if err != nil {
		return nil, err
	}
	ctx, cancel := context.WithTimeout(ctx, GenerationTimeout)
	defer cancel()
	if ctx.Err() != nil {
		return nil, ctx.Err()
	}
	path := ""
	if s.cacheDir != "" {
		path = filepath.Join(s.cacheDir, cacheKey(input))
		if audio, err := readAudio(path); err == nil {
			return audio, nil
		}
	}
	s.once.Do(func() { s.gate = make(chan struct{}, 1) })
	select {
	case s.gate <- struct{}{}:
	case <-ctx.Done():
		return nil, ctx.Err()
	}
	defer func() { <-s.gate }()
	if ctx.Err() != nil {
		return nil, ctx.Err()
	}
	if path != "" {
		if audio, err := readAudio(path); err == nil {
			return audio, nil
		}
	}
	audio, err := s.generate(ctx, input)
	if err != nil {
		return nil, err
	}
	if ctx.Err() != nil {
		return nil, ctx.Err()
	}
	if path != "" {
		// Publish only a closed, complete file on the same filesystem.
		f, err := os.CreateTemp(s.cacheDir, ".audio-*")
		if err != nil {
			return nil, ErrUnavailable
		}
		defer os.Remove(f.Name())
		_, writeErr := f.Write(audio)
		closeErr := f.Close()
		if writeErr != nil || closeErr != nil || os.Rename(f.Name(), path) != nil {
			return nil, ErrUnavailable
		}
	}
	return audio, nil
}

func (s *TextToSpeechService) generate(ctx context.Context, input Request) ([]byte, error) {
	parts := splitText(input.Text)
	if len(parts) == 1 {
		return s.synthesizeSegment(ctx, input, "MP3")
	}
	if s.ffmpeg == "" {
		return nil, ErrUnavailable
	}
	dir, err := os.MkdirTemp("", "verbum-tts-*")
	if err != nil {
		return nil, ErrUnavailable
	}
	defer os.RemoveAll(dir)
	var manifest strings.Builder
	for i, part := range parts {
		if strings.TrimSpace(part) == "" {
			continue
		}
		input.Text = part
		audio, err := s.synthesizeSegment(ctx, input, "LINEAR16")
		if err != nil {
			return nil, err
		}
		name := fmt.Sprintf("%04d.wav", i)
		if os.WriteFile(filepath.Join(dir, name), audio, 0600) != nil {
			return nil, ErrUnavailable
		}
		fmt.Fprintf(&manifest, "file '%s'\n", name)
	}
	if os.WriteFile(filepath.Join(dir, "segments.txt"), []byte(manifest.String()), 0600) != nil {
		return nil, ErrUnavailable
	}
	// Fixed filenames and arguments; neither text nor user paths enter a shell.
	// One PCM timeline encoded once avoids independent MP3 headers/encoder gaps.
	cmd := exec.CommandContext(ctx, s.ffmpeg, "-nostdin", "-v", "error", "-f", "concat", "-safe", "1", "-i", "segments.txt", "-map", "0:a:0", "-c:a", "libmp3lame", "-b:a", "128k", "-threads", "1", "-fs", "67108864", "speech.mp3")
	cmd.Dir = dir
	if cmd.Run() != nil {
		if ctx.Err() != nil {
			return nil, ctx.Err()
		}
		return nil, ErrResponse
	}
	audio, err := readAudio(filepath.Join(dir, "speech.mp3"))
	if err != nil || len(audio) >= maxAudioBytes {
		return nil, ErrResponse
	}
	return audio, nil
}
