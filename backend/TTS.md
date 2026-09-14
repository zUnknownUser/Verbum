# Google Cloud TTS: continuous chapter audio

`POST /v1/tts` keeps the existing request and binary `audio/mpeg` response.
The existing `internal/tts.TextToSpeechService` now handles normalization,
segmentation, synthesis, assembly and persistent caching. No new router, database
schema or App changes are required.

## Request

```json
{
  "text": "O Senhor é o meu pastor; nada me faltará.",
  "language": "pt-BR",
  "voice": "pt-BR-Chirp3-HD-Aoede",
  "speed": 0.9,
  "format": "MP3"
}
```

Omit `voice` to use `pt-BR-Chirp3-HD-Aoede` or, for English, the preserved
`en-US-Standard-A`. Explicit compatible Standard/Wavenet voices still work.
Speed defaults to 1, range 0.25–2. Google currently documents Chirp pace control
as Preview. Chirp requests omit `audioConfig.pitch`; callers must omit `pitch`
or send zero, otherwise they receive 400. Legacy voices retain -20–20 pitch.
Only MP3 output and plain text are exposed; no SSML or markup is introduced.

Text limit: 100000 UTF-8 bytes, JSON body limit: 1 MiB. Longer input is rejected,
never truncated. The complete chapter text must be supplied by the caller.
The service does not retrieve Scripture or decide which translation to read.

## Segmentation and continuous playback

Texts split at a 2200-byte target, below Google's 5000-byte ceiling, losslessly
at sentence/word boundaries (falling back to valid UTF-8 boundaries for very
long tokens). Near-limit Chirp requests exceeded the 60-second provider timeout
during local validation, which is why this isn't Google's own ceiling; 2200 was
re-validated against the real API without approaching that timeout. Whitespace-
only segments need no speech call. Segments are requested sequentially as 24 kHz
LINEAR16 WAV.

Each segment is synthesized independently, so Chirp's prosody resets at every
boundary — a hard cut between segments is audible as a mechanical restart.
FFmpeg joins them with a 60ms crossfade at each join (blending pitch/volume
across the seam) instead of a hard concat, then encodes a single 128 kbps MP3
with one duration/header. Short texts (one segment) use Google's MP3 directly,
untouched by any of this. Fewer, larger segments plus the crossfade were chosen
over switching TTS providers: the voice model (Chirp 3 HD) was already
Google's best available; the seam was the app's own assembly, not the model.

The endpoint returns only after the entire MP3 is ready; this is continuous
file playback, not streaming before generation completes. No verse timestamps
or synchronized highlighting are included. Failed/cancelled generation returns
a Problem response, never partial chapter audio.

Each Google call has a 60-second timeout. The whole operation, including waiting
for another generation, has a 10-minute timeout. Only this endpoint extends the
HTTP write deadline (10 minutes plus 30 seconds to deliver); other routes keep
the existing limits. Reverse proxies and App requests need matching timeouts.
Provider JSON is bounded to 32 MiB per segment; final MP3 to 64 MiB.

## Persistent chapter cache

### Mobile version discovery (2026-09-14)

`GET /v1/tts/config?language=pt-BR` returns `{ "version": "<sha256>" }` without
calling Google. The version includes the default voice/settings and `audioRevision` in
`internal/tts/chapter.go`; bump that constant when changing assembly or provider behavior
without changing settings. Deploy this backend endpoint to activate automatic updates.

iOS and Android cache this manifest for one hour, using the last known response offline.
Local MP3 keys now include version + language + exact text. Old unversioned files are not
relabeled as current; normal 40-chapter/150 MB eviction removes them gradually. When an older
backend lacks the endpoint, clients retain legacy behavior. Existing server MP3 cache keys
remain unchanged, so the first versioned download can reuse already-generated server audio.

The optional `revision` field in `POST /v1/tts` rejects a stale version before generation,
preventing deployment races from saving new audio under an old version. Until the manifest
refreshes, an uncached chapter in that race follows the existing English-recording fallback.
The new revision does not interrupt audio already playing. No deployment or paid synthesis
was performed as part of this change; device/network tests remain with the owner.

The key is SHA-256 of the exact supplied chapter text plus normalized language,
resolved voice, speed, pitch, format and a provider/assembly-version namespace.
Different translation text or settings produce different entries; omitted and
explicit defaults share an entry. Sending identical text/settings for two chapter
labels safely reuses identical audio: chapter IDs are not required by the existing
endpoint. Send one complete chapter per request to obtain chapter-level caching.

Only complete MP3 files are atomically published. Repeated requests survive API
restarts and container replacement when the volume is retained. Cache hits do not
contact Google. A cancellable per-service generation gate bounds resource use and
prevents simultaneous identical requests from paying twice. Different cache misses
are also serialized in this initial local backend; cache hits bypass the gate.
Coalescing is per process, not a distributed lock across multiple API replicas.

Set `VERBUM_TTS_CACHE_DIR` to a writable directory. Native default is the OS user
cache directory plus `verbum/tts`. Docker uses `/var/cache/verbum/tts`, mounted from
named volume `verbum-tts-cache` by the startup script. No automatic expiration or
eviction is configured; retain/manage the volume to retain audio. Removing entries
causes regeneration. No credentials or raw text are stored in the cache filenames.
Temporary WAVs are removed after completion or failure.

## Local configuration and testing

The Service Account remains outside the repository. In PowerShell:

```powershell
$env:GOOGLE_APPLICATION_CREDENTIALS = "$env:LOCALAPPDATA\Verbum\secrets\google-tts-service-account.json"
docker build -f backend/Dockerfile -t verbum-api:chirp .
.\backend\scripts\Start-LocalAPI.ps1 -ContainerName verbum-chirp-trial -Port 8082
.\backend\scripts\Test-TTS.ps1 -BaseURL http://localhost:8082 -Language pt-BR -OutputFile backend/tmp/chirp-trial.mp3
```

The startup script creates a new container; an existing name is preserved.
It mounts the JSON read-only and a persistent cache volume. Docker now includes
FFmpeg and still runs as nonroot (UID 65532). Native execution requires FFmpeg
(with libmp3lame) on PATH and a writable cache directory. Missing credentials,
FFmpeg or inaccessible cache disable TTS only; inspect the safe startup log.

```powershell
curl.exe --fail --max-time 630 --request POST http://localhost:8080/v1/tts --header "Content-Type: application/json" --data-binary "@api/examples/tts/request-pt-BR.json" --output leitura.mp3
```

Repeat the request with another output filename: it reuses the cached bytes.
The API keeps `Cache-Control: no-store` for HTTP intermediaries; that does not
disable the private server-side audio cache.

Errors preserve `Problem {code,message}`: 400 malformed input/unsupported controls,
415 non-JSON, 429 provider quota, 502 invalid audio/assembly failure, 503 unavailable
provider/credentials/cache, 504 timeout, 408 cancellation. No automatic paid retries.
Google error bodies, tokens, credentials and submitted text are never logged.

## Files and verification

- `internal/tts/tts.go`: Chirp defaults, compatible controls, ADC/configuration.
- `internal/tts/chapter.go`: segmentation, continuous MP3 and persistent cache.
- `internal/tts/{tts,chapter}_test.go`: provider, Unicode, identity, concurrency,
  persistence, failure and FFmpeg duration/decode tests.
- `internal/httpapi/{tts,server}.go`: larger body and scoped deadline through logging.
- `cmd/api/main.go`, `Dockerfile`, `scripts/*.ps1`: runtime/configuration.
- `../api/openapi.yaml`, `../api/examples/tts/request-pt-BR.json`: preserved contract
  with expanded limits and updated defaults; README/backend map link here.

Run `go test ./...` with the existing PostgreSQL test URL, and `go vet ./...`.
The FFmpeg integration test runs when FFmpeg is on PATH. To run it inside the API
image, compile a Linux test binary with `CGO_ENABLED=0 go test -c -o tmp/tts.test
./internal/tts`, mount it read-only at `/tts.test`, and run with entrypoint
`/tts.test -test.run TestChapterContinuousMP3 -test.v`.

## App handoff

The backend now accepts a complete chapter and returns one cached, playable MP3.
Use the existing audio client/player, send exact translation text, omit the voice
to use the new Portuguese default, and remove the pitch control for Chirp voices.
Allow loading/cancellation while the first generation finishes, save the response
bytes, then play the single file normally. No client segmentation/concatenation
and no Google credentials are necessary. Existing clients that explicitly send
Standard-A must stop specifying it or choose the Chirp voice to hear the new default.

References: [Chirp 3 HD voices and pace controls](https://docs.cloud.google.com/text-to-speech/docs/chirp3-hd),
[Google input limits](https://docs.cloud.google.com/text-to-speech/quotas),
[FFmpeg concat demuxer](https://ffmpeg.org/ffmpeg-formats.html#concat).
