package httpapi

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"mime"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"time"
	"verbum/backend/internal/reqid"
	"verbum/backend/internal/tts"
	"verbum/backend/internal/usage"
)

// Ephemeral transport assets. Canonical audio is still persisted by economicSpeech.
// One bounded generation per chapter; only authenticated POST can create a job.
// Media capabilities expire, cannot synthesize, and are redacted in request logs.
type playbackHub struct {
	mu     sync.Mutex
	jobs   map[string]*playbackJob
	tokens map[string]*playbackJob
}
type playbackJob struct {
	mu              sync.Mutex
	token, key, dir string
	expires         time.Time
	ready           chan struct{}
	notify          sync.Once
	segments        []hlsSegment
	done            bool
	err             error
	cues            []tts.Cue
	cancel          context.CancelFunc
}
type hlsSegment struct {
	name          string
	duration      float64
	discontinuity bool
}

func newPlaybackHub() *playbackHub {
	return &playbackHub{jobs: map[string]*playbackJob{}, tokens: map[string]*playbackJob{}}
}

func (h *handlers) startPlayback(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Cache-Control", "no-store")
	if usage.Identity(r.Context()).UID == "" {
		writeProblem(w, 401, CodeUnauthenticated, "authentication required")
		return
	}
	speech, ok := h.tts.(economicSpeech)
	if !ok || speech.verify == nil || speech.usage == nil {
		writeProblem(w, 503, CodeTTSUnavailable, "speech unavailable")
		return
	}
	media, _, _ := mime.ParseMediaType(r.Header.Get("Content-Type"))
	if media != "application/json" {
		writeProblem(w, 415, CodeMalformedRequest, "expected application/json")
		return
	}
	var input tts.Request
	decoder := json.NewDecoder(http.MaxBytesReader(w, r.Body, 1<<20))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(&input); err != nil {
		writeProblem(w, 400, CodeMalformedRequest, "invalid speech request")
		return
	}
	var extra any
	if decoder.Decode(&extra) != io.EOF || len(input.Verses) == 0 {
		writeProblem(w, 400, CodeMalformedRequest, "expected one chapter request")
		return
	}
	input, err := speech.verify(r.Context(), input)
	if err != nil {
		playbackError(w, err)
		return
	}
	prepared, err := tts.Prepare(input)
	if err != nil {
		playbackError(w, err)
		return
	}
	key := tts.EconomicIdentity(prepared)
	// Bind every caller before joining; an idempotency key cannot change chapters.
	if err := speech.usage.Bind(r.Context(), idempotency(r.Context()), usage.Hash("playback", key)); err != nil {
		playbackError(w, err)
		return
	}
	hub := h.playback
	hub.mu.Lock()
	job := hub.jobs[key]
	if job == nil {
		if len(hub.jobs) >= 32 {
			hub.mu.Unlock()
			writeProblem(w, 429, CodeTTSRateLimited, "playback capacity reached")
			return
		}
		tokenBytes := make([]byte, 32)
		if _, err = rand.Read(tokenBytes); err != nil {
			hub.mu.Unlock()
			playbackError(w, tts.ErrUnavailable)
			return
		}
		dir, dirErr := os.MkdirTemp("", "verbum-hls-*")
		if dirErr != nil {
			hub.mu.Unlock()
			playbackError(w, tts.ErrUnavailable)
			return
		}
		// A disconnected phone must not cancel an already paid generation shared by others.
		ctx, cancel := context.WithTimeout(context.WithoutCancel(r.Context()), tts.GenerationTimeout)
		// The POST key is already bound above; economicSpeech uses its own content binding.
		ctx = context.WithValue(ctx, idempotencyKey{}, "")
		job = &playbackJob{key: key, token: hex.EncodeToString(tokenBytes), dir: dir, expires: time.Now().Add(time.Hour), ready: make(chan struct{}), cancel: cancel}
		hub.jobs[key] = job
		hub.tokens[job.token] = job
		go job.generate(ctx, speech, input)
		time.AfterFunc(time.Hour, func() {
			hub.mu.Lock()
			delete(hub.jobs, key)
			delete(hub.tokens, job.token)
			hub.mu.Unlock()
			cancel()
			job.mu.Lock()
			defer job.mu.Unlock()
			os.RemoveAll(dir)
		})
	}
	hub.mu.Unlock()
	// Return immediately; native clients poll status, never repeat the paid POST.
	writeJSONNoStore(w, map[string]string{"statusPath": "/v1/tts/playback/" + job.token + "/status"})
}
func playbackError(w http.ResponseWriter, err error) {
	if writeUsageProblem(w, err) {
		return
	}
	if errors.Is(err, tts.ErrInvalidInput) {
		writeProblem(w, 400, CodeMalformedRequest, "invalid speech request")
		return
	}
	writeProblem(w, 503, CodeTTSUnavailable, "speech unavailable")
}
func (j *playbackJob) generate(ctx context.Context, speech economicSpeech, input tts.Request) {
	defer j.cancel()
	started := time.Now()
	var first sync.Once
	ctx = tts.WithProgress(ctx, func(ctx context.Context, path string, _ float64) error {
		err := j.publish(ctx, path)
		if err == nil {
			first.Do(func() {
				slog.Info("speech first excerpt ready", "reqID", reqid.From(ctx), "ms", time.Since(started).Milliseconds())
			})
		}
		return err
	})
	result, err := speech.SynthesizeTimed(ctx, input)
	// A complete cache hit is delivered directly as MP3; no repackaging or
	// second audio download is needed before playback.
	j.mu.Lock()
	j.err = err
	j.done = true
	if err == nil {
		j.err = os.WriteFile(filepath.Join(j.dir, "chapter.mp3"), result.Audio, 0600)
		j.cues = result.Cues
	}
	j.notify.Do(func() { close(j.ready) })
	j.mu.Unlock()
	slog.Info("speech playback completed", "reqID", reqid.From(ctx), "success", j.err == nil, "ms", time.Since(started).Milliseconds())
}
func (j *playbackJob) publish(ctx context.Context, source string) error {
	j.mu.Lock()
	prefix := len(j.segments)
	j.mu.Unlock()
	name := fmt.Sprintf("part-%04d", prefix)
	playlist := filepath.Join(j.dir, name+".m3u8")
	// Short TS segments give native players a stable target duration while the
	// model produces longer literary excerpts. No provider calls happen here.
	cmd := exec.CommandContext(ctx, "ffmpeg", "-nostdin", "-v", "error", "-i", source, "-vn", "-c:a", "aac", "-b:a", "96k", "-ar", "24000", "-f", "hls", "-hls_time", "6", "-hls_list_size", "0", "-hls_segment_filename", filepath.Join(j.dir, name+"-%04d.ts"), playlist)
	if cmd.Run() != nil {
		return tts.ErrUnavailable
	}
	raw, err := os.ReadFile(playlist)
	if err != nil {
		return err
	}
	var segments []hlsSegment
	var duration float64
	for _, line := range strings.Split(string(raw), "\n") {
		if strings.HasPrefix(line, "#EXTINF:") {
			duration, _ = strconv.ParseFloat(strings.TrimSuffix(strings.TrimPrefix(line, "#EXTINF:"), ","), 64)
		}
		if strings.HasSuffix(line, ".ts") {
			if duration <= 0 || duration > 10 || filepath.Base(line) != line {
				return tts.ErrResponse
			}
			segments = append(segments, hlsSegment{line, duration, prefix > 0 && len(segments) == 0})
		}
	}
	if len(segments) == 0 {
		return tts.ErrResponse
	}
	j.mu.Lock()
	j.segments = append(j.segments, segments...)
	j.notify.Do(func() { close(j.ready) })
	j.mu.Unlock()
	return nil
}
func (h *handlers) playbackMedia(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Cache-Control", "no-store")
	w.Header().Set("Referrer-Policy", "no-referrer")
	h.playback.mu.Lock()
	j := h.playback.tokens[r.PathValue("token")]
	h.playback.mu.Unlock()
	if j == nil || time.Now().After(j.expires) {
		http.NotFound(w, r)
		return
	}
	j.mu.Lock()
	defer j.mu.Unlock()
	asset := r.PathValue("asset")
	if asset == "status" {
		if j.err != nil {
			playbackError(w, j.err)
			return
		}
		base := "/v1/tts/playback/" + j.token + "/"
		writeJSONNoStore(w, map[string]any{"ready": len(j.segments) > 0 || (j.done && j.err == nil), "complete": j.done, "playlistPath": base + "index.m3u8", "audioPath": base + "chapter.mp3", "cues": j.cues})
		return
	}
	if asset == "chapter.mp3" && j.done && j.err == nil {
		w.Header().Set("Content-Type", "audio/mpeg")
		http.ServeFile(w, r, filepath.Join(j.dir, "chapter.mp3"))
		return
	}
	if j.err != nil {
		http.Error(w, "audio unavailable", 503)
		return
	}
	if asset == "index.m3u8" {
		if len(j.segments) == 0 {
			http.NotFound(w, r)
			return
		}
		w.Header().Set("Content-Type", "application/vnd.apple.mpegurl")
		fmt.Fprint(w, "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:10\n#EXT-X-MEDIA-SEQUENCE:0\n#EXT-X-PLAYLIST-TYPE:EVENT\n#EXT-X-START:TIME-OFFSET=0,PRECISE=YES\n")
		for _, s := range j.segments {
			if s.discontinuity {
				fmt.Fprint(w, "#EXT-X-DISCONTINUITY\n")
			}
			fmt.Fprintf(w, "#EXTINF:%.6f,\n%s\n", s.duration, s.name)
		}
		if j.done {
			fmt.Fprint(w, "#EXT-X-ENDLIST\n")
		}
		return
	}
	for _, s := range j.segments {
		if asset == s.name {
			w.Header().Set("Content-Type", "video/mp2t")
			http.ServeFile(w, r, filepath.Join(j.dir, s.name))
			return
		}
	}
	http.NotFound(w, r)
}
