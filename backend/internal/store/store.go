// Package store is the one door to content. Handlers talk to this interface;
// where the content lives (a JSON fixture today, Postgres next) is a detail
// behind it. Every method is read-only: content only enters through the
// editorial pipeline (§32), never through the API.
package store

import (
	"context"
	"errors"

	"verbum/backend/internal/domain"
)

// ErrNotFound means "no such id" or "no content for this yet". Handlers turn it
// into a 404 Problem (§52); nothing else should leak out as a status code.
var ErrNotFound = errors.New("not found")

type Store interface {
	// Entity returns one node, or ErrNotFound.
	Entity(ctx context.Context, id string) (domain.Entity, error)
	// Detail returns the page for an entity. Entities with no curated detail
	// still get a page: the entity plus the editorial source.
	Detail(ctx context.Context, id string) (domain.EntityDetail, error)
	// Entities lists every node of one kind, sorted by name. Passages are never listed.
	Entities(ctx context.Context, kind domain.EntityType) ([]domain.Entity, error)
	// Neighbors is the undirected one-hop neighbourhood, at most limit nodes,
	// ordered by edge confidence (desc) then name, with every edge among root+nodes.
	Neighbors(ctx context.Context, id string, limit int) (domain.GraphSnapshot, error)
	// Context is the chapter-level context, or ErrNotFound when nothing is
	// known about the chapter (§3.5: never invent).
	Context(ctx context.Context, bookID string, chapter int) (domain.PassageContext, error)
	// Timeline lists every event chronologically; entityID filters to one entity's.
	Timeline(ctx context.Context, entityID string) (domain.Timeline, error)
	// Search returns the entity group for a query (references and books are
	// also matched on device; the server may add them later).
	Search(ctx context.Context, query string) ([]domain.Entity, error)
	// SearchPassages returns Scripture passage hits for free-text and/or semantic search
	// (§27-28), most relevant first, capped at limit. queryEmbedding is nil when no embedder
	// is configured server-side; lexical-only results are still meaningful. Ranking does not
	// yet favor a query that is itself a direct Bible reference — the apps already parse and
	// serve that case (§28).
	SearchPassages(ctx context.Context, queryText string, queryEmbedding []float32, limit int) ([]domain.PassageReference, error)
	// PassageText returns verse text for the given references, keyed "bookId.chapter.verse"
	// (refs without stored text are simply absent from the map, never a placeholder). Used only
	// by Ask (§29) to ground synthesis in real words — never exposed through /v1/search, which
	// apps still read from bible.helloao.org (§3.5, backend/README.md).
	PassageText(ctx context.Context, translation string, refs []domain.PassageReference) (map[string]string, error)
	// EntitiesForPassages returns IDs whose curated key passages or sourced occurrences cover
	// the given verses — used only by Ask (§29) to link cited Scripture back to the entity
	// graph. Order is unspecified; deduplicated.
	EntitiesForPassages(ctx context.Context, refs []domain.PassageReference) ([]string, error)
	// DailyVersePool is the curated list the daily pick draws from.
	DailyVersePool(ctx context.Context) ([]domain.PassageReference, error)
}
