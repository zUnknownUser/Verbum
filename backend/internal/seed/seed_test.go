package seed_test

import (
	"context"
	"encoding/json"
	"os"
	"path/filepath"
	"testing"
	"verbum/backend/internal/seed"
	"verbum/backend/internal/testdb"
)

func TestAtomicSeedAndExistingContent(t *testing.T) {
	conn, _ := testdb.Open(t, "../../db/migrations")
	ctx := context.Background()
	path := "../../db/seed/fixtures.json"
	raw, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	var f seed.Fixtures
	if err = json.Unmarshal(raw, &f); err != nil {
		t.Fatal(err)
	}
	// Fail late in the import, after sources, entities and details were queued.
	f.Timeline[0].EntityIDs = append(f.Timeline[0].EntityIDs, "missing.entity")
	invalid, err := json.Marshal(f)
	if err != nil {
		t.Fatal(err)
	}
	broken := filepath.Join(t.TempDir(), "broken.json")
	if err = os.WriteFile(broken, invalid, 0600); err != nil {
		t.Fatal(err)
	}
	if err = seed.Load(ctx, conn, broken); err == nil {
		t.Fatal("accepted a broken foreign key")
	}
	var count int
	if err = conn.QueryRow(ctx, `SELECT count(*) FROM sources`).Scan(&count); err != nil || count != 0 {
		t.Fatalf("partial seed survived: %d %v", count, err)
	}
	if err = seed.Load(ctx, conn, path); err != nil {
		t.Fatal(err)
	}
	if _, err = conn.Exec(ctx, `UPDATE entities SET name='Reviewed David' WHERE id='fixture.person.david'`); err != nil {
		t.Fatal(err)
	}
	if err = seed.Load(ctx, conn, path); err == nil {
		t.Fatal("seed accepted an occupied database")
	}
	var name string
	if err = conn.QueryRow(ctx, `SELECT name FROM entities WHERE id='fixture.person.david'`).Scan(&name); err != nil || name != "Reviewed David" {
		t.Fatalf("existing content changed: %s %v", name, err)
	}
}
