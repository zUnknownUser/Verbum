package httpapi

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"verbum/backend/internal/tts"
)

type fakeTTS struct {
	got    tts.Request
	err    error
	called bool
}

func (f *fakeTTS) Synthesize(_ context.Context, r tts.Request) ([]byte, error) {
	f.called = true
	f.got = r
	return []byte("ID3audio"), f.err
}
func speechRequest(service TextToSpeech, body, media string) *httptest.ResponseRecorder {
	server := New(nil, time.Now, nil, nil, nil, service)
	req := httptest.NewRequest("POST", "/v1/tts", bytes.NewBufferString(body))
	req.Header.Set("Content-Type", media)
	response := httptest.NewRecorder()
	server.ServeHTTP(response, req)
	return response
}

func TestTTSSuccess(t *testing.T) {
	f := &fakeTTS{}
	response := speechRequest(f, `{"text":"Texto","language":"pt-BR","voice":"pt-BR-Standard-A","speed":0.9,"pitch":-1,"format":"MP3"}`, "application/json; charset=utf-8")
	if response.Code != 200 || response.Body.String() != "ID3audio" || response.Header().Get("Content-Type") != "audio/mpeg" || response.Header().Get("Cache-Control") != "no-store" {
		t.Fatalf("response: %v", response)
	}
	if f.got.Text != "Texto" || f.got.Speed == nil || *f.got.Speed != 0.9 || f.got.Pitch != -1 {
		t.Fatalf("input: %+v", f.got)
	}
}

func TestTTSNotConfigured(t *testing.T) {
	response := speechRequest(nil, `{}`, "application/json")
	if response.Code != 503 {
		t.Fatal(response.Code)
	}
	var p Problem
	json.Unmarshal(response.Body.Bytes(), &p)
	if p.Code != CodeTTSUnavailable {
		t.Fatal(p)
	}
}

func TestTTSMalformedHTTP(t *testing.T) {
	for _, body := range []string{`bad`, `{"text":2}`, `{"unknown":true}`, `{} {}`, `{} trailing`, strings.Repeat(" ", (1<<20)+1)} {
		f := &fakeTTS{}
		r := speechRequest(f, body, "application/json")
		if r.Code != 400 || f.called {
			t.Errorf("malformed: %d called %v", r.Code, f.called)
		}
	}
	f := &fakeTTS{}
	if r := speechRequest(f, `{}`, "text/plain"); r.Code != 415 || f.called {
		t.Fatal("media type accepted")
	}
}

func TestTTSErrorMapping(t *testing.T) {
	for _, test := range []struct {
		err    error
		status int
		code   string
	}{
		{tts.ErrInvalidInput, 400, CodeMalformedRequest}, {tts.ErrCredentials, 503, CodeTTSUnavailable},
		{tts.ErrUnavailable, 503, CodeTTSUnavailable}, {tts.ErrQuota, 429, CodeTTSRateLimited},
		{tts.ErrTimeout, 504, CodeTTSTimeout}, {context.DeadlineExceeded, 504, CodeTTSTimeout},
		{errors.New("PRIVATE PROVIDER DETAIL"), 502, CodeTTSFailed},
	} {
		r := speechRequest(&fakeTTS{err: test.err}, `{"text":"x","language":"pt-BR"}`, "application/json")
		var p Problem
		json.NewDecoder(r.Body).Decode(&p)
		if r.Code != test.status || p.Code != test.code || strings.Contains(p.Message, "PRIVATE") {
			t.Errorf("mapping: %d %+v", r.Code, p)
		}
	}
}

func TestTTSPostOnly(t *testing.T) {
	r := httptest.NewRecorder()
	New(nil, time.Now, nil, nil, nil, nil).ServeHTTP(r, httptest.NewRequest(http.MethodGet, "/v1/tts", nil))
	body, _ := io.ReadAll(r.Result().Body)
	if r.Code != 405 {
		t.Fatalf("status %d body %s", r.Code, body)
	}
}

type slowTTS struct{}

func (slowTTS) Synthesize(ctx context.Context, _ tts.Request) ([]byte, error) {
	select {
	case <-time.After(100 * time.Millisecond):
		return []byte("MP3"), nil
	case <-ctx.Done():
		return nil, ctx.Err()
	}
}

func TestTTSChapterBodyAndScopedDeadline(t *testing.T) {
	server := httptest.NewUnstartedServer(New(nil, time.Now, nil, nil, nil, slowTTS{}))
	server.Config.WriteTimeout = 30 * time.Millisecond
	server.Start()
	defer server.Close()
	body, _ := json.Marshal(map[string]string{"text": strings.Repeat("a", 40000), "language": "pt-BR"})
	response, err := server.Client().Post(server.URL+"/v1/tts", "application/json", bytes.NewReader(body))
	if err != nil {
		t.Fatal(err)
	}
	defer response.Body.Close()
	audio, err := io.ReadAll(response.Body)
	if err != nil || response.StatusCode != 200 || string(audio) != "MP3" {
		t.Fatalf("deadline/body: %v %d", err, response.StatusCode)
	}
	f := &fakeTTS{}
	body, _ = json.Marshal(map[string]string{"text": strings.Repeat("a", 1<<20), "language": "pt-BR"})
	if response := speechRequest(f, string(body), "application/json"); response.Code != 400 || f.called {
		t.Fatal("oversize body accepted")
	}
}

type fakeTimedTTS struct{ fakeTTS }

func (f *fakeTimedTTS) SynthesizeTimed(_ context.Context, r tts.Request) (tts.TimedAudio, error) {
	f.got = r
	return tts.TimedAudio{Audio: []byte("ID3timed"), Cues: []tts.Cue{{VerseStart: 1, VerseEnd: 1, Start: 0, End: 2.5}}}, nil
}
func TestSynchronizedSpeechMetadataAndLegacyCompatibility(t *testing.T) {
	service := &fakeTimedTTS{}
	res := speechRequest(service, `{"text":"Texto","language":"pt-BR","verses":[{"number":1,"text":"Texto"}]}`, "application/json")
	if res.Code != 200 || res.Body.String() != "ID3timed" {
		t.Fatal(res)
	}
	var cues []tts.Cue
	if json.Unmarshal([]byte(res.Header().Get("X-Verbum-Audio-Cues")), &cues) != nil || len(cues) != 1 || cues[0].End != 2.5 {
		t.Fatal("missing timing header")
	}
	legacy := speechRequest(service, `{"text":"Texto","language":"pt-BR"}`, "application/json")
	if legacy.Code != 200 || legacy.Body.String() != "ID3audio" || legacy.Header().Get("X-Verbum-Audio-Cues") != "" {
		t.Fatal("legacy playback changed")
	}
}
