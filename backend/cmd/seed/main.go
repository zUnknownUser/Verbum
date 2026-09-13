// seed loads the development fixtures into an empty, migrated database.
package main

import (
	"context"
	"flag"
	"github.com/jackc/pgx/v5"
	"log/slog"
	"os"
	"time"
	"verbum/backend/internal/seed"
)

func main() {
	path := flag.String("fixtures", "db/seed/fixtures.json", "development fixture file")
	flag.Parse()
	url := os.Getenv("VERBUM_DATABASE_URL")
	if url == "" {
		slog.Error("VERBUM_DATABASE_URL is required")
		os.Exit(1)
	}
	ctx, cancel := context.WithTimeout(context.Background(), time.Minute)
	defer cancel()
	conn, err := pgx.Connect(ctx, url)
	if err != nil {
		slog.Error("connect to database", "err", err)
		os.Exit(1)
	}
	defer conn.Close(context.Background())
	if err = seed.Load(ctx, conn, *path); err != nil {
		slog.Error("seed failed", "err", err)
		os.Exit(1)
	}
	slog.Info("development fixtures loaded", "file", *path)
}
