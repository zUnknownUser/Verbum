// Package domain holds the types that cross the wire. They are the schemas of
// api/openapi.yaml, one struct each, with the JSON names the apps decode.
// Keep them dumb: no methods that need a database, no framework imports.
package domain

import "fmt"

// EntityType mirrors BibleEntityType (§22.1). Values are the wire strings.
type EntityType string

const (
	Person           EntityType = "person"
	Place            EntityType = "place"
	Event            EntityType = "event"
	Theme            EntityType = "theme"
	Passage          EntityType = "passage"
	Book             EntityType = "book"
	Prophecy         EntityType = "prophecy"
	OriginalTerm     EntityType = "originalTerm"
	HistoricalPeriod EntityType = "historicalPeriod"
)

// Entity is a node of the graph (§22.1).
type Entity struct {
	ID      string     `json:"id"`
	Type    EntityType `json:"type"`
	Name    string     `json:"name"`
	Summary *string    `json:"summary"`
}

// Relationship is a directed, sourced edge (§22.2, §33).
type Relationship struct {
	ID                 string   `json:"id"`
	SourceID           string   `json:"sourceId"`
	TargetID           string   `json:"targetId"`
	Type               string   `json:"type"`
	Confidence         *float64 `json:"confidence"`
	SourceReferenceIDs []string `json:"sourceReferenceIds"`
}

// PassageReference is a place in Scripture, translation-independent. OSIS book
// ids; verses omitted for a whole chapter.
type PassageReference struct {
	BookID     string `json:"bookId"`
	Chapter    int    `json:"chapter"`
	VerseStart *int   `json:"verseStart,omitempty"`
	VerseEnd   *int   `json:"verseEnd,omitempty"`
}

// Key is the stable single-verse identity used to correlate a PassageReference with
// Store.PassageText's map and Store.EntitiesForPassages — every caller of either must build
// keys with this, not its own copy of the format. A chapter-only reference (nil VerseStart)
// keys as verse 0, which never collides with a real verse.
func (r PassageReference) Key() string {
	verse := 0
	if r.VerseStart != nil {
		verse = *r.VerseStart
	}
	return fmt.Sprintf("%s.%d.%d", r.BookID, r.Chapter, verse)
}

// SourceReference is provenance for a claim (§33).
type SourceReference struct {
	ID       string  `json:"id"`
	Citation string  `json:"citation"`
	URL      *string `json:"url"`
}

// EntityDetail is an entity's page (§9).
type EntityDetail struct {
	Entity           Entity             `json:"entity"`
	Aliases          []string           `json:"aliases"`
	ApproximateDates *string            `json:"approximateDates"`
	Role             *string            `json:"role"`
	ModernGeography  *string            `json:"modernGeography"`
	KeyPassages      []PassageReference `json:"keyPassages"`
	Sources          []SourceReference  `json:"sources"`
}

// GraphSnapshot is one entity's neighbourhood (§44).
type GraphSnapshot struct {
	Root  Entity         `json:"root"`
	Nodes []Entity       `json:"nodes"`
	Edges []Relationship `json:"edges"`
}

// PassageContext is chapter-level context (§10, §22.4).
type PassageContext struct {
	Reference       PassageReference   `json:"reference"`
	Entities        []Entity           `json:"entities"`
	RelatedPassages []PassageReference `json:"relatedPassages"`
	Sources         []SourceReference  `json:"sources"`
}

// TimelineEvent is a period or event with hedged dating (§22.5).
type TimelineEvent struct {
	ID                 string   `json:"id"`
	Title              string   `json:"title"`
	StartYear          *int     `json:"startYear"`
	EndYear            *int     `json:"endYear"`
	DatePrecision      string   `json:"datePrecision"`
	Summary            *string  `json:"summary"`
	EntityIDs          []string `json:"entityIds"`
	SourceReferenceIDs []string `json:"sourceReferenceIds"`
}

// Timeline is the /v1/timeline response.
type Timeline struct {
	Events      []TimelineEvent   `json:"events"`
	EntityNames map[string]string `json:"entityNames"`
}

// SearchResponse groups results (§27).
type SearchResponse struct {
	Query    string             `json:"query"`
	Passages []PassageReference `json:"passages"`
	Books    []BookHit          `json:"books"`
	Entities []Entity           `json:"entities"`
}

type BookHit struct {
	ID string `json:"id"`
}

// DailyVerse is one day's verse.
type DailyVerse struct {
	Date      string           `json:"date"`
	Reference PassageReference `json:"reference"`
}

// AskResponse is the §30 AI Response Data Contract, verbatim. EntityReferences only ever names
// entities with a curated keyPassage covering a cited passage (internal/ask) — never derived
// from the question text itself.
type AskResponse struct {
	Answer               string             `json:"answer"`
	Summary              string             `json:"summary"`
	PassageReferences    []PassageReference `json:"passageReferences"`
	EntityReferences     []string           `json:"entityReferences"`
	SourceReferences     []SourceReference  `json:"sourceReferences"`
	Confidence           string             `json:"confidence"`
	InterpretiveVariance bool               `json:"interpretiveVariance"`
}
