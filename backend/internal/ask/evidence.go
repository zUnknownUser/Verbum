package ask

import "verbum/backend/internal/domain"

const (
	// Version is included in the private answer cache key, so old refusals do not survive
	// a retrieval/prompt release. This is independent of the global TTS cache.
	Version           = "rag-context-2"
	maxEvidenceVerses = 48
	maxEvidenceBytes  = 20_000
	contextRadius     = 4
)

// contextReferences retains seed order (selected verses come first) and expands nearby
// verses in rounds. One passage cannot consume the budget before the others get context.
// Only real rows returned by PassageText can subsequently enter the prompt or citations.
func contextReferences(seeds []domain.PassageReference) []domain.PassageReference {
	refs := make([]domain.PassageReference, 0, maxEvidenceVerses)
	seen := map[string]bool{}
	add := func(ref domain.PassageReference) {
		if ref.VerseStart == nil || *ref.VerseStart < 1 || len(refs) >= maxEvidenceVerses || seen[ref.Key()] {
			return
		}
		seen[ref.Key()] = true
		refs = append(refs, ref)
	}
	for _, ref := range seeds {
		add(ref)
	}
	for distance := 1; distance <= contextRadius; distance++ {
		for _, ref := range seeds {
			if ref.VerseStart == nil {
				continue
			}
			for _, delta := range []int{-distance, distance} {
				verse := *ref.VerseStart + delta
				add(domain.PassageReference{BookID: ref.BookID, Chapter: ref.Chapter, VerseStart: &verse, VerseEnd: &verse})
			}
		}
	}
	return refs
}

func boundedEvidence(refs []domain.PassageReference, texts map[string]string) []evidence {
	items := make([]evidence, 0, len(refs))
	size := 0
	for _, ref := range refs {
		text := texts[ref.Key()]
		if text == "" || size+len(text) > maxEvidenceBytes {
			continue
		}
		items = append(items, evidence{ref: ref, text: text})
		size += len(text)
		if len(items) == maxEvidenceVerses {
			break
		}
	}
	return items
}

// Nearby hits often describe the same scene. Keep room for another relevant scene
// rather than spending all six seeds on neighboring verses from one chapter.
func diverseSeeds(candidates []domain.PassageReference, limit int) []domain.PassageReference {
	seeds := make([]domain.PassageReference, 0, limit)
	for _, candidate := range candidates {
		duplicate := false
		for _, seed := range seeds {
			if seed.BookID != candidate.BookID || seed.Chapter != candidate.Chapter || seed.VerseStart == nil || candidate.VerseStart == nil {
				continue
			}
			distance := *seed.VerseStart - *candidate.VerseStart
			if distance >= -contextRadius && distance <= contextRadius {
				duplicate = true
				break
			}
		}
		if !duplicate {
			seeds = append(seeds, candidate)
		}
		if len(seeds) == limit {
			break
		}
	}
	return seeds
}
