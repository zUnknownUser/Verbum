// Package personalsync stores private, offline-first reading data independently of AI.
package personalsync

import (
	"encoding/json"
	"errors"
	"fmt"
	"reflect"
	"regexp"
	"strconv"
	"strings"
	"time"
	"verbum/backend/internal/scripture"
)

var ErrInvalid = errors.New("invalid personal data")
var ErrDeleted = errors.New("account data deleted")
var ErrCapacity = errors.New("personal storage limit reached")

const PageSize = 200
const MaxChanges = 100

type Value struct {
	Book       string `json:"book,omitempty"`
	Chapter    int    `json:"chapter,omitempty"`
	Verse      int    `json:"verse,omitempty"`
	Highlight  string `json:"highlight,omitempty"`
	Style      string `json:"style,omitempty"`
	Note       string `json:"note,omitempty"`
	Bookmarked bool   `json:"bookmarked,omitempty"`
	Time       int64  `json:"time,omitempty"`
	Day        string `json:"day,omitempty"`
}
type Record struct {
	ID       string `json:"id"`
	Revision int64  `json:"revision"`
	Value    *Value `json:"value"`
}
type Change struct {
	BaseRevision int64  `json:"baseRevision"`
	ID           string `json:"id"`
	MutationID   string `json:"mutationId"`
	Base         *Value `json:"base"`
	Value        *Value `json:"value"`
}
type Request struct {
	Cursor  int64    `json:"cursor"`
	Changes []Change `json:"changes"`
}
type Response struct {
	Cursor   int64    `json:"cursor"`
	More     bool     `json:"more"`
	Records  []Record `json:"records"`
	Accepted []Record `json:"accepted"`
}

var mutationPattern = regexp.MustCompile(`^[a-zA-Z0-9-]{16,80}$`)

func Validate(r Request, now time.Time) error {
	if r.Cursor < 0 || len(r.Changes) > MaxChanges {
		return ErrInvalid
	}
	seen := map[string]bool{}
	mutations := map[string]bool{}
	for _, c := range r.Changes {
		if c.BaseRevision < 0 || seen[c.ID] || mutations[c.MutationID] || !mutationPattern.MatchString(c.MutationID) {
			return ErrInvalid
		}
		seen[c.ID] = true
		mutations[c.MutationID] = true
		if c.Value == nil && !strings.HasPrefix(c.ID, "annotation:") {
			return ErrInvalid
		}
		for _, v := range []*Value{c.Base, c.Value} {
			if err := validateValue(c.ID, v, now); err != nil {
				return err
			}
		}
	}
	return nil
}
func validateValue(id string, v *Value, now time.Time) error {
	parts := strings.SplitN(id, ":", 2)
	if len(parts) != 2 || len(id) > 100 {
		return ErrInvalid
	}
	kind := parts[0]
	switch kind {
	case "annotation", "visit", "day", "position":
	default:
		return ErrInvalid
	}
	if kind == "day" {
		if _, err := time.Parse("2006-01-02", parts[1]); err != nil {
			return ErrInvalid
		}
	} else if kind == "position" {
		if parts[1] != "last" {
			return ErrInvalid
		}
	} else {
		// Canonical references are checked even for deletion tombstones.
		loc := strings.Split(parts[1], ".")
		if len(loc) < 2 {
			return ErrInvalid
		}
		chapter, _ := strconv.Atoi(loc[1])
		if !scripture.ValidChapter(loc[0], chapter) {
			return ErrInvalid
		}
		if kind == "visit" && len(loc) != 2 {
			return ErrInvalid
		}
		if kind == "annotation" {
			if len(loc) != 3 {
				return ErrInvalid
			}
			verse, _ := strconv.Atoi(loc[2])
			if verse < 1 || verse > 176 {
				return ErrInvalid
			}
		}
	}
	if v == nil {
		return nil
	}
	if len(v.Note) > 32768 || v.Time < 0 || v.Time > now.Add(24*time.Hour).UnixMilli() {
		return ErrInvalid
	}
	switch kind {
	case "day":
		if v.Day != parts[1] || !reflect.DeepEqual(*v, Value{Day: v.Day}) {
			return ErrInvalid
		}
	case "annotation":
		if v.Verse < 1 || v.Verse > 176 || id != fmt.Sprintf("annotation:%s.%d.%d", v.Book, v.Chapter, v.Verse) {
			return ErrInvalid
		}
		if v.Highlight != "" && v.Highlight != "gold" && v.Highlight != "sage" && v.Highlight != "rose" {
			return ErrInvalid
		}
		if v.Style != "" && v.Style != "background" && v.Style != "underline" && v.Style != "margin" {
			return ErrInvalid
		}
		if v.Time != 0 || v.Day != "" {
			return ErrInvalid
		}
	case "visit", "position":
		if kind == "visit" && id != fmt.Sprintf("visit:%s.%d", v.Book, v.Chapter) {
			return ErrInvalid
		}
		if v.Time == 0 || !reflect.DeepEqual(*v, Value{Book: v.Book, Chapter: v.Chapter, Time: v.Time}) {
			return ErrInvalid
		}
	}
	if kind != "day" {
		if !scripture.ValidChapter(v.Book, v.Chapter) {
			return ErrInvalid
		}
	}
	return nil
}

// Three-way merge preserves independent field edits. Concurrent note edits retain
// both texts, separated visibly. Concurrent deletion wins over an unseen edit.
// Visits/position use event time, so an old offline visit cannot replace a newer one.
func Merge(id string, base, local, remote *Value, exists bool) *Value {
	if reflect.DeepEqual(local, base) {
		return remote
	}
	if reflect.DeepEqual(local, remote) {
		return remote
	}
	if !exists || reflect.DeepEqual(remote, base) {
		return local
	}
	if local == nil || remote == nil {
		return nil
	}
	if strings.HasPrefix(id, "day:") {
		return remote
	}
	if strings.HasPrefix(id, "visit:") || strings.HasPrefix(id, "position:") {
		if local.Time > remote.Time {
			return local
		}
		return remote
	}
	b := Value{}
	if base != nil {
		b = *base
	}
	merged := *remote
	if local.Highlight != b.Highlight {
		merged.Highlight = local.Highlight
	}
	if local.Style != b.Style {
		merged.Style = local.Style
	}
	if local.Bookmarked != b.Bookmarked {
		merged.Bookmarked = local.Bookmarked
	}
	if local.Note != b.Note {
		merged.Note = local.Note
		// Clearing an unchanged note is intentional. A concurrent edit from
		// another device is retained so clearing cannot silently discard it.
		if local.Note == "" && remote.Note != b.Note {
			merged.Note = remote.Note
		}
		if remote.Note != b.Note && remote.Note != local.Note && remote.Note != "" && local.Note != "" {
			notes := strings.Split(remote.Note, "\n\n---\n\n")
			for _, note := range strings.Split(local.Note, "\n\n---\n\n") {
				found := false
				for _, old := range notes {
					if note == old {
						found = true
					}
				}
				if !found {
					notes = append(notes, note)
				}
			}
			merged.Note = strings.Join(notes, "\n\n---\n\n")
		}
	}
	return &merged
}
func encode(v *Value) []byte { b, _ := json.Marshal(v); return b }
