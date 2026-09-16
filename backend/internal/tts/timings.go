package tts

import (
	"context"
	"encoding/binary"
	"encoding/json"
	"fmt"
	"math"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"verbum/backend/internal/usage"
)

// Timings describe measured audio excerpts, never estimates from character counts.
type Verse struct {
	Number int    `json:"number"`
	Text   string `json:"text"`
}
type Cue struct {
	VerseStart int     `json:"verseStart"`
	VerseEnd   int     `json:"verseEnd"`
	Start      float64 `json:"start"`
	End        float64 `json:"end"`
}
type TimedAudio struct {
	Audio []byte `json:"audio"`
	Cues  []Cue  `json:"cues"`
}

// Progress receives ordered, complete WAV excerpts while later excerpts generate.
// It is internal server context, never a field accepted from a client.
type progressKey struct{}
type Progress func(context.Context, string, float64) error

func WithProgress(ctx context.Context, publish Progress) context.Context {
	return context.WithValue(ctx, progressKey{}, publish)
}

type speechPart struct {
	text        string
	first, last int
	style       string
}

func validateVerses(input Request) error {
	if len(input.Verses) == 0 || len(input.Verses) > 176 {
		return fmt.Errorf("%w: provide 1–176 verses", ErrInvalidInput)
	}
	var texts []string
	previous := 0
	for _, v := range input.Verses {
		if v.Number <= previous || v.Number > 176 || strings.TrimSpace(v.Text) == "" {
			return fmt.Errorf("%w: invalid verse sequence", ErrInvalidInput)
		}
		previous = v.Number
		texts = append(texts, v.Text)
	}
	if strings.Join(texts, "\n") != input.Text {
		return fmt.Errorf("%w: verses must match speech text", ErrInvalidInput)
	}
	return nil
}
func timedParts(verses []Verse) []speechPart {
	var result []speechPart
	current := speechPart{}
	count := 0
	flush := func() {
		if current.text != "" {
			result = append(result, current)
		}
		current = speechPart{}
		count = 0
	}
	for _, v := range verses {
		// Short passages retain natural prosody within a small excerpt. Long verses
		// still obey the existing provider byte limit; all their parts keep their identity.
		if current.text != "" && (count >= 3 || len(current.text)+1+len(v.Text) > segmentBytes) {
			flush()
		}
		if len(v.Text) > segmentBytes {
			for _, p := range splitText(v.Text) {
				result = append(result, speechPart{text: p, first: v.Number, last: v.Number})
			}
			continue
		}
		if current.text == "" {
			current.first = v.Number
		} else {
			current.text += "\n"
		}
		current.text += v.Text
		current.last = v.Number
		count++
	}
	flush()
	return result
}

// The existing gate/cache/provider/encoder are reused. Legacy MP3 requests and
// their cache remain unchanged. A single atomic artifact binds timing to audio.
func (s *TextToSpeechService) SynthesizeTimed(ctx context.Context, input Request) (TimedAudio, error) {
	var empty TimedAudio
	normalized, err := input.normalized()
	if err != nil {
		return empty, err
	}
	input = normalized
	if err = validateVerses(input); err != nil {
		return empty, err
	}
	ctx, cancel := context.WithTimeout(ctx, GenerationTimeout)
	defer cancel()
	path := ""
	if s.cacheDir != "" {
		path = filepath.Join(s.cacheDir, "sync-v1-"+strings.TrimSuffix(cacheKey(input), ".mp3")+".json")
	}
	read := func() (TimedAudio, bool) {
		if path == "" {
			return empty, false
		}
		f, err := os.Open(path)
		if err != nil {
			return empty, false
		}
		defer f.Close()
		stat, err := f.Stat()
		if err != nil || stat.Size() > maxAudioBytes*2 {
			return empty, false
		}
		var a TimedAudio
		if json.NewDecoder(f).Decode(&a) != nil || len(a.Audio) == 0 || len(a.Audio) > maxAudioBytes || !validCues(a.Cues) {
			return empty, false
		}
		return a, true
	}
	if a, ok := read(); ok {
		return a, nil
	}
	s.once.Do(func() { s.gate = make(chan struct{}, 1) })
	select {
	case s.gate <- struct{}{}:
	case <-ctx.Done():
		return empty, ctx.Err()
	}
	defer func() { <-s.gate }()
	if a, ok := read(); ok {
		return a, nil
	}
	if s.ffmpeg == "" {
		return empty, ErrUnavailable
	}
	dir, err := os.MkdirTemp("", "verbum-sync-*")
	if err != nil {
		return empty, ErrUnavailable
	}
	defer os.RemoveAll(dir)
	parts := input.speechParts()
	names, durations, err := s.timedWaves(ctx, input, dir, parts)
	if err != nil {
		return empty, err
	}
	var cues []Cue
	elapsed := 0.0
	for i, p := range parts {
		start := elapsed
		if i > 0 {
			start -= crossfadeSeconds / 2
			elapsed -= crossfadeSeconds
			cues[len(cues)-1].End = start
		}
		elapsed += durations[i]
		cues = append(cues, Cue{p.first, p.last, start, elapsed})
	}
	audio, err := s.encodeChapter(ctx, dir, names)
	if err != nil {
		return empty, err
	}
	// Refuse synchronization if encoding produced a different timeline. This also
	// guards against future codec/filter regressions before publishing the cache.
	probe, probeErr := exec.CommandContext(ctx, filepath.Join(filepath.Dir(s.ffmpeg), "ffprobe"), "-v", "error", "-show_entries", "format=duration", "-of", "default=noprint_wrappers=1:nokey=1", filepath.Join(dir, "speech.mp3")).Output()
	duration, parseErr := strconv.ParseFloat(strings.TrimSpace(string(probe)), 64)
	if probeErr != nil || parseErr != nil || math.IsNaN(duration) || math.IsInf(duration, 0) || math.Abs(duration-elapsed) > 0.15 {
		return empty, ErrResponse
	}
	result := TimedAudio{audio, cues}
	if path != "" {
		f, err := os.CreateTemp(s.cacheDir, ".sync-*")
		if err != nil {
			return empty, ErrUnavailable
		}
		defer os.Remove(f.Name())
		writeErr := json.NewEncoder(f).Encode(result)
		closeErr := f.Close()
		if writeErr != nil || closeErr != nil || os.Rename(f.Name(), path) != nil {
			return empty, ErrUnavailable
		}
	}
	if input.IsGemini() {
		var cost int64
		for i, p := range parts {
			segment := input
			segment.Text = p.text
			segment.style = p.style
			cost += segment.measuredCost(durations[i])
		}
		usage.Record(ctx, cost)
	}
	return result, nil
}
func validCues(cues []Cue) bool {
	if len(cues) == 0 || len(cues) > 256 {
		return false
	}
	end := 0.0
	for _, c := range cues {
		if math.IsNaN(c.Start) || math.IsNaN(c.End) || math.IsInf(c.End, 0) || c.Start < end || c.End <= c.Start || c.VerseStart < 1 || c.VerseEnd < c.VerseStart || c.VerseEnd > 176 {
			return false
		}
		end = c.End
	}
	return true
}
func waveDuration(data []byte) (float64, error) {
	if len(data) < 12 || string(data[:4]) != "RIFF" || string(data[8:12]) != "WAVE" {
		return 0, ErrResponse
	}
	rate := uint32(0)
	size := uint32(0)
	for pos := 12; pos+8 <= len(data); {
		n := int(binary.LittleEndian.Uint32(data[pos+4 : pos+8]))
		start := pos + 8
		if n > len(data)-start {
			return 0, ErrResponse
		}
		switch string(data[pos : pos+4]) {
		case "fmt ":
			if n < 16 || binary.LittleEndian.Uint16(data[start:start+2]) != 1 {
				return 0, ErrResponse
			}
			rate = binary.LittleEndian.Uint32(data[start+8 : start+12])
		case "data":
			size += uint32(n)
		}
		pos = start + n + n%2
	}
	if rate == 0 || size == 0 {
		return 0, ErrResponse
	}
	return float64(size) / float64(rate), nil
}

// Two bounded provider requests shorten a cold render; filenames/durations retain
// input order even when the second request finishes first. Nothing partial is cached.
func (s *TextToSpeechService) timedWaves(ctx context.Context, input Request, dir string, parts []speechPart) ([]string, []float64, error) {
	ctx, cancel := context.WithCancel(ctx)
	defer cancel()
	names := make([]string, len(parts))
	durations := make([]float64, len(parts))
	ready := make([]chan struct{}, len(parts))
	for i := range ready {
		ready[i] = make(chan struct{})
	}
	var wg sync.WaitGroup
	var once sync.Once
	var failure error
	fail := func(err error) { once.Do(func() { failure = err; cancel() }) }
	for worker := 0; worker < 2; worker++ {
		wg.Add(1)
		go func(worker int) {
			defer wg.Done()
			for i := worker; i < len(parts); i += 2 {
				if ctx.Err() != nil {
					fail(ctx.Err())
					return
				}
				segment := input
				segment.Text = parts[i].text
				segment.style = parts[i].style
				segment.Verses = nil
				audio, err := s.synthesizeSegment(ctx, segment, "LINEAR16")
				if err != nil {
					fail(err)
					return
				}
				duration, err := waveDuration(audio)
				if err != nil || duration <= crossfadeSeconds || (input.IsGemini() && duration >= 655) {
					fail(ErrResponse)
					return
				}
				name := fmt.Sprintf("%04d.wav", i)
				if os.WriteFile(filepath.Join(dir, name), audio, 0600) != nil {
					fail(ErrUnavailable)
					return
				}
				names[i] = name
				durations[i] = duration
				close(ready[i])
			}
		}(worker)
	}
	if publish, ok := ctx.Value(progressKey{}).(Progress); ok {
		for i := range parts {
			select {
			case <-ready[i]:
				if err := publish(ctx, filepath.Join(dir, names[i]), durations[i]); err != nil {
					fail(err)
				}
			case <-ctx.Done():
			}
			if ctx.Err() != nil {
				break
			}
		}
	}
	wg.Wait()
	return names, durations, failure
}
