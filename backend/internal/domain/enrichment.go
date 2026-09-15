package domain

// LexicalData preserves STEP's original script and distinct identifier schemes.
// Glosses are source-language metadata, not Scripture text or a translation of it.
type LexicalData struct {
	Language            string `json:"language"`
	Original            string `json:"original"`
	Transliteration     string `json:"transliteration"`
	Morphology          string `json:"morphology"`
	Gloss               string `json:"gloss"`
	GlossLanguage       string `json:"glossLanguage"`
	ExtendedStrong      string `json:"extendedStrong"`
	DisambiguatedStrong string `json:"disambiguatedStrong"`
	UnifiedStrong       string `json:"unifiedStrong"`
}

type EntityLocalization struct {
	Language    string   `json:"language"`
	SourceID    string   `json:"sourceId"`
	Name        string   `json:"name"`
	Aliases     []string `json:"aliases"`
	Description *string  `json:"description"`
}

type DatasetProvenance struct {
	Repository    string `json:"repository"`
	Revision      string `json:"revision"`
	Path          string `json:"path"`
	SHA256        string `json:"sha256"`
	LicenseURL    string `json:"licenseUrl"`
	Attribution   string `json:"attribution"`
	Modifications string `json:"modifications"`
}

type EntitySourceRecord struct {
	ID              string              `json:"id"`
	SourceID        string              `json:"sourceId"`
	ExternalID      string              `json:"externalId"`
	SourceLine      int                 `json:"sourceLine"`
	Identifiers     map[string][]string `json:"identifiers"`
	Lexical         *LexicalData        `json:"lexical,omitempty"`
	Dataset         DatasetProvenance   `json:"dataset"`
	OccurrenceCount int                 `json:"occurrenceCount"`
}
