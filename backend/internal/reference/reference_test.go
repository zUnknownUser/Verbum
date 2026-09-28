package reference

import (
	"reflect"
	"testing"

	"verbum/backend/internal/domain"
)

func verse(bookID string, chapter, v int) domain.PassageReference {
	vv := v
	return domain.PassageReference{BookID: bookID, Chapter: chapter, VerseStart: &vv, VerseEnd: &vv}
}

func TestParseVerseRecognizesFullNameAndOSISID(t *testing.T) {
	cases := map[string]domain.PassageReference{
		"John 3:16":           verse("John", 3, 16),
		"john 3:16":           verse("John", 3, 16),
		"1 Samuel 17:49":      verse("1Sam", 17, 49),
		"1Sam 17:49":          verse("1Sam", 17, 49),
		"Psalm 23:1":          verse("Ps", 23, 1),
		"Psalms 23:1":         verse("Ps", 23, 1),
		"Song of Solomon 2:1": verse("Song", 2, 1),
	}
	for query, want := range cases {
		got, ok := ParseVerse(query)
		if !ok || !reflect.DeepEqual(got, want) {
			t.Errorf("ParseVerse(%q) = %+v, %v; want %+v, true", query, got, ok, want)
		}
	}
}

func TestParseVerseRejectsNonReferences(t *testing.T) {
	cases := []string{
		"David",
		"why did job suffer",
		"leviathan",
		"Ps 23",           // chapter-only: not a verse-exact hit
		"Nonexistent 1:1", // unknown book name
		"",
		"John abc:16",
	}
	for _, query := range cases {
		if _, ok := ParseVerse(query); ok {
			t.Errorf("ParseVerse(%q) = true, want false", query)
		}
	}
}

func TestPortugueseReferencesWithAndWithoutAccents(t *testing.T) {
	cases := map[string]domain.PassageReference{
		"Jó 38:4": verse("Job", 38, 4), "Jo 38:4": verse("Job", 38, 4), "João 3:16": verse("John", 3, 16), "joao 3:16": verse("John", 3, 16),
		"1 João 4:8": verse("1John", 4, 8), "Êxodo 3:2": verse("Exod", 3, 2), "Salmos 23:1": verse("Ps", 23, 1),
		"Cântico dos Cânticos 2:1": verse("Song", 2, 1), "2 Coríntios 5:17": verse("2Cor", 5, 17), "Apocalipse 22:21": verse("Rev", 22, 21),
	}
	for input, want := range cases {
		got, ok := ParseVerse(input)
		if !ok || !reflect.DeepEqual(got, want) {
			t.Errorf("%s = %+v", input, got)
		}
	}
}
