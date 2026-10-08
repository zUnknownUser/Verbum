package store

import (
	"sort"
	"strings"
	"unicode"

	"golang.org/x/text/unicode/norm"
	"verbum/backend/internal/domain"
)

// EntityPage is an opt-in paginated catalog. The legacy Entities response stays unchanged.
type EntityPage struct {
	Entities   []domain.Entity `json:"entities"`
	Letters    []string        `json:"letters"`
	NextOffset *int            `json:"nextOffset"`
}
type EntityPageRequest struct {
	Query, Letter string
	Offset, Limit int
}

func CatalogName(value string) string {
	return strings.Map(func(r rune) rune {
		if unicode.Is(unicode.Mn, r) {
			return -1
		}
		return unicode.ToLower(r)
	}, norm.NFD.String(strings.TrimSpace(value)))
}
func CatalogLetter(name string) string {
	n := CatalogName(name)
	if len(n) > 0 && n[0] >= 'a' && n[0] <= 'z' {
		return strings.ToUpper(n[:1])
	}
	return "#"
}

// PaginateEntities is the fixture implementation; Postgres filters and limits in SQL.
func PaginateEntities(entities []domain.Entity, request EntityPageRequest) EntityPage {
	page := EntityPage{Entities: []domain.Entity{}, Letters: []string{}}
	matches := []domain.Entity{}
	letters := map[string]bool{}
	for _, e := range entities {
		if !strings.Contains(CatalogName(e.Name), CatalogName(request.Query)) {
			continue
		}
		letter := CatalogLetter(e.Name)
		letters[letter] = true
		if request.Letter == "" || letter == request.Letter {
			matches = append(matches, e)
		}
	}
	for letter := range letters {
		page.Letters = append(page.Letters, letter)
	}
	sort.Strings(page.Letters)
	sort.Slice(matches, func(i, j int) bool {
		left, right := CatalogLetter(matches[i].Name), CatalogLetter(matches[j].Name)
		if left != right {
			return left < right
		}
		a, b := CatalogName(matches[i].Name), CatalogName(matches[j].Name)
		if a == b {
			return matches[i].ID < matches[j].ID
		}
		return a < b
	})
	if request.Offset >= len(matches) {
		return page
	}
	end := min(request.Offset+request.Limit, len(matches))
	page.Entities = matches[request.Offset:end]
	if end < len(matches) {
		page.NextOffset = &end
	}
	return page
}
