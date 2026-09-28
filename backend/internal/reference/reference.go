// Package reference recognizes a direct Bible verse reference typed into a search query, for
// direct verse lookups. English and PT-BR names share OSIS identifiers; existence is
// always checked against the corpus before a reference becomes evidence.
package reference

import (
	"regexp"
	"strconv"
	"strings"

	"verbum/backend/internal/domain"
	"verbum/backend/internal/retrieval"
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
	pairs = append(pairs, [][2]string{
		{"Gen", "Gênesis"}, {"Exod", "Êxodo"}, {"Lev", "Levítico"}, {"Num", "Números"},
		{"Deut", "Deuteronômio"}, {"Josh", "Josué"}, {"Judg", "Juízes"}, {"Ruth", "Rute"},
		{"1Sam", "1 Samuel"}, {"2Sam", "2 Samuel"}, {"1Kgs", "1 Reis"}, {"2Kgs", "2 Reis"},
		{"1Chr", "1 Crônicas"}, {"2Chr", "2 Crônicas"}, {"Ezra", "Esdras"}, {"Neh", "Neemias"},
		{"Esth", "Ester"}, {"Job", "Jó"}, {"Ps", "Salmo"}, {"Ps", "Salmos"},
		{"Prov", "Provérbios"}, {"Eccl", "Eclesiastes"}, {"Song", "Cantares"}, {"Song", "Cântico dos Cânticos"},
		{"Isa", "Isaías"}, {"Jer", "Jeremias"}, {"Lam", "Lamentações"}, {"Ezek", "Ezequiel"},
		{"Dan", "Daniel"}, {"Hos", "Oseias"}, {"Joel", "Joel"}, {"Amos", "Amós"},
		{"Obad", "Obadias"}, {"Jonah", "Jonas"}, {"Mic", "Miqueias"}, {"Nah", "Naum"},
		{"Hab", "Habacuque"}, {"Zeph", "Sofonias"}, {"Hag", "Ageu"}, {"Zech", "Zacarias"},
		{"Mal", "Malaquias"}, {"Matt", "Mateus"}, {"Mark", "Marcos"}, {"Luke", "Lucas"},
		{"John", "João"}, {"Acts", "Atos"}, {"Rom", "Romanos"}, {"1Cor", "1 Coríntios"},
		{"2Cor", "2 Coríntios"}, {"Gal", "Gálatas"}, {"Eph", "Efésios"}, {"Phil", "Filipenses"},
		{"Col", "Colossenses"}, {"1Thess", "1 Tessalonicenses"}, {"2Thess", "2 Tessalonicenses"},
		{"1Tim", "1 Timóteo"}, {"2Tim", "2 Timóteo"}, {"Titus", "Tito"}, {"Phlm", "Filemom"},
		{"Heb", "Hebreus"}, {"Jas", "Tiago"}, {"1Pet", "1 Pedro"}, {"2Pet", "2 Pedro"},
		{"1John", "1 João"}, {"2John", "2 João"}, {"3John", "3 João"}, {"Jude", "Judas"}, {"Rev", "Apocalipse"},
	}...)
	m := make(map[string]string, len(pairs)*2)
	for _, p := range pairs {
		osisID, name := p[0], p[1]
		m[normalize(osisID)] = osisID
		m[normalize(name)] = osisID
	}
	return m
}

func normalize(s string) string {
	return strings.ReplaceAll(retrieval.Normalize(s), " ", "")
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
