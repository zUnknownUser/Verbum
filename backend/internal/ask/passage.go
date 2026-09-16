package ask

import (
	"context"
	"verbum/backend/internal/domain"
)

type passageKey struct{}

// WithPassage anchors investigation in an explicitly selected, bounded reference.
// The service still requires real corpus text before any synthesis.
func WithPassage(ctx context.Context, ref domain.PassageReference) context.Context {
	return context.WithValue(ctx, passageKey{}, ref)
}
func selectedPassages(ctx context.Context) []domain.PassageReference {
	ref, ok := ctx.Value(passageKey{}).(domain.PassageReference)
	if !ok || ref.VerseStart == nil {
		return nil
	}
	end := *ref.VerseStart
	if ref.VerseEnd != nil {
		end = *ref.VerseEnd
	}
	if end < *ref.VerseStart || end-*ref.VerseStart > 5 {
		return nil
	}
	var refs []domain.PassageReference
	for v := *ref.VerseStart; v <= end; v++ {
		verse := v
		refs = append(refs, domain.PassageReference{BookID: ref.BookID, Chapter: ref.Chapter, VerseStart: &verse, VerseEnd: &verse})
	}
	return refs
}
