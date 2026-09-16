package httpapi

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"sync/atomic"
	"testing"
	"time"
	"verbum/backend/internal/tts"
	"verbum/backend/internal/usage"
)

type playbackUsage struct {
	usage.Store
	denied   bool
	reserves atomic.Int32
}

func (s *playbackUsage) Cached(context.Context, string, time.Time) ([]byte, error) { return nil, nil }
func (s *playbackUsage) Bind(context.Context, string, string, time.Time) error     { return nil }
func (s *playbackUsage) Lock(context.Context, string) (func(), error)              { return func() {}, nil }
func (s *playbackUsage) PutCache(context.Context, string, []byte, time.Time) error { return nil }
func (s *playbackUsage) Settle(context.Context, usage.Charge, int64, bool) error   { return nil }
func (s *playbackUsage) Reserve(_ context.Context, _ usage.Principal, _ usage.Policy, _ string, estimate int64, _ time.Time) (usage.Charge, error) {
	s.reserves.Add(1)
	if s.denied {
		return usage.Charge{}, &usage.Denial{Code: "budget_exhausted"}
	}
	return usage.Charge{Reserved: estimate}, nil
}

type waitingSpeech struct {
	calls   atomic.Int32
	release chan struct{}
}

func (s *waitingSpeech) Synthesize(context.Context, tts.Request) ([]byte, error) { panic("untimed") }
func (s *waitingSpeech) SynthesizeTimed(ctx context.Context, _ tts.Request) (tts.TimedAudio, error) {
	s.calls.Add(1)
	select {
	case <-s.release:
	case <-ctx.Done():
	}
	return tts.TimedAudio{}, tts.ErrUnavailable
}

func TestPlaybackAdmissionDeduplicatesAndPreservesBudget(t *testing.T) {
	for _, denied := range []bool{false, true} {
		t.Run(map[bool]string{false: "shared", true: "budget"}[denied], func(t *testing.T) {
			meter := &playbackUsage{denied: denied}
			speaker := &waitingSpeech{release: make(chan struct{})}
			defer close(speaker.release)
			h := &handlers{playback: newPlaybackHub(), tts: economicSpeech{next: speaker, usage: usage.New(meter, usage.Defaults()), verify: func(_ context.Context, r tts.Request) (tts.Request, error) { return r, nil }}}
			t.Cleanup(func() {
				for _, j := range h.playback.jobs {
					j.cancel()
					<-j.ready
					os.RemoveAll(j.dir)
				}
			})
			body := `{"text":"Texto.","language":"pt-BR","bookId":"Ps","chapter":55,"verses":[{"number":1,"text":"Texto."}]}`
			call := func(auth bool) *httptest.ResponseRecorder {
				r := httptest.NewRequest("POST", "/v1/tts/playback", strings.NewReader(body))
				r.Header.Set("Content-Type", "application/json")
				if auth {
					r = r.WithContext(usage.WithPrincipal(r.Context(), usage.Principal{UID: "reader"}))
				}
				w := httptest.NewRecorder()
				h.startPlayback(w, r)
				return w
			}
			if call(false).Code != 401 {
				t.Fatal("anonymous generation allowed")
			}
			first, second := call(true), call(true)
			if first.Code != 200 || first.Body.String() != second.Body.String() {
				t.Fatalf("jobs not shared: %s %s", first.Body, second.Body)
			}
			deadline := time.Now().Add(time.Second)
			for meter.reserves.Load() == 0 && time.Now().Before(deadline) {
				time.Sleep(time.Millisecond)
			}
			if meter.reserves.Load() != 1 {
				t.Fatal("duplicate or missing reservation")
			}
			if denied {
				var reply map[string]string
				json.Unmarshal(first.Body.Bytes(), &reply)
				var j *playbackJob
				for _, v := range h.playback.jobs {
					j = v
				}
				<-j.ready
				if speaker.calls.Load() != 0 {
					t.Fatal("budget denial reached provider")
				}
				r := httptest.NewRequest("GET", reply["statusPath"], nil)
				r.SetPathValue("token", j.token)
				r.SetPathValue("asset", "status")
				w := httptest.NewRecorder()
				h.playbackMedia(w, r)
				if !strings.Contains(w.Body.String(), "budget_exhausted") {
					t.Fatal(w.Body.String())
				}
			}
		})
	}
}
func TestHLSOrderedSegmentsAndCompletion(t *testing.T) {
	if _, err := exec.LookPath("ffmpeg"); err != nil {
		t.Skip("ffmpeg integration")
	}
	dir := t.TempDir()
	source := filepath.Join(dir, "silence.wav")
	if err := exec.Command("ffmpeg", "-v", "error", "-f", "lavfi", "-i", "anullsrc=r=24000:cl=mono", "-t", "13", source).Run(); err != nil {
		t.Fatal(err)
	}
	job := &playbackJob{dir: dir, token: strings.Repeat("a", 64), expires: time.Now().Add(time.Hour), ready: make(chan struct{})}
	h := &handlers{playback: newPlaybackHub()}
	h.playback.tokens[job.token] = job
	if err := job.publish(context.Background(), source); err != nil {
		t.Fatal(err)
	}
	mux := http.NewServeMux()
	mux.HandleFunc("GET /v1/tts/playback/{token}/{asset}", h.playbackMedia)
	get := func(path string) *httptest.ResponseRecorder {
		w := httptest.NewRecorder()
		mux.ServeHTTP(w, httptest.NewRequest("GET", path, nil))
		return w
	}
	path := "/v1/tts/playback/" + job.token + "/index.m3u8"
	first := get(path).Body.String()
	if !strings.Contains(first, "#EXT-X-PLAYLIST-TYPE:EVENT") || strings.Contains(first, "#EXT-X-ENDLIST") || len(job.segments) != 3 {
		t.Fatal(first)
	}
	if err := job.publish(context.Background(), source); err != nil {
		t.Fatal(err)
	}
	job.done = true
	last := get(path).Body.String()
	if !strings.Contains(last, "#EXT-X-DISCONTINUITY") || !strings.Contains(last, "#EXT-X-ENDLIST") || len(job.segments) != 6 {
		t.Fatal(last)
	}
	for _, s := range job.segments {
		if w := get("/v1/tts/playback/" + job.token + "/" + s.name); w.Code != 200 || w.Body.Len() == 0 {
			t.Fatal("missing segment")
		}
	}
	if w := get("/v1/tts/playback/" + strings.Repeat("b", 64) + "/index.m3u8"); w.Code != 404 {
		t.Fatal("invalid capability")
	}
	server := httptest.NewServer(mux)
	defer server.Close()
	if output, err := exec.Command("ffmpeg", "-nostdin", "-v", "error", "-i", server.URL+path, "-f", "null", "-").CombinedOutput(); err != nil {
		t.Fatalf("HLS decoding failed: %s %v", output, err)
	}
	job.err = tts.ErrUnavailable
	if get(path).Code != 503 {
		t.Fatal("partial failure presented as complete chapter")
	}
}

type cachedPlaybackUsage struct {
	playbackUsage
	payload []byte
}

func (s *cachedPlaybackUsage) Cached(context.Context, string, time.Time) ([]byte, error) {
	return s.payload, nil
}
func TestCompleteCachedAudioDoesNotRequireHLSOrProvider(t *testing.T) {
	payload, _ := json.Marshal(tts.TimedAudio{Audio: []byte("ID3cached"), Cues: []tts.Cue{{VerseStart: 1, VerseEnd: 1, Start: 0, End: 1}}})
	store := &cachedPlaybackUsage{payload: payload}
	speaker := &waitingSpeech{release: make(chan struct{})}
	job := &playbackJob{dir: t.TempDir(), ready: make(chan struct{}), cancel: func() {}}
	speech := economicSpeech{next: speaker, usage: usage.New(store, usage.Defaults()), verify: func(_ context.Context, r tts.Request) (tts.Request, error) { return r, nil }}
	ctx := usage.WithPrincipal(context.Background(), usage.Principal{UID: "reader"})
	job.generate(ctx, speech, tts.Request{BookID: "Ps", Chapter: 55, Language: "pt-BR", Text: "Texto.", Verses: []tts.Verse{{Number: 1, Text: "Texto."}}})
	if !job.done || job.err != nil || len(job.segments) != 0 || speaker.calls.Load() != 0 || store.reserves.Load() != 0 {
		t.Fatal("cached audio regenerated or repackaged", job.err)
	}
	audio, err := os.ReadFile(filepath.Join(job.dir, "chapter.mp3"))
	if err != nil || string(audio) != "ID3cached" {
		t.Fatal("cached audio missing")
	}
}
