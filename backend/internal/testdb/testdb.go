// Package testdb provisions an isolated schema for PostgreSQL integration tests.
package testdb

import (
	"context"
	"fmt"
	"github.com/jackc/pgx/v5"
	"net/url"
	"os"
	"path/filepath"
	"testing"
	"time"
)

// Open never touches existing application tables. VERBUM_TEST_DATABASE_URL
// must point at a development database whose role can create schemas.
func Open(t *testing.T, migrations string) (*pgx.Conn, string) {
	t.Helper()
	raw := os.Getenv("VERBUM_TEST_DATABASE_URL")
	if raw == "" {
		t.Skip("set VERBUM_TEST_DATABASE_URL to run PostgreSQL integration tests")
	}
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()
	admin, err := pgx.Connect(ctx, raw)
	if err != nil {
		t.Fatal(err)
	}
	schema := fmt.Sprintf("verbum_test_%d", time.Now().UnixNano())
	quoted := pgx.Identifier{schema}.Sanitize()
	if _, err = admin.Exec(ctx, "CREATE SCHEMA "+quoted); err != nil {
		admin.Close(ctx)
		t.Fatal(err)
	}
	t.Cleanup(func() {
		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		if _, err := admin.Exec(ctx, "DROP SCHEMA "+quoted+" CASCADE"); err != nil {
			t.Errorf("clean test schema: %v", err)
		}
		admin.Close(ctx)
	})
	u, err := url.Parse(raw)
	if err != nil {
		t.Fatal(err)
	}
	q := u.Query()
	q.Set("search_path", schema)
	u.RawQuery = q.Encode()
	conn, err := pgx.Connect(ctx, u.String())
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { conn.Close(context.Background()) })
	paths, err := filepath.Glob(filepath.Join(migrations, "*.sql"))
	if err != nil {
		t.Fatal(err)
	}
	if len(paths) == 0 {
		t.Fatal("no migrations found")
	}
	// Applying twice also verifies the documented idempotence.
	for range 2 {
		for _, path := range paths {
			sql, err := os.ReadFile(path)
			if err != nil {
				t.Fatal(err)
			}
			if _, err = conn.Exec(ctx, string(sql)); err != nil {
				t.Fatalf("migrate %s: %v", path, err)
			}
		}
	}
	return conn, u.String()
}
