// The Verbum API. Wires config → store → HTTP and runs until SIGTERM.
//
//	go run ./cmd/api                       # serves the fixtures on :8080
//	VERBUM_ADDR=:9000 go run ./cmd/api     # another port
//
// When the Postgres store exists, VERBUM_DATABASE_URL will select it here;
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

	"verbum/backend/internal/httpapi"
	"verbum/backend/internal/store"
	"verbum/backend/internal/store/memory"
)

func main() {
	addr := env("VERBUM_ADDR", ":8080")
	fixtures := env("VERBUM_FIXTURES", "db/seed/fixtures.json")

	var s store.Store
	if url := os.Getenv("VERBUM_DATABASE_URL"); url != "" {
		// TODO(Task 11a): s, err = postgres.Open(ctx, url)
		slog.Error("VERBUM_DATABASE_URL is set but the Postgres store is not written yet; see README")
		os.Exit(1)
	} else {
		var err error
		s, err = memory.Load(fixtures)
		if err != nil {
			slog.Error("load fixtures", "err", err)
			os.Exit(1)
		}
		slog.Info("serving fixtures", "file", fixtures)
	}

	server := &http.Server{
		Addr:              addr,
		Handler:           httpapi.New(s, time.Now),
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
