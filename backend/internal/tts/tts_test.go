package tts

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

func testService(t *testing.T, h http.HandlerFunc) *TextToSpeechService {
	t.Helper()
	server := httptest.NewServer(h)
	t.Cleanup(server.Close)
	return &TextToSpeechService{client: server.Client(), endpoint: server.URL}
}

func TestSynthesize(t *testing.T) {
	for _, language := range []string{"pt-BR", "en-US"} {
		t.Run(language, func(t *testing.T) {
			service := testService(t, func(w http.ResponseWriter, r *http.Request) {
				var got struct {
					Input  struct{ Text string }               `json:"input"`
					Voice  struct{ LanguageCode, Name string } `json:"voice"`
					Config struct {
						AudioEncoding       string
						SpeakingRate, Pitch float64
					} `json:"audioConfig"`
				}
				if r.Method != "POST" || r.Header.Get("Content-Type") != "application/json" {
					t.Error("incorrect HTTP request")
				}
				if err := json.NewDecoder(r.Body).Decode(&got); err != nil {
					t.Fatal(err)
				}
				voice := language + "-Standard-A"
				if language == "pt-BR" {
					voice = "pt-BR-Chirp3-HD-Aoede"
				}
				if got.Input.Text != "  Texto exato.\n" || got.Voice.LanguageCode != language || got.Voice.Name != voice || got.Config.AudioEncoding != "MP3" || got.Config.SpeakingRate != 1 || got.Config.Pitch != 0 {
					t.Errorf("unexpected payload: %+v", got)
				}
				json.NewEncoder(w).Encode(map[string][]byte{"audioContent": []byte("ID3audio")})
			})
			audio, err := service.Synthesize(context.Background(), Request{Text: "  Texto exato.\n", Language: language, Voice: language + map[string]string{"pt-BR": "-Chirp3-HD-Aoede", "en-US": "-Standard-A"}[language]})
			if err != nil || string(audio) != "ID3audio" {
				t.Fatalf("audio %q err %v", audio, err)
			}
		})
	}
}

func TestCustomSettings(t *testing.T) {
	service := testService(t, func(w http.ResponseWriter, r *http.Request) {
		var body map[string]any
		json.NewDecoder(r.Body).Decode(&body)
		voice := body["voice"].(map[string]any)
		config := body["audioConfig"].(map[string]any)
		if voice["name"] != "pt-BR-Wavenet-A" || config["speakingRate"] != 0.85 || config["pitch"] != float64(-2) {
			t.Errorf("settings: %v", body)
		}
		json.NewEncoder(w).Encode(map[string][]byte{"audioContent": []byte("audio")})
	})
	speed := 0.85
	_, err := service.Synthesize(context.Background(), Request{Text: "Texto", Language: "pt-BR", Voice: "pt-BR-Wavenet-A", Speed: &speed, Pitch: -2, Format: "MP3"})
	if err != nil {
		t.Fatal(err)
	}
}

func TestInvalidRequestsNeverCallGoogle(t *testing.T) {
	service := testService(t, func(w http.ResponseWriter, r *http.Request) { t.Error("unexpected provider call") })
	zero := 0.0
	for _, input := range []Request{
		{}, {Text: "  ", Language: "pt-BR"}, {Text: strings.Repeat("á", MaxTextBytes/2+1), Language: "pt-BR"},
		{Text: "x", Language: "pt-PT"}, {Text: "x", Language: "pt-BR", Voice: "en-US-Standard-A"},
		{Text: "x", Language: "pt-BR", Speed: &zero}, {Text: "x", Language: "pt-BR", Pitch: 21},
		{Text: "x", Language: "pt-BR", Format: "LINEAR16"},
	} {
		if _, err := service.Synthesize(context.Background(), input); !errors.Is(err, ErrInvalidInput) {
			t.Errorf("expected invalid input, got %v", err)
		}
	}
	if _, err := (Request{Text: strings.Repeat("á", 2500), Language: "pt-BR", Voice: "pt-BR-Chirp3-HD-Aoede"}).normalized(); err != nil {
		t.Fatal(err)
	}
}

func TestProviderErrors(t *testing.T) {
	for status, want := range map[int]error{400: ErrInvalidInput, 401: ErrCredentials, 403: ErrCredentials, 429: ErrQuota, 500: ErrUnavailable, 503: ErrUnavailable, 504: ErrTimeout} {
		service := testService(t, func(w http.ResponseWriter, r *http.Request) {
			w.WriteHeader(status)
			w.Write([]byte(`{"error":{"message":"PRIVATE PROVIDER DETAIL"}}`))
		})
		_, err := service.Synthesize(context.Background(), Request{Text: "x", Language: "pt-BR", Voice: "pt-BR-Chirp3-HD-Aoede"})
		if !errors.Is(err, want) || strings.Contains(err.Error(), "PRIVATE") {
			t.Errorf("status %d: %v", status, err)
		}
	}
}

func TestMalformedProviderResponses(t *testing.T) {
	for _, body := range []string{`{}`, `{"audioContent":""}`, `{"audioContent":"bad base64"}`, `not json`, strings.Repeat(" ", maxResponseBytes+1)} {
		service := testService(t, func(w http.ResponseWriter, r *http.Request) { w.Write([]byte(body)) })
		if _, err := service.Synthesize(context.Background(), Request{Text: "x", Language: "pt-BR", Voice: "pt-BR-Chirp3-HD-Aoede"}); !errors.Is(err, ErrResponse) {
			t.Errorf("want invalid response: %v", err)
		}
	}
}

func TestCancellationAndTimeout(t *testing.T) {
	service := testService(t, func(w http.ResponseWriter, r *http.Request) {
		// Bound the test server independently: HTTP/1 may not detect a disconnect
		// before consuming a request body. Never leave cleanup waiting forever.
		select {
		case <-r.Context().Done():
		case <-time.After(100 * time.Millisecond):
		}
	})
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	if _, err := service.Synthesize(ctx, Request{Text: "x", Language: "pt-BR", Voice: "pt-BR-Chirp3-HD-Aoede"}); !errors.Is(err, context.Canceled) {
		t.Fatalf("cancel: %v", err)
	}
	service.client.Timeout = 20 * time.Millisecond
	if _, err := service.Synthesize(context.Background(), Request{Text: "x", Language: "pt-BR", Voice: "pt-BR-Chirp3-HD-Aoede"}); !errors.Is(err, ErrTimeout) {
		t.Fatalf("timeout: %v", err)
	}
}

func TestInvalidCredentialFileDoesNotLeak(t *testing.T) {
	t.Setenv("GOOGLE_APPLICATION_CREDENTIALS", t.TempDir()+"/missing.json")
	if _, err := New(); !errors.Is(err, ErrCredentials) {
		t.Fatalf("credentials: %v", err)
	}
}
