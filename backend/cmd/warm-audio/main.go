// warm-audio inventories the shared narration library by default. Generation
// requires both -generate and an explicit per-run conservative spending ceiling.
package main

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"os"
	"os/signal"
	"path/filepath"
	"strings"
	"sync"
	"syscall"
	"time"

	"verbum/backend/internal/httpapi"
	"verbum/backend/internal/scripture"
	"verbum/backend/internal/tts"
	"verbum/backend/internal/usage"
)

type entry struct {
	Book          string `json:"book"`
	Chapter       int    `json:"chapter"`
	Cached        bool   `json:"cached"`
	Words         int    `json:"words"`
	ReserveMicros int64  `json:"reserveMicros"`
}
type report struct {
	Language      string  `json:"language"`
	Version       string  `json:"version"`
	Chapters      int     `json:"chapters"`
	Cached        int     `json:"cached"`
	MissingWords  int     `json:"missingWords"`
	ReserveMicros int64   `json:"reserveMicros"`
	Entries       []entry `json:"entries"`
}

func main() {
	if err := run(); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}
func run() error {
	language := flag.String("language", "pt-BR", "pt-BR or en-US")
	generate := flag.Bool("generate", false, "generate missing audio; default only inventories")
	ceiling := flag.Int64("max-reserve-micros", 0, "maximum sum of new generation reservations for this invocation; 1000000 = USD 1")
	dir := flag.String("work-dir", "", "required persistent local directory for canonical text and report")
	book := flag.String("book", "", "optional canonical book ID")
	flag.Parse()
	if *dir == "" || (*generate && *ceiling <= 0) {
		return errors.New("provide -work-dir; generation also requires positive -max-reserve-micros")
	}
	version, err := tts.AudioVersion(*language)
	if err != nil {
		return err
	}
	ctx, cancel := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer cancel()
	db, err := usage.Open(ctx, os.Getenv("VERBUM_DATABASE_URL"))
	if err != nil {
		return errors.New("cannot connect to usage database")
	}
	defer db.Close()
	policy, err := usage.FromEnvironment()
	if err != nil {
		return err
	}
	if err = os.MkdirAll(*dir, 0700); err != nil {
		return err
	}
	resolver := scripture.New(db)
	chapters := scripture.Chapters()
	if *book != "" {
		filtered := chapters[:0]
		for _, ch := range chapters {
			if ch.BookID == *book {
				filtered = append(filtered, ch)
			}
		}
		chapters = filtered
		if len(chapters) == 0 {
			return errors.New("unknown book")
		}
	}
	requests := make([]tts.Request, len(chapters))
	// Bounded concurrent text fetches; paid synthesis below stays sequential.
	jobs := make(chan int)
	errs := make(chan error, len(chapters))
	var workers sync.WaitGroup
	for w := 0; w < 6; w++ {
		workers.Add(1)
		go func() {
			defer workers.Done()
			for i := range jobs {
				ch := chapters[i]
				path := filepath.Join(*dir, fmt.Sprintf("%s-%s-%d.json", *language, ch.BookID, ch.Number))
				var in tts.Request
				raw, e := os.ReadFile(path)
				if e == nil {
					e = json.Unmarshal(raw, &in)
					if in.BookID != ch.BookID || in.Chapter != ch.Number || in.Language != *language {
						e = errors.New("invalid local chapter")
					}
				}
				if e != nil {
					in, e = resolver.Load(ctx, *language, ch)
					if e == nil {
						raw, _ = json.Marshal(in)
						e = os.WriteFile(path, raw, 0600)
					}
				}
				if e != nil {
					errs <- fmt.Errorf("load %s %d: %w", ch.BookID, ch.Number, e)
					continue
				}
				requests[i] = in
			}
		}()
	}
	for i := range chapters {
		jobs <- i
	}
	close(jobs)
	workers.Wait()
	close(errs)
	for e := range errs {
		if e != nil {
			return e
		}
	}
	var provider *tts.TextToSpeechService
	if *generate {
		cleanup, e := credentials()
		if e != nil {
			return e
		}
		defer cleanup()
		provider, err = tts.New()
		if err != nil {
			return err
		}
	}
	keys := make([]string, 0, len(requests)*2)
	for _, in := range requests {
		prepared, e := tts.Prepare(in)
		if e != nil {
			return e
		}
		keys = append(keys, cacheKeys(prepared)...)
	}
	rows, e := db.Pool.Query(ctx, `SELECT key FROM usage_cache WHERE key=ANY($1::text[]) AND expires_at>now()`, keys)
	if e != nil {
		return e
	}
	present := map[string]bool{}
	for rows.Next() {
		var key string
		if e = rows.Scan(&key); e != nil {
			rows.Close()
			return e
		}
		present[key] = true
	}
	rows.Close()
	if e = rows.Err(); e != nil {
		return e
	}
	inventory := inventoryIndex{Store: db, present: present}
	summary := report{Language: *language, Version: version, Chapters: len(chapters)}
	for _, in := range requests {
		prepared, e := tts.Prepare(in)
		if e != nil {
			return e
		}
		found, e := cached(ctx, inventory, provider, prepared)
		if e != nil {
			return e
		}
		row := entry{Book: in.BookID, Chapter: in.Chapter, Cached: found, Words: len(strings.Fields(in.Text))}
		if found {
			summary.Cached++
		} else {
			row.ReserveMicros = tts.EstimateCost(prepared, true)
			summary.ReserveMicros += row.ReserveMicros
			summary.MissingWords += row.Words
		}
		summary.Entries = append(summary.Entries, row)
	}
	raw, _ := json.MarshalIndent(summary, "", "  ")
	if err = os.WriteFile(filepath.Join(*dir, "inventory.json"), raw, 0600); err != nil {
		return err
	}
	fmt.Printf("chapters=%d cached=%d missing=%d missing_words=%d conservative_reservation_usd=%.2f daily_budget_usd=%.2f\n", summary.Chapters, summary.Cached, summary.Chapters-summary.Cached, summary.MissingWords, float64(summary.ReserveMicros)/1e6, float64(policy.GlobalDailyMicros)/1e6)
	if !*generate {
		return nil
	}
	speech := (httpapi.EconomicOptions{Usage: usage.New(db, policy), SpeechVerifier: resolver.Verify}).WrapSpeech(provider).(interface {
		SynthesizeTimed(context.Context, tts.Request) (tts.TimedAudio, error)
	})
	ctx = usage.WithPrincipal(ctx, usage.Principal{UID: "library-warmer", Device: "library-warmer", IP: "library-warmer"})
	var reserved int64
	for i, in := range requests {
		prepared, e := tts.Prepare(in)
		if e != nil {
			return e
		}
		found, e := cached(ctx, db, provider, prepared)
		if e != nil {
			return e
		}
		if found {
			continue
		}
		cost := tts.EstimateCost(prepared, true)
		if cost > *ceiling-reserved {
			return errors.New("per-run reservation ceiling reached; rerun to resume")
		}
		in.Revision = version
		reserved += cost
		chapterCtx, chapterCancel := context.WithTimeout(ctx, tts.GenerationTimeout)
		_, e = speech.SynthesizeTimed(chapterCtx, in)
		chapterCancel()
		if e != nil {
			return fmt.Errorf("stopped at %s %d (no automatic paid retry): %w", in.BookID, in.Chapter, e)
		}
		fmt.Printf("ready %d/%d %s %d\n", i+1, len(requests), in.BookID, in.Chapter)
	}
	return nil
}
func cached(ctx context.Context, db usage.Store, provider *tts.TextToSpeechService, in tts.Request) (bool, error) {
	if provider != nil {
		if _, ok := provider.Cached(in, true); ok {
			return true, nil
		}
	}
	keys := cacheKeys(in)
	for _, key := range keys {
		raw, e := db.Cached(ctx, key, time.Now())
		if e != nil {
			return false, e
		}
		if raw != nil {
			var v tts.TimedAudio
			if json.Unmarshal(raw, &v) != nil || len(v.Audio) == 0 {
				return false, errors.New("invalid shared audio artifact")
			}
			return true, nil
		}
	}
	return false, nil
}
func credentials() (func(), error) {
	if os.Getenv("GOOGLE_APPLICATION_CREDENTIALS") != "" {
		return func() {}, nil
	}
	raw := strings.TrimSpace(os.Getenv("GOOGLE_APPLICATION_CREDENTIALS_JSON"))
	data := []byte(raw)
	if !strings.HasPrefix(raw, "{") {
		var err error
		data, err = base64.StdEncoding.DecodeString(raw)
		if err != nil {
			return nil, errors.New("invalid Google credentials encoding")
		}
	}
	f, err := os.CreateTemp("", "verbum-warmer-credentials-*.json")
	if err != nil {
		return nil, err
	}
	cleanup := func() { os.Remove(f.Name()) }
	if _, err = f.Write(data); err != nil {
		f.Close()
		cleanup()
		return nil, err
	}
	if err = f.Close(); err != nil {
		cleanup()
		return nil, err
	}
	if err = os.Setenv("GOOGLE_APPLICATION_CREDENTIALS", f.Name()); err != nil {
		cleanup()
		return nil, err
	}
	return cleanup, nil
}

type inventoryIndex struct {
	usage.Store
	present map[string]bool
}

func (s inventoryIndex) Cached(ctx context.Context, key string, now time.Time) ([]byte, error) {
	if !s.present[key] {
		return nil, nil
	}
	return s.Store.Cached(ctx, key, now)
}
func cacheKeys(in tts.Request) []string {
	keys := []string{usage.Hash("cost-v1", "tts", httpapi.SpeechCacheKey(in, true))}
	if in.IsGemini() {
		keys = append(keys, usage.Hash("cost-v1", "tts", usage.Hash(tts.LegacyEconomicIdentity(in), "true")))
	}
	return keys
}
