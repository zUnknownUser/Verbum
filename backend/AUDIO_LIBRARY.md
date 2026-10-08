# Shared narration inventory and batch generation

`cmd/warm-audio` prepares the same synchronized chapter artifacts used by iOS and
Android. It does not play audio. Portuguese is the default; English can be selected
explicitly with `-language en-US`. Existing English recordings in the apps do not
need TTS pre-generation.

## Inventory (no synthesis or budget reservation)

```sh
go run ./cmd/warm-audio -work-dir /persistent/audio-inventory
```

Requires `VERBUM_DATABASE_URL`. Uses the active `VERBUM_TTS_NARRATOR`. Fetches the
1,189 canonical chapter texts with six workers and keeps resumable local copies.
Writes `inventory.json`, including cache coverage, missing word count and the
conservative reservation sum used by the existing cost-control service.

Inventory checks current and compatible legacy shared database artifacts. A
database-only inventory cannot detect disk-only artifacts on another machine, so
its missing count is an upper bound in that case. Generation checks local disk
again as well as the database before each chapter. Never delete existing caches
to run this job.

The reservation sum is **not a price quote**: Gemini reserves the maximum output
allowance per segment, then settles using measured audio duration. At the price
checked October 8, 2026, Gemini 2.5 Pro TTS charges USD 1/million input tokens and
USD 20/million audio tokens, with 25 audio tokens/second (USD 1.80/hour of output,
plus input). Storage, database growth, backups and network traffic are separate.
Source: https://cloud.google.com/text-to-speech/pricing

## Generate missing chapters, with an explicit spending ceiling

```sh
/app/warm-audio -work-dir /var/cache/verbum/tts/inventory \
  -generate -max-reserve-micros 5000000
```

The example bounds the invocation to USD 5 in conservative reservations. It also
honors the existing shared daily budget, canonical text verification, permanent
cache identity, cross-replica generation lock and crash/timeout protection. It
does not raise quotas or change narrator settings. No public administrative route
is added. Runtime requires the normal Google credentials and FFmpeg.

Run inside the API environment to share its persistent disk and database. The
binary is included in the backend image. Local execution against production also
stores audio in the shared database, but its local disk is not the server volume.

Generation is sequential and stops on a provider error, quota, interruption or
spending ceiling. It never automatically retries a possibly billable failure.
Rerunning discovers completed artifacts and resumes; rerun inventory afterwards
to refresh the report. `-book John` limits either mode to a single canonical book.
Do not run concurrent warmers. Plan volume/database capacity before generating
the full library: at 128 kbps, each hour is approximately 57.6 MB of MP3, with
additional JSON/base64 and database storage overhead.

Users do not spend a personal generation quota for this standard narration.
The first synthesis is still billed to the application's provider account.
