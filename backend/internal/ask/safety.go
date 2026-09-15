package ask

import "strings"

// highStakesSignals: real testing (2026-09-13) showed the synthesis prompt's instruction to
// recommend professional help on mental-health/crisis questions is NOT reliably followed by the
// model — a direct "I feel hopeless and depressed" question got a purely devotional answer with
// no such note, twice, even after strengthening the prompt wording. §31 ("avoid replacing
// professional medical/legal/financial help") is not optional, so this is enforced here
// deterministically instead of trusted to the model, the same reason citation validation in
// ask.go never trusts the model's own text. Keep this list conservative (few false positives)
// rather than exhaustive — it is a floor under the prompt instruction, not a replacement for it.
var highStakesSignals = []string{
	"depress", "suicid", "self-harm", "self harm", "hurt myself", "kill myself", "hopeless",
	"want to die", "end my life", "suicídio", "suicidio", "quero morrer", "me matar", "sem esperança", "sem esperanca", "automutil", "abuso", "deprimid", "crise financeira", "violência", "violencia", "divórcio", "divorcio",
	"abuse", "abused", "abusive", "overdose", "addiction", "addicted",
	"lawsuit", "sue me", "legal advice", "custody battle", "divorce",
	"bankrupt", "financial crisis", "can't pay", "debt collector",
}

// professionalHelpNote is fixed, non-generated text — never produced by the model — so there is
// nothing here for the citation/fabrication concerns elsewhere in this package to apply to.
const professionalHelpNote = " This isn't a substitute for professional help: please also " +
	"reach out to a qualified doctor, therapist, counselor, lawyer, or crisis line, as fits " +
	"your situation."

func needsProfessionalHelpNote(question string) bool {
	q := strings.ToLower(question)
	for _, signal := range highStakesSignals {
		if strings.Contains(q, signal) {
			return true
		}
	}
	return false
}

// mentionsProfessionalHelp avoids an awkward double note on the rare occasion the model did
// include one on its own.
func mentionsProfessionalHelp(answer string) bool {
	a := strings.ToLower(answer)
	for _, word := range []string{"profissional", "terapeuta", "psicólogo", "psicologo", "médico", "medico", "advogado", "professional", "therapist", "counselor", "counsellor", "doctor", "crisis line", "hotline"} {
		if strings.Contains(a, word) {
			return true
		}
	}
	return false
}

const professionalHelpNotePT = " Esta resposta não substitui ajuda profissional. Procure também um médico, psicólogo, orientador, advogado ou serviço de apoio em crise, conforme a sua situação."
