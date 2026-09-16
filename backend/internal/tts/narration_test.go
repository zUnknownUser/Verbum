package tts

import (
	"context"
	"encoding/json"
	"net/http"
	"os/exec"
	"strings"
	"sync/atomic"
	"testing"
)

func TestGeminiProviderContract(t *testing.T) {
	s := testService(t, func(w http.ResponseWriter, r *http.Request) {
		var body struct {
			Input       map[string]string
			Voice       map[string]string
			AudioConfig map[string]any
		}
		if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
			t.Fatal(err)
		}
		if body.Input["text"] != "No princípio era o Verbo." || !strings.Contains(body.Input["prompt"], "sem cantar") || len(body.Input["prompt"]) > 4000 {
			t.Errorf("incorrect text/direction")
		}
		if body.Voice["name"] != "Charon" || body.Voice["modelName"] != "gemini-2.5-pro-tts" || body.Voice["languageCode"] != "pt-BR" {
			t.Errorf("wrong voice: %v", body.Voice)
		}
		if _, ok := body.AudioConfig["pitch"]; ok {
			t.Error("unsupported pitch sent")
		}
		if _, ok := body.AudioConfig["speakingRate"]; ok {
			t.Error("unsupported rate sent")
		}
		json.NewEncoder(w).Encode(map[string][]byte{"audioContent": testWAV()})
	})
	in, err := Prepare(Request{BookID: "John", Chapter: 1, Language: "pt-BR", Text: "No princípio era o Verbo."})
	if err != nil {
		t.Fatal(err)
	}
	if _, err = s.synthesizeSegment(context.Background(), in, "LINEAR16"); err != nil {
		t.Fatal(err)
	}
	for style := range narrationProfiles {
		in.style = style
		if len(in.narrationPrompt()) > 4000 {
			t.Fatalf("prompt too long: %s", style)
		}
	}
}
func TestEditorialProfilesAndBoundaries(t *testing.T) {
	for _, c := range []struct {
		book           string
		chapter, verse int
		want           string
	}{
		{"Gen", 1, 1, "narrative"}, {"Ps", 23, 1, "contemplative"}, {"Prov", 1, 1, "wisdom"}, {"Rom", 8, 1, "teaching"}, {"Rev", 1, 1, "prophecy"}, {"John", 1, 1, "contemplative"}, {"John", 1, 19, "narrative"}, {"John", 11, 1, "narrative"}, {"John", 3, 4, "dialogue"},
	} {
		if got := literaryStyle(c.book, c.chapter, c.verse); got != c.want {
			t.Fatalf("%+v: %s", c, got)
		}
	}
	in, err := Prepare(Request{BookID: "John", Chapter: 1, Language: "pt-BR", Text: "A.\nB.", Verses: []Verse{{18, "A."}, {19, "B."}}})
	if err != nil {
		t.Fatal(err)
	}
	parts := in.speechParts()
	if len(parts) != 2 || parts[0].style != "contemplative" || parts[1].style != "narrative" {
		t.Fatalf("lost editorial boundary: %+v", parts)
	}
	text := strings.Repeat("Uma frase íntegra. ", 500)
	if strings.Join(splitTextAt(text, geminiSegmentBytes), "") != text {
		t.Fatal("lost text")
	}
	for _, p := range splitTextAt(text, geminiSegmentBytes) {
		if len(p) > 4000 {
			t.Fatal("provider limit exceeded")
		}
	}
}
func TestGeminiCacheVersionIncludesEditorialDirection(t *testing.T) {
	in, _ := Prepare(Request{BookID: "John", Chapter: 1, Language: "pt-BR", Text: "Mesmo texto."})
	other, _ := Prepare(Request{BookID: "John", Chapter: 11, Language: "pt-BR", Text: in.Text})
	old, _ := (Request{Language: "pt-BR", Voice: "pt-BR-Chirp3-HD-Aoede", Text: in.Text}).normalized()
	if cacheKey(in) == cacheKey(other) || cacheKey(in) == cacheKey(old) {
		t.Fatal("cache aliases old or different direction")
	}
	before, _ := AudioVersion("pt-BR")
	enBefore, _ := AudioVersion("en-US")
	saved := narrationProfiles["teaching"]
	defer func() { narrationProfiles["teaching"] = saved }()
	changed := saved
	changed.Direction += " Revisão editorial."
	narrationProfiles["teaching"] = changed
	after, _ := AudioVersion("pt-BR")
	enAfter, _ := AudioVersion("en-US")
	if before == after || enBefore != enAfter {
		t.Fatal("incorrect manifest invalidation")
	}
}
func TestGeminiBudgetIncludesEveryPromptAndMaximumOutput(t *testing.T) {
	in, _ := Prepare(Request{BookID: "John", Chapter: 1, Language: "pt-BR", Text: "A.\nB.", Verses: []Verse{{18, "A."}, {19, "B."}}})
	var want int64
	for _, p := range in.speechParts() {
		seg := in
		seg.Text = p.text
		seg.style = p.style
		want += int64(len(p.text)+len(seg.narrationPrompt())) + 327680
	}
	if got := EstimateCost(in, true); got != want {
		t.Fatalf("underreservation: %d != %d", got, want)
	}
	if got := in.measuredCost(1); got != in.geminiInputCost()+500 {
		t.Fatal("wrong audio token price", got)
	}
}
func TestGeminiTimedArtifactAndCache(t *testing.T) {
	ffmpeg, err := exec.LookPath("ffmpeg")
	if err != nil {
		t.Skip("run in API container with ffmpeg")
	}
	var calls atomic.Int32
	s := testService(t, func(w http.ResponseWriter, r *http.Request) {
		calls.Add(1)
		json.NewEncoder(w).Encode(map[string][]byte{"audioContent": testWAV()})
	})
	s.ffmpeg = ffmpeg
	s.cacheDir = t.TempDir()
	in, _ := Prepare(Request{BookID: "John", Chapter: 1, Language: "pt-BR", Text: "A.\nB.", Verses: []Verse{{18, "A."}, {19, "B."}}})
	got, err := s.SynthesizeTimed(context.Background(), in)
	if err != nil || len(got.Audio) == 0 || !validCues(got.Cues) || len(got.Cues) != 2 || got.Cues[1].VerseStart != 19 {
		t.Fatalf("artifact: %+v %v", got.Cues, err)
	}
	if _, err = s.SynthesizeTimed(context.Background(), in); err != nil || calls.Load() != 2 {
		t.Fatal("cache miss", err, calls.Load())
	}
	in.Verses = nil
	if audio, err := s.Synthesize(context.Background(), in); err != nil || len(audio) == 0 {
		t.Fatal("untimed Gemini encoding", err)
	}
}
