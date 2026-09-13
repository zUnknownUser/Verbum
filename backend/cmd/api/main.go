// The Verbum API. Wires config → store → HTTP and runs until SIGTERM.
//
//	go run ./cmd/api                       # serves the fixtures on :8080
//	VERBUM_ADDR=:9000 go run ./cmd/api     # another port
//
// VERBUM_DATABASE_URL selects the Postgres store;
// with no database configured the fixture file is served, which is also
// what the tests use.
package main

import (
	"context"
	"errors"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"verbum/backend/internal/ask"
	"verbum/backend/internal/embeddings"
	"verbum/backend/internal/httpapi"
	"verbum/backend/internal/realtime"
	"verbum/backend/internal/store"
	"verbum/backend/internal/store/memory"
	"verbum/backend/internal/store/postgres"
	"verbum/backend/internal/synthesis"
	"verbum/backend/internal/tts"
)

func main() {
	// Structured logs (§54): JSON by default so a log aggregator can parse fields like reqID,
	// ms and hits without scraping text. VERBUM_LOG_FORMAT=text keeps the human-readable
	// handler for a local terminal — either way every log site already passes structured
	// key-value pairs, only the encoding changes here.
	if env("VERBUM_LOG_FORMAT", "json") != "text" {
		slog.SetDefault(slog.New(slog.NewJSONHandler(os.Stdout, nil)))
	}

	addr := env("VERBUM_ADDR", ":8080")
	fixtures := env("VERBUM_FIXTURES", "db/seed/fixtures.json")

	var s store.Store
	if url := os.Getenv("VERBUM_DATABASE_URL"); url != "" {
		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		db, err := postgres.Open(ctx, url)
		cancel()
		if err != nil {
			slog.Error("open database", "err", err)
			os.Exit(1)
		}
		defer db.Close()
		s = db
		slog.Info("serving Postgres content")
	} else {
		var err error
		s, err = memory.Load(fixtures)
		if err != nil {
			slog.Error("load fixtures", "err", err)
			os.Exit(1)
		}
		slog.Info("serving fixtures", "file", fixtures)
	}

	// OPENAI_API_KEY is optional: without it, /v1/realtime/session and /v1/ask both answer 503
	// and search stays lexical/entity-only, instead of the server failing to start. See
	// backend/README.md for how a local run sets this from the DPAPI secret without ever
	// writing the plaintext key to disk. Unlike the pipeline's transient per-command use, this
	// process holds it in memory for as long as it runs.
	//
	// httpapi.New's realtime/embedder/asker parameters must each receive a literal nil (not a
	// nil *realtime.Broker/*embeddings.Client/*ask.Service variable) in the disabled case:
	// handing a typed nil pointer through an interface parameter would make handlers.go's
	// `== nil` checks false, and the first request would panic instead of degrading gracefully.
	var speech httpapi.TextToSpeech
	if os.Getenv("GOOGLE_APPLICATION_CREDENTIALS") != "" {
		client, err := tts.New()
		if err != nil {
			slog.Error("TTS disabled: check GOOGLE_APPLICATION_CREDENTIALS, writable cache directory and ffmpeg")
		} else {
			speech = client
			slog.Info("Google Cloud TTS enabled")
		}
	}
	var handler http.Handler
	if key := os.Getenv("OPENAI_API_KEY"); key != "" {
		slog.Info("realtime session broker, query embedder and ask enabled")
		embedder := embeddings.New(key)
		asker := &ask.Service{
			Store:       s,
			Embedder:    embedder,
			Synthesizer: synthesis.New(key, env("VERBUM_ASK_MODEL", synthesis.DefaultModel)),
			Translation: "WEB",
		}
		handler = httpapi.New(s, time.Now, realtime.New(key), embedder, asker, speech)
	} else {
		slog.Info("realtime session broker, query embedder and ask disabled: OPENAI_API_KEY not set")
		handler = httpapi.New(s, time.Now, nil, nil, nil, speech)
	}

	server := &http.Server{
		Addr:              addr,
		Handler:           handler,
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       10 * time.Second,
		WriteTimeout:      15 * time.Second,
		IdleTimeout:       60 * time.Second,
	}

	go func() {
		slog.Info("listening", "addr", addr)
		if err := server.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			slog.Error("serve", "err", err)
			os.Exit(1)
		}
	}()

	// Stop cleanly on Ctrl-C or a container stop: finish in-flight requests first.
	stop := make(chan os.Signal, 1)
	signal.Notify(stop, os.Interrupt, syscall.SIGTERM)
	<-stop
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	_ = server.Shutdown(ctx)
	slog.Info("stopped")
}

func env(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
