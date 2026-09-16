package tts

import (
	"errors"
	"strings"
	"testing"
)

func TestAudioVersion(t *testing.T) {
	pt, err := AudioVersion("pt-BR")
	if err != nil || len(pt) != 64 {
		t.Fatal("invalid version", err)
	}
	en, _ := AudioVersion("en-US")
	if pt == en {
		t.Fatal("language must change version")
	}
	if _, err := AudioVersion("unknown"); !errors.Is(err, ErrInvalidInput) {
		t.Fatal("invalid language accepted")
	}
	legacy, _ := (Request{Text: "chapter", Language: "pt-BR"}).normalized()
	guarded, err := (Request{Text: "chapter", Language: "pt-BR", Revision: pt}).normalized()
	if err != nil || cacheKey(legacy) != cacheKey(guarded) {
		t.Fatal("guard must preserve existing server cache", err)
	}
	if _, err := (Request{Text: "chapter", Language: "pt-BR", Revision: "old"}).normalized(); !errors.Is(err, ErrInvalidInput) {
		t.Fatal("stale revision accepted")
	}
}

func TestRollbackAcceptsRecentlyPublishedGeminiManifest(t *testing.T) {
	t.Setenv("VERBUM_TTS_NARRATOR", "chirp3")
	current, err := AudioVersion("pt-BR")
	if err != nil {
		t.Fatal(err)
	}
	previous := geminiAudioVersion()
	if current == previous {
		t.Fatal("rollback did not change voice")
	}
	r, err := (Request{Text: "passage", Language: "pt-BR", Revision: previous}).normalized()
	if err != nil || r.Voice != "pt-BR-Chirp3-HD-Aoede" {
		t.Fatalf("stale mobile revision failed to use Portuguese rollback: %v %s", err, r.Voice)
	}
}

func TestPreviouslyCachedGeminiVersionRemainsReusable(t *testing.T) {
	input, _ := (Request{Text: "audio-version", Language: "pt-BR", Voice: NarrationVoice}).normalized()
	old := LegacyEconomicIdentity(input)
	if old != "2f259b36683fd3a97aa2b6d6f2939454178a0717067d9c6bf9b4489ca74cae30.mp3" {
		t.Fatal("legacy cache changed", old)
	}
	_, err := (Request{Text: "Text", Language: "pt-BR", Revision: strings.TrimSuffix(old, ".mp3")}).normalized()
	if err != nil {
		t.Fatal(err)
	}
}
