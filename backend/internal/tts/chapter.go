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
	"verbum/backend/internal/usage"
)

const maxAudioBytes = 64 << 20

// Each segment is synthesized independently, so Chirp's prosody resets at every
// join; a hard cut (the previous concat-demuxer approach) makes that audible as
// a mechanical restart. A short crossfade blends consecutive segments' pitch/
// volume across the join instead of cutting between them, at the cost of this
// much overlapping audio per join — imperceptible against a spoken sentence,
// enough to remove the seam. Segments this short don't occur: splitText only
// produces one under segmentBytes long (the final one), and speech that brief
// is at least a few hundred milliseconds.
const crossfadeSeconds = 0.06

// splitText preserves every UTF-8 byte, preferring sentence and word boundaries.
func splitText(text string) []string { return splitTextAt(text, segmentBytes) }
func splitTextAt(text string, limit int) []string {
	var parts []string
	for len(text) > limit {
		end := limit
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

// Bump when generation/encoding changes. Default voice changes are included automatically.
const audioRevision = "google-tts/chapter-v4/2200bytes-wav24k-crossfade60ms-mp3-128k"

func AudioVersion(language string) (string, error) {
	input, err := (Request{Text: "audio-version", Language: language}).normalized()
	if err != nil {
		return "", err
	}
	return strings.TrimSuffix(cacheKey(input), ".mp3"), nil
}

func cacheKey(input Request) string {
	raw, _ := json.Marshal(input)
	revision := audioRevision
	if input.IsGemini() {
		revision += "\n" + narrationRevision() + fmt.Sprintf("\n%s/%d/%s", input.literaryBook, input.literaryChapter, input.style)
	}
	hash := sha256.Sum256(append([]byte(revision+"\n"), raw...))
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
	parts := input.textParts()
	if len(parts) == 1 && !input.IsGemini() {
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
	var names []string
	var cost int64
	for i, part := range parts {
		if strings.TrimSpace(part) == "" {
			continue
		}
		input.Text = part
		audio, err := s.synthesizeSegment(ctx, input, "LINEAR16")
		if err != nil {
			return nil, err
		}
		if input.IsGemini() {
			duration, err := waveDuration(audio)
			if err != nil || duration >= 655 {
				return nil, ErrResponse
			}
			cost += input.measuredCost(duration)
		}
		name := fmt.Sprintf("%04d.wav", i)
		if os.WriteFile(filepath.Join(dir, name), audio, 0600) != nil {
			return nil, ErrUnavailable
		}
		names = append(names, name)
	}
	if len(names) == 0 {
		return nil, ErrResponse
	}
	audio, err := s.encodeChapter(ctx, dir, names)
	if err == nil && input.IsGemini() {
		usage.Record(ctx, cost)
	}
	return audio, err
}

func (s *TextToSpeechService) encodeChapter(ctx context.Context, dir string, names []string) ([]byte, error) {
	// Fixed filenames and arguments; neither text nor user paths enter a shell.
	args := []string{"-nostdin", "-v", "error"}
	for _, name := range names {
		args = append(args, "-i", name)
	}
	if len(names) == 1 {
		args = append(args, "-map", "0:a:0")
	} else {
		// Chain acrossfade left to right: each join blends the tail of the
		// timeline so far with the head of the next segment (see crossfadeSeconds).
		var filter strings.Builder
		label := "[0:a]"
		for i := 1; i < len(names); i++ {
			out := fmt.Sprintf("[a%d]", i)
			if i == len(names)-1 {
				out = "[out]"
			}
			fmt.Fprintf(&filter, "%s[%d:a]acrossfade=d=%g:c1=tri:c2=tri%s;", label, i, crossfadeSeconds, out)
			label = out
		}
		args = append(args, "-filter_complex", strings.TrimSuffix(filter.String(), ";"), "-map", "[out]")
	}
	args = append(args, "-c:a", "libmp3lame", "-b:a", "128k", "-threads", "1", "-fs", "67108864", "speech.mp3")
	cmd := exec.CommandContext(ctx, s.ffmpeg, args...)
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

// Prepare normalizes identity after a trusted chapter resolver validated the input.
func Prepare(input Request) (Request, error) {
	input.literaryBook = input.BookID
	input.literaryChapter = input.Chapter
	input.BookID = ""
	input.Chapter = 0
	input.Translation = ""
	return input.normalized()
}
func (s *TextToSpeechService) Cached(input Request, timed bool) (TimedAudio, bool) {
	input, err := input.normalized()
	if err != nil || s.cacheDir == "" {
		return TimedAudio{}, false
	}
	if !timed {
		v, e := readAudio(filepath.Join(s.cacheDir, cacheKey(input)))
		return TimedAudio{Audio: v}, e == nil
	}
	raw, e := os.ReadFile(filepath.Join(s.cacheDir, "sync-v1-"+strings.TrimSuffix(cacheKey(input), ".mp3")+".json"))
	if e != nil || len(raw) > maxAudioBytes*2 {
		return TimedAudio{}, false
	}
	var v TimedAudio
	e = json.Unmarshal(raw, &v)
	return v, e == nil && len(v.Audio) > 0 && validCues(v.Cues)
}
