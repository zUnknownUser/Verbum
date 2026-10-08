package main

import (
	"context"
	"encoding/json"
	"testing"
	"time"
	"verbum/backend/internal/httpapi"
	"verbum/backend/internal/tts"
	"verbum/backend/internal/usage"
)

// Any reservation/provider use during inventory would panic through the nil
// embedded Store. Only read-only cache access is implemented here.
type inventoryStore struct {
	usage.Store
	values map[string][]byte
}

func (s inventoryStore) Cached(_ context.Context, key string, _ time.Time) ([]byte, error) {
	return s.values[key], nil
}

func TestInventoryReusesPlaybackIdentity(t *testing.T) {
	t.Setenv("VERBUM_TTS_NARRATOR", "gemini")
	in, err := tts.Prepare(tts.Request{BookID: "John", Chapter: 1, Translation: "por_blj", Language: "pt-BR", Text: "Texto.", Verses: []tts.Verse{{Number: 1, Text: "Texto."}}})
	if err != nil {
		t.Fatal(err)
	}
	audio, _ := json.Marshal(tts.TimedAudio{Audio: []byte("existing-mp3")})
	current := usage.Hash("cost-v1", "tts", httpapi.SpeechCacheKey(in, true))
	legacy := usage.Hash("cost-v1", "tts", usage.Hash(tts.LegacyEconomicIdentity(in), "true"))
	for _, key := range []string{current, legacy} {
		found, err := cached(context.Background(), inventoryStore{values: map[string][]byte{key: audio}}, nil, in)
		if err != nil || !found {
			t.Fatalf("existing narration not reused: %v", err)
		}
	}
	found, err := cached(context.Background(), inventoryStore{}, nil, in)
	if err != nil || found {
		t.Fatal("missing narration reported cached")
	}
	_, err = cached(context.Background(), inventoryStore{values: map[string][]byte{current: []byte(`{}`)}}, nil, in)
	if err == nil {
		t.Fatal("corrupt artifact accepted")
	}
}
