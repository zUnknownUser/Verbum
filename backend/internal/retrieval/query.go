// Package retrieval prepares bounded, deterministic lexical queries. Semantic retrieval
// still receives the original question; no language model is called to rewrite it.
package retrieval

import (
	"strings"
	"unicode"

	"golang.org/x/text/unicode/norm"
)

// Terms returns independent concepts, each expressed as English full-text alternatives.
// This bridges conversational PT-BR to the existing WEB corpus without translating Scripture.
// Unknown words are retained, so this is a lexical aid, not an intent whitelist.
func Terms(question string) []string {
	words := strings.FieldsFunc(Normalize(question), func(r rune) bool { return !unicode.IsLetter(r) && !unicode.IsDigit(r) })
	result := make([]string, 0, 16)
	seen := map[string]bool{}
	for _, word := range words {
		if stopWords[word] || len([]rune(word)) < 2 {
			continue
		}
		term := word
		if expanded, ok := alternatives[word]; ok {
			term = expanded
		}
		if !seen[term] {
			result = append(result, term)
			seen[term] = true
		}
		if len(result) == 16 {
			break
		}
	}
	return result
}

func Normalize(text string) string {
	return strings.Map(func(r rune) rune {
		if unicode.Is(unicode.Mn, r) {
			return -1
		}
		return unicode.ToLower(r)
	}, norm.NFD.String(text))
}

func wordSet(text string) map[string]bool {
	result := map[string]bool{}
	for _, word := range strings.Fields(text) {
		result[word] = true
	}
	return result
}

var stopWords = wordSet(`a o as os um uma uns umas de da do das dos em no na nos nas com ao aos para pra pro por pelo pela pelos pelas e ou que se seu sua seus suas eu tu voce voces ele ela eles elas me mim meu minha como onde quando qual quais quem porque pq sobre naqueles naquele nessa nesse isso isto aquilo esse essa aquele aquela the a an of in on at to with for from and or is are was were does did do what where when who why how which tell show find me my you your he she it they his her their about can could would please`)

// Shared synonym groups cover vocabulary, never prewritten answers or verse locations.
// English morphology is stemmed by PostgreSQL after this expansion.
var alternatives = buildAlternatives()

func buildAlternatives() map[string]string {
	groups := []struct{ input, output string }{
		{"deus god senhor lord yahweh jeova", "god | lord | yahweh"},
		{"fala falar falou falando diz dizer disse disse-lhe conversa conversar conversou responde respondeu answer speak say said talk", "speak | say | said | answer | talk"},
		{"jo job", "job"}, {"joao john", "john"}, {"jesus cristo christ", "jesus | christ"},
		{"moises moses", "moses"}, {"davi david", "david"}, {"golias goliath", "goliath | philistine"},
		{"abraao abraham", "abraham"}, {"isaias isaiah", "isaiah"}, {"elias elijah", "elijah"},
		{"pedro peter", "peter"}, {"paulo paul", "paul"}, {"jose joseph", "joseph"},
		{"maria mary", "mary"}, {"lazaro lazarus", "lazarus"}, {"noe noah", "noah"},
		{"jonas jonah", "jonah"}, {"salomao solomon", "solomon"}, {"jacob jaco", "jacob"},
		{"esau", "esau"}, {"daniel", "daniel"}, {"ester esther", "esther"}, {"rute ruth", "ruth"},
		{"ceu ceus heaven heavens", "heaven"}, {"terra earth", "earth | land"},
		{"criou criar criacao fundava fundou fundamentos created creation foundations", "create | foundation | beginning"},
		{"redemoinho tempestade whirlwind storm", "whirlwind | storm"},
		{"ressuscita ressuscitar ressuscitou ressurreicao raise raised resurrection", "raise | resurrection | dead"},
		{"morreu morte mortos morto dead death", "dead | death | die"},
		{"amor ama amar love", "love"}, {"perdoar perdao perdoou forgive forgiveness", "forgive | forgiveness"},
		{"inimigo inimigos enemy enemies", "enemy"}, {"proximo neighbor neighbour", "neighbor | neighbour"},
		{"ansiedade ansioso ansiosa preocupacao preocupar worry anxious anxiety", "anxious | worry | care"},
		{"amanha tomorrow", "tomorrow"}, {"medo fear afraid", "fear | afraid"},
		{"sofrimento sofrer sofreu suffering suffer", "suffer | affliction | trouble"},
		{"sabedoria wisdom wise", "wisdom | wise"}, {"fe faith", "faith | believe"},
		{"esperanca hope", "hope"}, {"paz peace", "peace"}, {"oracao orar prayer pray", "pray | prayer"},
		{"pastor shepherd", "shepherd"}, {"ovelha ovelhas sheep", "sheep"},
		{"filho son", "son"}, {"pai father", "father"}, {"perdido perdida lost", "lost"},
		{"prodigo prodigal", "prodigal | inheritance | son"}, {"semente semeador seed sower", "seed | sow"},
		{"samaritano samaritan", "samaritan"}, {"agua aguas water", "water"},
		{"mar sea", "sea"}, {"vermelho red", "red"}, {"sarça sarca burning", "burning | bush"},
		{"pao paes bread loaves", "bread | loaves"}, {"peixe peixes fish", "fish"},
		{"multiplicacao multiplicou alimentou feeding fed", "bread | feed | eat"},
		{"vida life", "life"}, {"fogo fire", "fire | burn"}, {"planta plant bush", "plant | bush"},
		{"engolido engoliu swallow swallowed", "swallow"}, {"homem man", "man"},
		{"ferido feridas wounded wounds", "wound | injury"}, {"estrada road", "road | way"},
		{"ajudou ajudar help helped", "help | compassion | bind"}, {"afundar afundando sink sinking", "sink"},
		{"segura segurou hold caught", "hold | catch | hand"},
		{"cura curou heal healed", "heal"}, {"cego blind", "blind"},
		{"cruz cross", "cross"}, {"pecado pecados sin sins", "sin"},
		{"mandamento mandamentos commandment commandments", "commandment"},
		{"tentacao temptation", "temptation | tempt"}, {"deserto wilderness", "wilderness | desert"},
		{"batismo baptism", "baptism | baptize"}, {"espirito spirit", "spirit"},
		{"santo holy", "holy"}, {"graca grace", "grace"}, {"salvacao salvation", "salvation | save"},
	}
	result := map[string]string{}
	for _, group := range groups {
		for _, word := range strings.Fields(group.input) {
			result[Normalize(word)] = group.output
		}
	}
	return result
}
