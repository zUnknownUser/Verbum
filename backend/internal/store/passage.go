package store

import "context"

type verseKey struct{}

// WithVerse narrows related occurrences to entities attested at a selected verse.
// Chapter context remains available; this does not assert a quotation or parallel.
func WithVerse(ctx context.Context, verse int) context.Context {
	return context.WithValue(ctx, verseKey{}, verse)
}
func Verse(ctx context.Context) int {
	verse, _ := ctx.Value(verseKey{}).(int)
	return verse
}
