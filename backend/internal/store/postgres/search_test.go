package postgres_test

import (
	"context"
	"fmt"
	"reflect"
	"strconv"
	"strings"
	"testing"

	"github.com/jackc/pgx/v5"
	"verbum/backend/internal/domain"
	"verbum/backend/internal/embeddings"
	"verbum/backend/internal/store/postgres"
	"verbum/backend/internal/testdb"
)

func oneHot(index int) []float32 {
	v := make([]float32, embeddings.Dimensions)
	v[index] = 1
	return v
}

func vectorLiteral(v []float32) string {
	parts := make([]string, len(v))
	for i, f := range v {
		parts[i] = strconv.FormatFloat(float64(f), 'g', -1, 32)
	}
	return "[" + strings.Join(parts, ",") + "]"
}

func insertVerse(t *testing.T, ctx context.Context, conn *pgx.Conn, bookID string, chapter, verse int, text string, embedding []float32) {
	t.Helper()
	_, err := conn.Exec(ctx,
		`INSERT INTO scripture_verses (translation,book_id,chapter,verse,text,embedding) `+
			`VALUES ('WEB',$1,$2,$3,$4,$5::public.vector)`,
		bookID, chapter, verse, text, vectorLiteral(embedding),
	)
	if err != nil {
		t.Fatal(err)
	}
}

// verseA is the only one mentioning "Philistine" (lexical anchor) and carries a distinct
// one-hot embedding (semantic anchor); verseB/verseC are unrelated on both axes.
func seedThreeVerses(t *testing.T, ctx context.Context, conn *pgx.Conn) {
	t.Helper()
	insertVerse(t, ctx, conn, "1Sam", 17, 49, "David struck the Philistine with a stone.", oneHot(0))
	insertVerse(t, ctx, conn, "Gen", 1, 1, "In the beginning God created the heavens and the earth.", oneHot(1))
	insertVerse(t, ctx, conn, "John", 3, 16, "For God so loved the world.", oneHot(2))
}

func passage(bookID string, chapter, verse int) domain.PassageReference {
	v := verse
	return domain.PassageReference{BookID: bookID, Chapter: chapter, VerseStart: &v, VerseEnd: &v}
}

// derefAll is for failure messages only: %+v on a PassageReference prints pointer addresses,
// not the verse numbers, which makes a failing assertion unreadable.
func derefAll(refs []domain.PassageReference) []string {
	out := make([]string, len(refs))
	for i, r := range refs {
		out[i] = fmt.Sprintf("%s.%d.%v-%v", r.BookID, r.Chapter, deref(r.VerseStart), deref(r.VerseEnd))
	}
	return out
}

func deref(p *int) any {
	if p == nil {
		return nil
	}
	return *p
}

func TestSearchPassagesLexicalOnly(t *testing.T) {
	ctx := context.Background()
	conn, url := testdb.Open(t, "../../../db/migrations")
	seedThreeVerses(t, ctx, conn)
	db, err := postgres.Open(ctx, url)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(db.Close)

	got, err := db.SearchPassages(ctx, "Philistine", nil, 5)
	if err != nil {
		t.Fatal(err)
	}
	want := []domain.PassageReference{passage("1Sam", 17, 49)}
	if !reflect.DeepEqual(got, want) {
		t.Errorf("got %+v, want %+v", derefAll(got), derefAll(want))
	}
}

func TestSearchPassagesSemanticRanksMatchingEmbeddingFirst(t *testing.T) {
	ctx := context.Background()
	conn, url := testdb.Open(t, "../../../db/migrations")
	seedThreeVerses(t, ctx, conn)
	db, err := postgres.Open(ctx, url)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(db.Close)

	// A query embedding orthogonal to B/C's but identical to A's: cosine similarity 1 vs 0.
	got, err := db.SearchPassages(ctx, "", oneHot(0), 5)
	if err != nil {
		t.Fatal(err)
	}
	if len(got) == 0 || !reflect.DeepEqual(got[0], passage("1Sam", 17, 49)) {
		t.Fatalf("top hit = %+v, want 1Sam.17.49 ranked first", derefAll(got))
	}
}

func TestSearchPassagesNoMatchIsEmpty(t *testing.T) {
	ctx := context.Background()
	conn, url := testdb.Open(t, "../../../db/migrations")
	seedThreeVerses(t, ctx, conn)
	db, err := postgres.Open(ctx, url)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(db.Close)

	got, err := db.SearchPassages(ctx, "xyznonexistentword", nil, 5)
	if err != nil {
		t.Fatal(err)
	}
	if len(got) != 0 {
		t.Errorf("got %+v, want no hits (§3.5: never fabricate a match)", got)
	}
}

func TestSearchPassagesBlankQueryIsEmpty(t *testing.T) {
	ctx := context.Background()
	_, url := testdb.Open(t, "../../../db/migrations")
	db, err := postgres.Open(ctx, url)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(db.Close)

	got, err := db.SearchPassages(ctx, "   ", nil, 5)
	if err != nil {
		t.Fatal(err)
	}
	if len(got) != 0 {
		t.Errorf("got %+v, want no hits for a blank query with no embedding", got)
	}
}

func TestPassageTextReturnsOnlyStoredVerses(t *testing.T) {
	ctx := context.Background()
	conn, url := testdb.Open(t, "../../../db/migrations")
	seedThreeVerses(t, ctx, conn)
	db, err := postgres.Open(ctx, url)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(db.Close)

	got, err := db.PassageText(ctx, "WEB", []domain.PassageReference{
		passage("1Sam", 17, 49),
		passage("Gen", 1, 1),
		passage("Gen", 99, 99), // not seeded — must be silently absent, never a placeholder
	})
	if err != nil {
		t.Fatal(err)
	}
	want := map[string]string{
		"1Sam.17.49": "David struck the Philistine with a stone.",
		"Gen.1.1":    "In the beginning God created the heavens and the earth.",
	}
	if !reflect.DeepEqual(got, want) {
		t.Errorf("got %+v, want %+v", got, want)
	}
}

func TestPassageTextEmptyRefsIsEmptyMap(t *testing.T) {
	ctx := context.Background()
	_, url := testdb.Open(t, "../../../db/migrations")
	db, err := postgres.Open(ctx, url)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(db.Close)

	got, err := db.PassageText(ctx, "WEB", nil)
	if err != nil {
		t.Fatal(err)
	}
	if len(got) != 0 {
		t.Errorf("got %+v, want empty", got)
	}
}
