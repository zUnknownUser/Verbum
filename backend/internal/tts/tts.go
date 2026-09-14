// Package tts synthesizes exact supplied text with Google Cloud Text-to-Speech.
// It follows the existing embeddings/synthesis clients: one HTTP client, injected
// into httpapi. It has no database, editorial, or chapter-reading responsibilities.
package tts

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"math"
	"net"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"sync"
	"time"
	"unicode/utf8"

	"golang.org/x/oauth2"
	"golang.org/x/oauth2/google"
)

const endpoint = "https://texttospeech.googleapis.com/v1/text:synthesize"
const MaxTextBytes = 100000

// Below Google's 5000-byte ceiling: near-limit Chirp segments exceeded the
// provider timeout during local validation. Larger than the original 1000
// (re-validated up to ~2200 without approaching that timeout) because every
// join is an audible seam (see crossfadeSeconds): fewer, larger segments
// mean fewer seams in a chapter's reading.
const segmentBytes = 2200
const maxResponseBytes = 32 << 20
const GenerationTimeout = 10 * time.Minute

var (
	ErrInvalidInput = errors.New("invalid speech request")
	ErrCredentials  = errors.New("Google TTS credentials or permissions unavailable")
	ErrQuota        = errors.New("Google TTS quota exhausted")
	ErrUnavailable  = errors.New("Google TTS unavailable")
	ErrTimeout      = errors.New("Google TTS timed out")
	ErrResponse     = errors.New("invalid Google TTS response")
)

// Request identifies a cached chapter by exact supplied text (translation-sensitive),
// language, resolved voice, speed, pitch and format, with a provider/version namespace.
type Request struct {
	Text     string   `json:"text"`
	Language string   `json:"language"`
	Voice    string   `json:"voice,omitempty"`
	Speed    *float64 `json:"speed,omitempty"`
	Pitch    float64  `json:"pitch,omitempty"`
	Format   string   `json:"format,omitempty"`
}

func (r Request) normalized() (Request, error) {
	if !utf8.ValidString(r.Text) || strings.TrimSpace(r.Text) == "" || len(r.Text) > MaxTextBytes {
		return r, fmt.Errorf("%w: text must contain 1–100000 UTF-8 bytes", ErrInvalidInput)
	}
	switch r.Language {
	case "pt-BR", "en-US":
	default:
		return r, fmt.Errorf("%w: language must be pt-BR or en-US", ErrInvalidInput)
	}
	if r.Voice == "" {
		r.Voice = r.Language + "-Standard-A"
		if r.Language == "pt-BR" {
			r.Voice = "pt-BR-Chirp3-HD-Aoede"
		}
	}
	if !strings.HasPrefix(r.Voice, r.Language+"-") || len(r.Voice) > 128 || strings.ContainsAny(r.Voice, " \t\r\n") {
		return r, fmt.Errorf("%w: voice must match language", ErrInvalidInput)
	}
	if r.Speed == nil {
		speed := 1.0
		r.Speed = &speed
	}
	if math.IsNaN(*r.Speed) || math.IsInf(*r.Speed, 0) || *r.Speed < 0.25 || *r.Speed > 2 {
		return r, fmt.Errorf("%w: speed must be 0.25–2.0", ErrInvalidInput)
	}
	if math.IsNaN(r.Pitch) || math.IsInf(r.Pitch, 0) || r.Pitch < -20 || r.Pitch > 20 {
		return r, fmt.Errorf("%w: pitch must be -20–20", ErrInvalidInput)
	}
	if strings.Contains(r.Voice, "-Chirp3-HD-") && r.Pitch != 0 {
		return r, fmt.Errorf("%w: pitch must be omitted or zero for Chirp 3 HD", ErrInvalidInput)
	}
	if r.Format == "" {
		r.Format = "MP3"
	}
	if r.Format != "MP3" {
		return r, fmt.Errorf("%w: only MP3 is supported", ErrInvalidInput)
	}
	return r, nil
}

type TextToSpeechService struct {
	client   *http.Client
	endpoint string
	cacheDir string
	ffmpeg   string
	once     sync.Once
	gate     chan struct{}
}

// New uses ADC, including GOOGLE_APPLICATION_CREDENTIALS. The token source must
// have a process-lifetime context: cancelling a startup context would break refresh.
func New() (*TextToSpeechService, error) {
	authContext := context.WithValue(context.Background(), oauth2.HTTPClient, &http.Client{Timeout: 10 * time.Second})
	client, err := google.DefaultClient(authContext, "https://www.googleapis.com/auth/cloud-platform")
	if err != nil {
		return nil, ErrCredentials
	}
	client.Timeout = 60 * time.Second
	cacheDir := os.Getenv("VERBUM_TTS_CACHE_DIR")
	if cacheDir == "" {
		base, err := os.UserCacheDir()
		if err != nil {
			return nil, ErrUnavailable
		}
		cacheDir = filepath.Join(base, "verbum", "tts")
	}
	if os.MkdirAll(cacheDir, 0700) != nil {
		return nil, ErrUnavailable
	}
	probe, err := os.CreateTemp(cacheDir, ".probe-*")
	if err != nil {
		return nil, ErrUnavailable
	}
	probe.Close()
	os.Remove(probe.Name())
	ffmpeg, err := exec.LookPath("ffmpeg")
	if err != nil {
		return nil, ErrUnavailable
	}
	return &TextToSpeechService{client: client, endpoint: endpoint, cacheDir: cacheDir, ffmpeg: ffmpeg}, nil
}

// Synthesize returns MP3 bytes or a safe classified error. Google error bodies,
// tokens, credentials and submitted text are never returned in errors or logged.
func (s *TextToSpeechService) synthesizeSegment(ctx context.Context, input Request, encoding string) ([]byte, error) {
	config := map[string]any{"audioEncoding": encoding, "speakingRate": *input.Speed}
	if !strings.Contains(input.Voice, "-Chirp3-HD-") {
		config["pitch"] = input.Pitch
	}
	if encoding == "LINEAR16" {
		config["sampleRateHertz"] = 24000
	}
	body, _ := json.Marshal(map[string]any{
		"input":       map[string]string{"text": input.Text},
		"voice":       map[string]string{"languageCode": input.Language, "name": input.Voice},
		"audioConfig": config,
	})
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, s.endpoint, bytes.NewReader(body))
	if err != nil {
		return nil, ErrUnavailable
	}
	req.Header.Set("Content-Type", "application/json")
	response, err := s.client.Do(req)
	if err != nil {
		if ctx.Err() != nil {
			return nil, ctx.Err()
		}
		var timed net.Error
		if errors.As(err, &timed) && timed.Timeout() {
			return nil, ErrTimeout
		}
		var auth *oauth2.RetrieveError
		if errors.As(err, &auth) {
			return nil, ErrCredentials
		}
		return nil, ErrUnavailable
	}
	defer response.Body.Close()
	switch response.StatusCode {
	case http.StatusOK:
	case http.StatusBadRequest:
		return nil, fmt.Errorf("%w: Google rejected the voice or audio configuration", ErrInvalidInput)
	case http.StatusUnauthorized, http.StatusForbidden:
		return nil, ErrCredentials
	case http.StatusTooManyRequests:
		return nil, ErrQuota
	case http.StatusRequestTimeout, http.StatusGatewayTimeout:
		return nil, ErrTimeout
	default:
		return nil, ErrUnavailable
	}
	raw, err := io.ReadAll(io.LimitReader(response.Body, maxResponseBytes+1))
	if err != nil {
		if ctx.Err() != nil {
			return nil, ctx.Err()
		}
		var timed net.Error
		if errors.As(err, &timed) && timed.Timeout() {
			return nil, ErrTimeout
		}
		return nil, ErrResponse
	}
	if len(raw) > maxResponseBytes {
		return nil, ErrResponse
	}
	var parsed struct {
		Audio []byte `json:"audioContent"`
	}
	if json.Unmarshal(raw, &parsed) != nil || len(parsed.Audio) == 0 {
		return nil, ErrResponse
	}
	return parsed.Audio, nil
}
