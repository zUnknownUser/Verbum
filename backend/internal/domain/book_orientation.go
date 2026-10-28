package domain

// BookOrientation describes the whole book; it never substitutes for ChapterStudy.
type BookOrientation struct {
	Genre              string             `json:"genre"`
	Context            string             `json:"context"`
	Purpose            string             `json:"purpose"`
	Outline            []BookSection      `json:"outline"`
	Readings           []PassageReference `json:"readings"`
	SourceReferenceIDs []string           `json:"sourceReferenceIds"`
}
type BookSection struct {
	Title        string `json:"title"`
	ChapterStart int    `json:"chapterStart"`
	ChapterEnd   int    `json:"chapterEnd"`
}
