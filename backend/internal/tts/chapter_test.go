package tts

import (
	"bytes"
	"context"
	"encoding/binary"
	"encoding/json"
	"errors"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"unicode/utf8"
)

func TestSplitTextLossless(t *testing.T) {
	for _, text := range []string{strings.Repeat("á", 2501), strings.Repeat("🙂", 4000), strings.Repeat("Versículo completo. Próxima frase!\n", 600), strings.Repeat("x", MaxTextBytes), strings.Repeat(" ", 7000) + "fim"} {
		parts := splitText(text)
		if strings.Join(parts, "") != text {
			t.Fatal("text changed")
		}
		for _, p := range parts {
			if len(p) == 0 || len(p) > segmentBytes || !utf8.ValidString(p) {
				t.Fatal("invalid segment")
			}
		}
	}
	text := strings.Repeat("a", segmentBytes-100) + ". " + strings.Repeat("b", segmentBytes-100)
	if !strings.HasSuffix(splitText(text)[0], ". ") {
		t.Fatal("sentence boundary not preferred")
	}
}

func TestChirpOmitsPitch(t *testing.T) {
	s := testService(t, func(w http.ResponseWriter, r *http.Request) {
		var body struct {
			Config map[string]any `json:"audioConfig"`
		}
		json.NewDecoder(r.Body).Decode(&body)
		if _, ok := body.Config["pitch"]; ok {
			t.Error("Chirp received pitch")
		}
		if body.Config["speakingRate"] != 0.9 {
			t.Error("speed lost")
		}
		json.NewEncoder(w).Encode(map[string][]byte{"audioContent": []byte("mp3")})
	})
	speed := 0.9
	input := Request{Text: "Texto", Language: "pt-BR", Voice: "pt-BR-Chirp3-HD-Aoede", Speed: &speed}
	if _, err := s.Synthesize(context.Background(), input); err != nil {
		t.Fatal(err)
	}
	input.Pitch = 1
	if _, err := s.Synthesize(context.Background(), input); !errors.Is(err, ErrInvalidInput) {
		t.Fatal(err)
	}
}

func TestCacheConcurrentPersistentAndIdentity(t *testing.T) {
	var calls atomic.Int32
	s := testService(t, func(w http.ResponseWriter, r *http.Request) {
		calls.Add(1)
		json.NewEncoder(w).Encode(map[string][]byte{"audioContent": []byte("complete mp3")})
	})
	s.cacheDir = t.TempDir()
	input := Request{Text: "Capítulo exato", Language: "pt-BR", Voice: "pt-BR-Chirp3-HD-Aoede"}
	var group sync.WaitGroup
	for i := 0; i < 12; i++ {
		group.Add(1)
		go func() {
			defer group.Done()
			a, err := s.Synthesize(context.Background(), input)
			if err != nil || string(a) != "complete mp3" {
				t.Errorf("cache %v", err)
			}
		}()
	}
	group.Wait()
	if calls.Load() != 1 {
		t.Fatalf("duplicate synthesis: %d", calls.Load())
	}
	restarted := &TextToSpeechService{client: s.client, endpoint: s.endpoint, cacheDir: s.cacheDir}
	explicit, _ := input.normalized()
	if _, err := restarted.Synthesize(context.Background(), explicit); err != nil {
		t.Fatal(err)
	}
	if calls.Load() != 1 {
		t.Fatal("cache not persistent or defaults inconsistent")
	}
	for _, r := range []Request{{Text: "Outra tradução", Language: "pt-BR", Voice: "pt-BR-Chirp3-HD-Aoede"}, {Text: input.Text, Language: "pt-BR", Voice: "pt-BR-Chirp3-HD-Kore"}, {Text: input.Text, Language: "en-US"}} {
		if _, err := s.Synthesize(context.Background(), r); err != nil {
			t.Fatal(err)
		}
	}
	if calls.Load() != 4 {
		t.Fatal("incorrect identity")
	}
	changed := explicit
	speed := 1.1
	changed.Speed = &speed
	if cacheKey(changed) == cacheKey(explicit) {
		t.Fatal("speed missing from key")
	}
}

func TestFailureNotCachedAndCancelledWait(t *testing.T) {
	var calls atomic.Int32
	s := testService(t, func(w http.ResponseWriter, r *http.Request) { calls.Add(1); w.WriteHeader(503) })
	s.cacheDir = t.TempDir()
	input := Request{Text: "Texto", Language: "pt-BR", Voice: "pt-BR-Chirp3-HD-Aoede"}
	for i := 0; i < 2; i++ {
		if _, err := s.Synthesize(context.Background(), input); !errors.Is(err, ErrUnavailable) {
			t.Fatal(err)
		}
	}
	entries, _ := os.ReadDir(s.cacheDir)
	if len(entries) != 0 || calls.Load() != 2 {
		t.Fatal("failed result cached")
	}
	s.gate <- struct{}{}
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	if _, err := s.Synthesize(ctx, input); !errors.Is(err, context.Canceled) {
		t.Fatal(err)
	}
	<-s.gate
}

func testWAV() []byte {
	var b bytes.Buffer
	b.WriteString("RIFF")
	binary.Write(&b, binary.LittleEndian, uint32(36+48000))
	b.WriteString("WAVEfmt ")
	binary.Write(&b, binary.LittleEndian, uint32(16))
	for _, v := range []uint16{1, 1} {
		binary.Write(&b, binary.LittleEndian, v)
	}
	for _, v := range []uint32{24000, 48000} {
		binary.Write(&b, binary.LittleEndian, v)
	}
	for _, v := range []uint16{2, 16} {
		binary.Write(&b, binary.LittleEndian, v)
	}
	b.WriteString("data")
	binary.Write(&b, binary.LittleEndian, uint32(48000))
	b.Write(make([]byte, 48000))
	return b.Bytes()
}

func TestChapterContinuousMP3(t *testing.T) {
	ffmpeg, err := exec.LookPath("ffmpeg")
	if err != nil {
		t.Skip("run in API image with ffmpeg")
	}
	var texts []string
	fail := false
	s := testService(t, func(w http.ResponseWriter, r *http.Request) {
		var body struct {
			Input  struct{ Text string }
			Config map[string]any `json:"audioConfig"`
		}
		json.NewDecoder(r.Body).Decode(&body)
		texts = append(texts, body.Input.Text)
		if body.Config["audioEncoding"] != "LINEAR16" || body.Config["sampleRateHertz"] != float64(24000) {
			t.Error("wrong intermediate audio")
		}
		if fail && len(texts) == 2 {
			w.WriteHeader(429)
			return
		}
		json.NewEncoder(w).Encode(map[string][]byte{"audioContent": testWAV()})
	})
	s.ffmpeg = ffmpeg
	s.cacheDir = t.TempDir()
	input := Request{Text: strings.Repeat("Leitura de teste. ", 350), Language: "pt-BR", Voice: "pt-BR-Chirp3-HD-Aoede"}
	audio, err := s.Synthesize(context.Background(), input)
	if err != nil {
		t.Fatal(err)
	}
	segments := len(splitText(input.Text))
	if len(texts) != segments || strings.Join(texts, "") != input.Text {
		t.Fatal("lost or reordered text")
	}
	path := filepath.Join(t.TempDir(), "chapter.mp3")
	os.WriteFile(path, audio, 0600)
	out, err := exec.Command("ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "default=noprint_wrappers=1:nokey=1", path).Output()
	duration, _ := strconv.ParseFloat(strings.TrimSpace(string(out)), 64)
	// Each of the segments-1 joins overlaps by crossfadeSeconds instead of a hard cut.
	expected := float64(segments) - float64(segments-1)*crossfadeSeconds
	if err != nil || duration < expected-0.05 || duration > expected+0.2 {
		t.Fatalf("duration %f (expected ~%f): %v", duration, expected, err)
	}
	if err := exec.Command(ffmpeg, "-v", "error", "-i", path, "-f", "null", "-").Run(); err != nil {
		t.Fatal("invalid MP3", err)
	}
	// An upstream failure after the first segment must never publish a chapter.
	fail = true
	texts = nil
	input.Text += " Changed."
	if _, err := s.Synthesize(context.Background(), input); !errors.Is(err, ErrQuota) {
		t.Fatal(err)
	}
	entries, _ := os.ReadDir(s.cacheDir)
	if len(entries) != 1 {
		t.Fatal("partial chapter cached")
	}
}
