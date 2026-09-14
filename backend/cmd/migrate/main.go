// migrate applies db/migrations/*.sql, in file order, to VERBUM_DATABASE_URL.
//
//	VERBUM_DATABASE_URL=postgres://... go run ./cmd/migrate
//
// Every migration is written to be idempotent (CREATE ... IF NOT EXISTS), so this is safe to
// re-run against a database that already has the schema — the same property
// internal/testdb relies on when it applies the directory twice. There is no version table
// on purpose: the files are the source of truth and the API never calls this.
package main

import (
	"context"
	"flag"
	"log/slog"
	"os"
	"path/filepath"
	"time"

	"github.com/jackc/pgx/v5"
)

func main() {
	dir := flag.String("migrations", "db/migrations", "directory of ordered *.sql files")
	flag.Parse()
	url := os.Getenv("VERBUM_DATABASE_URL")
	if url == "" {
		slog.Error("VERBUM_DATABASE_URL is required")
		os.Exit(1)
	}
	paths, err := filepath.Glob(filepath.Join(*dir, "*.sql"))
	if err != nil || len(paths) == 0 {
		slog.Error("no migrations found", "dir", *dir)
		os.Exit(1)
	}
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Minute)
	defer cancel()
	conn, err := pgx.Connect(ctx, url)
	if err != nil {
		slog.Error("connect to database", "err", err)
		os.Exit(1)
	}
	defer conn.Close(context.Background())
	for _, path := range paths {
		sql, err := os.ReadFile(path)
		if err != nil {
			slog.Error("read migration", "file", path, "err", err)
			os.Exit(1)
		}
		if _, err = conn.Exec(ctx, string(sql)); err != nil {
			slog.Error("apply migration", "file", filepath.Base(path), "err", err)
			os.Exit(1)
		}
		slog.Info("applied", "file", filepath.Base(path))
	}
}
