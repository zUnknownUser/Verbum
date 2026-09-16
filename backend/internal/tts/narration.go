package tts

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"math"
	"os"
	"strings"
	"unicode/utf8"
)

const NarrationModel = "gemini-2.5-pro-tts"
const NarrationVoice = "Charon"
const narrationPromptVersion = 1
const geminiSegmentBytes = 3800 // Cloud TTS accepts at most 4000 UTF-8 bytes per field.

// Editorial instructions are trusted server data, never a client prompt or a paid classifier.
const narrationBase = `Narre somente o texto fornecido, integralmente e na ordem original, em português brasileiro. Não acrescente introdução, comentários, títulos, números de versículos, sons ou música. Não resuma, omita, repita nem modernize palavras.
Leitura bíblica de audiobook profissional para escuta longa e contemplativa. Voz masculina profunda e natural, íntima, humana, acolhedora e segura; presença sem forçar o grave. Tom sereno e reverente, sem solenidade artificial. O texto é o protagonista.
Ritmo calmo e confortável, nunca excessivamente lento ou palavra por palavra. Conduza as frases pelo significado, com pequenas variações naturais. Respire na pontuação e dê um pouco mais de espaço a mudanças de pensamento ou cena. Uma quebra de linha separa versículos, mas não exige uma pausa quando o pensamento continua.
Enfatize apenas palavras que naturalmente carregam significado. Seja mais suave no consolo, firme nas declarações, contemplativo nas reflexões e discretamente intenso nos momentos dramáticos. Nos diálogos, diferencie sutilmente a intenção das falas, mantendo a mesma voz e identidade, sem imitar personagens.
Evite voz de trailer, documentário, publicidade ou pregador; atuação teatral, dramatização, entonação repetitiva, pausas previsíveis e a mesma cadência no fim de todas as frases. Pronuncie nomes próprios e termos bíblicos com clareza em português brasileiro. Mantenha timbre, distância do microfone e volume consistentes do início ao fim, como parte de uma leitura contínua.`

type narrationProfile struct {
	Style          string
	Pace           string // Prompt direction, not DSP speed: playback speed stays on the device.
	Expressiveness string
	Direction      string
}

var narrationProfiles = map[string]narrationProfile{
	"narrative":     {"narrative", "calmo e fluido", "sutil", "Conduza a narrativa naturalmente, com pequenas mudanças de intensidade conforme a cena e sem anunciar transições."},
	"contemplative": {"contemplative", "ligeiramente mais espaçado", "contida", "Dê espaço para respirar às imagens e ao paralelismo poético, sem cantar, declamar ou impor pausas a cada linha."},
	"wisdom":        {"wisdom", "ponderado", "contida", "Leia com clareza e ponderação. Deixe cada reflexão ser compreendida, sem dramatizar nem dar tom de sermão."},
	"teaching":      {"teaching", "conversacional e articulado", "sutil", "Comunique o encadeamento das ideias com clareza e proximidade, como uma leitura pessoal; sem pregar ou enfatizar todas as afirmações."},
	"prophecy":      {"prophecy", "calmo e firme", "moderada, estritamente controlada", "Dê presença às declarações e imagens, com intensidade discreta. Nunca use voz ameaçadora, grandiosa, de trailer ou atuação teatral."},
	"dialogue":      {"dialogue", "conversacional", "sutil", "Valorize a intenção e a escuta nas falas. Preserve uma única voz, com mudanças mínimas de entonação; não represente personagens."},
}
var narrationBooks = map[string]string{
	"Lev": "teaching", "Deut": "teaching", "Job": "wisdom", "Ps": "contemplative", "Song": "contemplative", "Lam": "contemplative", "Prov": "wisdom", "Eccl": "wisdom",
	"Isa": "prophecy", "Jer": "prophecy", "Ezek": "prophecy", "Dan": "prophecy", "Hos": "prophecy", "Joel": "prophecy", "Amos": "prophecy", "Obad": "prophecy", "Mic": "prophecy", "Nah": "prophecy", "Hab": "prophecy", "Zeph": "prophecy", "Hag": "prophecy", "Zech": "prophecy", "Mal": "prophecy", "Rev": "prophecy",
	"Rom": "teaching", "1Cor": "teaching", "2Cor": "teaching", "Gal": "teaching", "Eph": "teaching", "Phil": "teaching", "Col": "teaching", "1Thess": "teaching", "2Thess": "teaching", "1Tim": "teaching", "2Tim": "teaching", "Titus": "teaching", "Phlm": "teaching", "Heb": "teaching", "Jas": "teaching", "1Pet": "teaching", "2Pet": "teaching", "1John": "teaching", "2John": "teaching", "3John": "teaching", "Jude": "teaching",
}

type literaryOverride struct {
	Book                 string
	Chapter, First, Last int
	Style                string
}

// Small, reviewable editorial starting set; this is not an exhaustive annotation of the Bible.
var narrationOverrides = []literaryOverride{
	{"John", 1, 1, 18, "contemplative"}, {"John", 3, 1, 21, "dialogue"},
	{"Matt", 5, 1, 48, "teaching"}, {"Matt", 6, 1, 34, "teaching"}, {"Matt", 7, 1, 29, "teaching"},
	{"Dan", 1, 1, 21, "narrative"}, {"Dan", 2, 1, 49, "narrative"}, {"Dan", 3, 1, 30, "narrative"}, {"Dan", 4, 1, 37, "narrative"}, {"Dan", 5, 1, 31, "narrative"}, {"Dan", 6, 1, 28, "narrative"},
}

func literaryStyle(book string, chapter, verse int) string {
	for _, o := range narrationOverrides {
		if o.Book == book && o.Chapter == chapter && verse >= o.First && verse <= o.Last {
			return o.Style
		}
	}
	if s := narrationBooks[book]; s != "" {
		return s
	}
	return "narrative"
}
func DefaultVoice(language string) string {
	if language == "pt-BR" {
		if os.Getenv("VERBUM_TTS_NARRATOR") == "chirp3" {
			return "pt-BR-Chirp3-HD-Aoede"
		}
		return NarrationVoice
	}
	return language + "-Standard-A"
}
func (r Request) IsGemini() bool { return r.Language == "pt-BR" && r.Voice == NarrationVoice }
func (r Request) narrationPrompt() string {
	style := r.style
	if style == "" {
		style = literaryStyle(r.literaryBook, r.literaryChapter, 1)
	}
	p := narrationProfiles[style]
	return fmt.Sprintf("%s\nDireção deste trecho: %s Ritmo %s. Expressividade %s.", narrationBase, p.Direction, p.Pace, p.Expressiveness)
}
func narrationRevision() string {
	raw, _ := json.Marshal([]any{NarrationModel, NarrationVoice, narrationPromptVersion, narrationBase, narrationProfiles, narrationBooks, narrationOverrides, geminiSegmentBytes})
	h := sha256.Sum256(raw)
	return hex.EncodeToString(h[:])
}

// EconomicIdentity also versions the permanent database cache, not just disk/mobile files.
func EconomicIdentity(r Request) string { return cacheKey(r) }

func (r Request) textParts() []string {
	if r.IsGemini() {
		return splitTextAt(r.Text, geminiSegmentBytes)
	}
	return splitText(r.Text)
}
func (r Request) speechParts() []speechPart {
	if !r.IsGemini() {
		return timedParts(r.Verses)
	}
	var out []speechPart
	current := speechPart{}
	flush := func() {
		if current.text != "" {
			out = append(out, current)
		}
		current = speechPart{}
	}
	for _, v := range r.Verses {
		style := literaryStyle(r.literaryBook, r.literaryChapter, v.Number)
		if current.text != "" && (current.style != style || len(current.text)+1+len(v.Text) > geminiSegmentBytes) {
			flush()
		}
		if len(v.Text) > geminiSegmentBytes {
			for _, part := range splitTextAt(v.Text, geminiSegmentBytes) {
				out = append(out, speechPart{text: part, first: v.Number, last: v.Number, style: style})
			}
			continue
		}
		if current.text == "" {
			current.first = v.Number
			current.style = style
		} else {
			current.text += "\n"
		}
		current.text += v.Text
		current.last = v.Number
	}
	flush()
	return out
}

// Pro: $1/million input tokens, $20/million output tokens, 25 audio tokens/sec.
// UTF-8 bytes conservatively bound input tokens (including the repeated direction).
// Reserve the full documented 16,384-token output allowance for EVERY segment.
// Unknown outcomes retain this reservation; a successful render settles by measured WAV duration.
func EstimateCost(r Request, timed bool) int64 {
	if !r.IsGemini() {
		return int64(utf8.RuneCountInString(r.Text)) * 30
	}
	var cost int64
	if timed {
		for _, p := range r.speechParts() {
			segment := r
			segment.Text = p.text
			segment.style = p.style
			cost += segment.geminiInputCost() + 16384*20
		}
	} else {
		for _, p := range r.textParts() {
			if strings.TrimSpace(p) == "" {
				continue
			}
			segment := r
			segment.Text = p
			cost += segment.geminiInputCost() + 16384*20
		}
	}
	return cost
}
func (r Request) geminiInputCost() int64 { return int64(len(r.Text) + len(r.narrationPrompt())) }
func (r Request) measuredCost(seconds float64) int64 {
	return r.geminiInputCost() + int64(math.Ceil(seconds*25))*20
}

func geminiAudioVersion() string {
	r, _ := (Request{Text: "audio-version", Language: "pt-BR", Voice: NarrationVoice}).normalized()
	return strings.TrimSuffix(cacheKey(r), ".mp3")
}
