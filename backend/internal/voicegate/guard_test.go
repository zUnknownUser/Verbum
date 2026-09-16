package voicegate

import (
	"encoding/base64"
	"testing"
	"time"
)

func TestClientCannotOverridePaidLimitsOrEnableAutomaticResponses(t *testing.T) {
	g := Guard{Started: time.Now(), Seconds: 60}
	e, err := g.Client(map[string]any{"type": "session.update", "session": map[string]any{"model": "expensive", "max_output_tokens": "inf", "instructions": "Help with Scripture", "tools": []any{}}})
	if err != nil {
		t.Fatal(err)
	}
	s := e["session"].(map[string]any)
	if s["max_output_tokens"] != 384 || s["model"] != nil {
		t.Fatal("client override survived")
	}
	audio := s["audio"].(map[string]any)["input"].(map[string]any)
	if audio["turn_detection"].(map[string]any)["create_response"] != false {
		t.Fatal("unmetered automatic response")
	}
	if _, e := g.Client(map[string]any{"type": "session.update", "session": map[string]any{}}); e == nil {
		t.Fatal("reconfiguration allowed")
	}
	if _, e := g.Client(map[string]any{"type": "conversation.item.create", "item": map[string]any{"type": "message", "content": []any{map[string]any{"type": "input_image"}}}}); e == nil {
		t.Fatal("paid image injection allowed")
	}
}
func TestAudioCannotBeUploadedFasterThanRealtime(t *testing.T) {
	g := Guard{Started: time.Now(), Seconds: 60, Configured: true}
	frame := map[string]any{"type": "input_audio_buffer.append", "audio": base64.StdEncoding.EncodeToString(make([]byte, 48000))}
	for i := 0; i < 2; i++ {
		if _, e := g.Client(frame); e != nil {
			t.Fatal(e)
		}
	}
	if _, e := g.Client(frame); e == nil {
		t.Fatal("accelerated audio accepted")
	}
}
func TestVoiceUsageIncludesBothModalities(t *testing.T) {
	e := map[string]any{"response": map[string]any{"usage": map[string]any{"input_token_details": map[string]any{"text_tokens": float64(100), "audio_tokens": float64(10)}, "output_token_details": map[string]any{"text_tokens": float64(20), "audio_tokens": float64(50)}}}}
	cost, known := responseCost(e)
	if !known || cost != 4240 {
		t.Fatal(cost, known)
	}
}
