// Trusted operator CLI. Mobile cannot write entitlements. A future billing adapter must
// verify store purchases server-side before granting/revoking the same entitlement.
package main

import (
	"context"
	"flag"
	"fmt"
	"os"
	"time"
	"verbum/backend/internal/usage"
)

func main() {
	uid := flag.String("uid", "", "Firebase UID")
	plan := flag.String("plan", "", "free or premium")
	expires := flag.String("expires", "", "RFC3339 expiration")
	source := flag.String("source", "", "auditable grant/revoke reason")
	flag.Parse()
	until, e := time.Parse(time.RFC3339, *expires)
	if *uid == "" || len(*uid) > 128 || (*plan != "free" && *plan != "premium") || *source == "" || len(*source) > 200 || e != nil || until.Before(time.Now()) {
		fmt.Fprintln(os.Stderr, "provide uid, plan, future RFC3339 expires and source")
		os.Exit(2)
	}
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	db, e := usage.Open(ctx, os.Getenv("VERBUM_DATABASE_URL"))
	if e != nil {
		fmt.Fprintln(os.Stderr, "database unavailable")
		os.Exit(1)
	}
	defer db.Close()
	_, e = db.Pool.Exec(ctx, `INSERT INTO usage_entitlements(uid,plan,expires_at,source) VALUES($1,$2,$3,$4) ON CONFLICT(uid) DO UPDATE SET plan=EXCLUDED.plan,expires_at=EXCLUDED.expires_at,source=EXCLUDED.source,updated_at=now()`, *uid, *plan, until, *source)
	if e != nil {
		fmt.Fprintln(os.Stderr, "entitlement update failed")
		os.Exit(1)
	}
	fmt.Println("Entitlement updated.")
}
