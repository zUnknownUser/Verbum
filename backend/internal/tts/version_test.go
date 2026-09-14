package tts

import (
	"errors"
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
