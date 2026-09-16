package voicegate

import (
	"encoding/base64"
	"encoding/json"
	"fmt"
	"time"
)

type Guard struct {
	Started    time.Time
	Seconds    int
	Configured bool
	AudioBytes int
	TextBytes  int
	Responses  int
}

func (g *Guard) Client(e map[string]any) (map[string]any, error) {
	invalid := fmt.Errorf("voice event exceeds policy")
	kind, _ := e["type"].(string)
	switch kind {
	case "session.update":
		if g.Configured {
			return nil, invalid
		}
		s, ok := e["session"].(map[string]any)
		if !ok {
			return nil, invalid
		}
		instructions, _ := s["instructions"].(string)
		if len(instructions) > 16000 {
			return nil, invalid
		}
		tools, _ := s["tools"].([]any)
		if tools == nil {
			tools = []any{}
		}
		raw, _ := json.Marshal(tools)
		if len(tools) > 8 || len(raw) > 8192 {
			return nil, invalid
		}
		for _, t := range tools {
			m, ok := t.(map[string]any)
			if !ok || m["type"] != "function" {
				return nil, invalid
			}
		}
		language := "en"
		voice := "marin"
		if audio, ok := s["audio"].(map[string]any); ok {
			if input, ok := audio["input"].(map[string]any); ok {
				if tr, ok := input["transcription"].(map[string]any); ok && tr["language"] == "pt" {
					language = "pt"
				}
			}
		}
		g.Configured = true
		return map[string]any{"type": kind, "session": map[string]any{
			"type": "realtime", "instructions": instructions, "tools": tools, "tool_choice": "auto", "output_modalities": []string{"audio"}, "max_output_tokens": 384,
			"truncation": map[string]any{"type": "retention_ratio", "retention_ratio": 0.8, "token_limits": map[string]any{"post_instructions": 2048}},
			"audio":      map[string]any{"input": map[string]any{"format": map[string]any{"type": "audio/pcm", "rate": 24000}, "transcription": map[string]any{"model": "gpt-4o-mini-transcribe", "language": language}, "turn_detection": map[string]any{"type": "server_vad", "threshold": 0.65, "prefix_padding_ms": 300, "silence_duration_ms": 800, "create_response": false, "interrupt_response": false}}, "output": map[string]any{"format": map[string]any{"type": "audio/pcm", "rate": 24000}, "voice": voice}},
		}}, nil
	case "input_audio_buffer.append":
		if !g.Configured {
			return nil, invalid
		}
		s, _ := e["audio"].(string)
		b, err := base64.StdEncoding.DecodeString(s)
		if err != nil || len(b) > 48000 || len(b)%2 != 0 {
			return nil, invalid
		}
		g.AudioBytes += len(b)
		if g.AudioBytes > g.Seconds*48000 || g.AudioBytes > int(time.Since(g.Started).Seconds()+2)*48000 {
			return nil, invalid
		}
		return map[string]any{"type": kind, "audio": s}, nil
	case "response.create":
		if !g.Configured {
			return nil, invalid
		}
		r := map[string]any{"max_output_tokens": 384}
		if requested, ok := e["response"].(map[string]any); ok {
			if text, ok := requested["instructions"].(string); ok {
				if len(text) > 1500 {
					return nil, invalid
				}
				r["instructions"] = text
			}
		}
		return map[string]any{"type": kind, "response": r}, nil
	case "conversation.item.create":
		item, ok := e["item"].(map[string]any)
		if !ok || item["type"] != "function_call_output" {
			return nil, invalid
		}
		output, _ := item["output"].(string)
		id, _ := item["call_id"].(string)
		g.TextBytes += len(output)
		if len(output) > 8000 || g.TextBytes > 32000 || len(id) > 150 {
			return nil, invalid
		}
		return map[string]any{"type": kind, "item": map[string]any{"type": "function_call_output", "call_id": id, "output": output}}, nil
	case "response.cancel", "input_audio_buffer.clear":
		return map[string]any{"type": kind}, nil
	default:
		return nil, invalid
	}
}
