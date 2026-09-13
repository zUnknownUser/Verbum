// Package reference recognizes a direct Bible verse reference typed into a search query, for
// §28's "a direct reference match wins outright". This is deliberately narrower than the apps'
// on-device PassageReferenceParser (Swift/Kotlin): it accepts an OSIS id or one common English
// book name, plus chapter:verse, and nothing fancier — no abbreviation matrix, no verse ranges,
// no other languages. The apps remain the primary place a reference gets parsed (§28 says the
// server "may repeat" that, not that it must match it feature-for-feature).
package reference

import (
	"regexp"
	"strconv"
	"strings"

	"verbum/backend/internal/domain"
)

// bookNames maps a normalized (lowercased, spaces removed) book name or OSIS id to its OSIS
// id. Generated from the 66 ids VerbumKit/Sources/Clients/Resources/web.tsv actually uses.
var bookNames = buildBookNames()

func buildBookNames() map[string]string {
	pairs := [][2]string{
		{"Gen", "Genesis"}, {"Exod", "Exodus"}, {"Lev", "Leviticus"}, {"Num", "Numbers"},
		{"Deut", "Deuteronomy"}, {"Josh", "Joshua"}, {"Judg", "Judges"}, {"Ruth", "Ruth"},
		{"1Sam", "1 Samuel"}, {"2Sam", "2 Samuel"}, {"1Kgs", "1 Kings"}, {"2Kgs", "2 Kings"},
		{"1Chr", "1 Chronicles"}, {"2Chr", "2 Chronicles"}, {"Ezra", "Ezra"}, {"Neh", "Nehemiah"},
		{"Esth", "Esther"}, {"Job", "Job"}, {"Ps", "Psalm"}, {"Ps", "Psalms"},
		{"Prov", "Proverbs"}, {"Eccl", "Ecclesiastes"}, {"Song", "Song of Solomon"},
		{"Song", "Song of Songs"}, {"Isa", "Isaiah"}, {"Jer", "Jeremiah"},
		{"Lam", "Lamentations"}, {"Ezek", "Ezekiel"}, {"Dan", "Daniel"}, {"Hos", "Hosea"},
		{"Joel", "Joel"}, {"Amos", "Amos"}, {"Obad", "Obadiah"}, {"Jonah", "Jonah"},
		{"Mic", "Micah"}, {"Nah", "Nahum"}, {"Hab", "Habakkuk"}, {"Zeph", "Zephaniah"},
		{"Hag", "Haggai"}, {"Zech", "Zechariah"}, {"Mal", "Malachi"}, {"Matt", "Matthew"},
		{"Mark", "Mark"}, {"Luke", "Luke"}, {"John", "John"}, {"Acts", "Acts"},
		{"Rom", "Romans"}, {"1Cor", "1 Corinthians"}, {"2Cor", "2 Corinthians"},
		{"Gal", "Galatians"}, {"Eph", "Ephesians"}, {"Phil", "Philippians"},
		{"Col", "Colossians"}, {"1Thess", "1 Thessalonians"}, {"2Thess", "2 Thessalonians"},
		{"1Tim", "1 Timothy"}, {"2Tim", "2 Timothy"}, {"Titus", "Titus"}, {"Phlm", "Philemon"},
		{"Heb", "Hebrews"}, {"Jas", "James"}, {"1Pet", "1 Peter"}, {"2Pet", "2 Peter"},
		{"1John", "1 John"}, {"2John", "2 John"}, {"3John", "3 John"}, {"Jude", "Jude"},
		{"Rev", "Revelation"},
	}
	m := make(map[string]string, len(pairs)*2)
	for _, p := range pairs {
		osisID, name := p[0], p[1]
		m[normalize(osisID)] = osisID
		m[normalize(name)] = osisID
	}
	return m
}

func normalize(s string) string {
	return strings.ToLower(strings.ReplaceAll(s, " ", ""))
}

// bookAndLocation splits "1 Samuel 17:49" into ("1 Samuel", "17", "49"), and "Ps 23" into
// ("Ps", "23", ""). The book part is non-greedy so multi-word names are captured whole.
var bookAndLocation = regexp.MustCompile(`^(.+?)\s+(\d{1,3})(?::(\d{1,3}))?$`)

// ParseVerse recognizes "<book> <chapter>:<verse>" and returns false for anything else,
// including a chapter-only reference (no single verse to point at) — callers needing a direct
// search hit only care about the verse-exact case.
func ParseVerse(query string) (domain.PassageReference, bool) {
	m := bookAndLocation.FindStringSubmatch(strings.TrimSpace(query))
	if m == nil || m[3] == "" {
		return domain.PassageReference{}, false
	}
	bookID, ok := bookNames[normalize(m[1])]
	if !ok {
		return domain.PassageReference{}, false
	}
	chapter, err := strconv.Atoi(m[2])
	if err != nil || chapter < 1 {
		return domain.PassageReference{}, false
	}
	verse, err := strconv.Atoi(m[3])
	if err != nil || verse < 1 {
		return domain.PassageReference{}, false
	}
	return domain.PassageReference{BookID: bookID, Chapter: chapter, VerseStart: &verse, VerseEnd: &verse}, true
}
