package tts

import (
	"context"
	"encoding/json"
	"errors"
	"math"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"sync"
	"testing"
	"time"
)

func TestTimedSpeechUsesMeasuredDurationsAndPersistentCache(t *testing.T) {
	ffmpeg, err := exec.LookPath("ffmpeg")
	if err != nil {
		t.Skip("ffmpeg required")
	}
	var supplied []string
	var mu sync.Mutex
	s := testService(t, func(w http.ResponseWriter, r *http.Request) {
		var body struct{ Input struct{ Text string } }
		json.NewDecoder(r.Body).Decode(&body)
		mu.Lock()
		supplied = append(supplied, body.Input.Text)
		mu.Unlock()
		json.NewEncoder(w).Encode(map[string][]byte{"audioContent": testWAV()})
	})
	s.ffmpeg = ffmpeg
	s.cacheDir = t.TempDir()
	verses := []Verse{{1, "Primeiro."}, {2, "Segundo."}, {3, "Terceiro."}, {4, "Quarto."}}
	input := Request{Text: "Primeiro.\nSegundo.\nTerceiro.\nQuarto.", Language: "pt-BR", Voice: "pt-BR-Chirp3-HD-Aoede", Verses: verses}
	got, err := s.SynthesizeTimed(context.Background(), input)
	if err != nil {
		t.Fatal(err)
	}
	mp3 := filepath.Join(t.TempDir(), "timed.mp3")
	if err := os.WriteFile(mp3, got.Audio, 0600); err != nil {
		t.Fatal(err)
	}
	measured, err := exec.Command("ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "default=noprint_wrappers=1:nokey=1", mp3).Output()
	if err != nil {
		t.Fatal(err)
	}
	duration, err := strconv.ParseFloat(strings.TrimSpace(string(measured)), 64)
	if err != nil || math.Abs(duration-got.Cues[len(got.Cues)-1].End) > 0.15 {
		t.Fatalf("MP3 duration %f does not match cues: %+v", duration, got.Cues)
	}
	if len(got.Cues) != 2 || got.Cues[0].VerseEnd != 3 || got.Cues[1].VerseStart != 4 {
		t.Fatalf("wrong coverage: %+v", got.Cues)
	}
	// Two exactly one-second WAVs, with the existing 60ms crossfade. The cue
	// changes at the crossfade midpoint, not at a text-length-derived timestamp.
	if math.Abs(got.Cues[0].End-0.97) > 0.0001 || math.Abs(got.Cues[1].End-1.94) > 0.0001 {
		t.Fatalf("not measured: %+v", got.Cues)
	}
	sort.Strings(supplied)
	if strings.Join(supplied, "\n") != input.Text {
		t.Fatal("spoken text changed")
	}
	restarted := &TextToSpeechService{client: s.client, endpoint: s.endpoint, cacheDir: s.cacheDir, ffmpeg: ffmpeg}
	again, err := restarted.SynthesizeTimed(context.Background(), input)
	if err != nil || len(supplied) != 2 || string(again.Audio) != string(got.Audio) || !validCues(again.Cues) {
		t.Fatalf("cache replay failed: %v", err)
	}
}
func TestTimedSpeechRejectsMismatchedTextBeforeProvider(t *testing.T) {
	s := testService(t, func(http.ResponseWriter, *http.Request) { t.Fatal("provider must not be called") })
	for _, verses := range [][]Verse{{{1, "other"}}, {{2, "A"}, {1, "B"}}, {{177, "A"}}} {
		_, err := s.SynthesizeTimed(context.Background(), Request{Text: "A", Language: "pt-BR", Voice: "pt-BR-Chirp3-HD-Aoede", Verses: verses})
		if !errors.Is(err, ErrInvalidInput) {
			t.Fatalf("accepted invalid input: %v", err)
		}
	}
}
func TestWaveDurationRejectsTruncatedAndNonPCMData(t *testing.T) {
	good := testWAV()
	duration, err := waveDuration(good)
	if err != nil || duration != 1 {
		t.Fatal(duration, err)
	}
	if _, err = waveDuration(good[:len(good)-1]); err == nil {
		t.Fatal("truncated audio accepted")
	}
	if _, err = waveDuration([]byte("not WAV")); err == nil {
		t.Fatal("invalid audio accepted")
	}
}

func TestProgressPublishesFirstExcerptBeforeLastCompletes(t *testing.T) {
	release := make(chan struct{})
	defer close(release)
	service := testService(t, func(w http.ResponseWriter, r *http.Request) {
		var body struct{ Input struct{ Text string } }
		json.NewDecoder(r.Body).Decode(&body)
		if body.Input.Text == "B" {
			select {
			case <-release:
			case <-r.Context().Done():
				return
			}
		}
		json.NewEncoder(w).Encode(map[string][]byte{"audioContent": testWAV()})
	})
	input, _ := (Request{Text: "A", Language: "pt-BR", Voice: "pt-BR-Chirp3-HD-Aoede"}).normalized()
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	published := make(chan struct{}, 1)
	ctx = WithProgress(ctx, func(_ context.Context, path string, duration float64) error {
		if _, err := os.Stat(path); err != nil || duration <= 0 {
			t.Error("incomplete excerpt")
		}
		published <- struct{}{}
		cancel()
		return nil
	})
	finished := make(chan struct{})
	go func() {
		defer close(finished)
		service.timedWaves(ctx, input, t.TempDir(), []speechPart{{text: "A"}, {text: "B"}})
	}()
	select {
	case <-published:
	case <-time.After(2 * time.Second):
		t.Fatal("first excerpt waited for last")
	}
	<-finished
}
