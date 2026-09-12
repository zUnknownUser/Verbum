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
	// DailyVersePool is the curated list the daily pick draws from.
	DailyVersePool(ctx context.Context) ([]domain.PassageReference, error)
}
