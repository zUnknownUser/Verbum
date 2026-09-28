package ask

import (
	"context"
	"strings"
	"testing"
	"verbum/backend/internal/domain"
)

func TestContextIncludesOpeningOfGodsSpeechAndQuestionToJob(t *testing.T) {
	refs := contextReferences([]domain.PassageReference{ref("Job", 38, 1), ref("Job", 40, 6)})
	found := map[string]bool{}
	for _, r := range refs {
		if r.Chapter != 38 && r.Chapter != 40 || *r.VerseStart < 1 {
			t.Fatal(r)
		}
		if found[r.Key()] {
			t.Fatal("duplicate", r.Key())
		}
		found[r.Key()] = true
	}
	if !found["Job.38.4"] || !found["Job.38.1"] {
		t.Fatal("speaker or question missing")
	}
	texts := map[string]string{"Job.38.1": "Then Yahweh answered Job out of the whirlwind,", "Job.38.4": "Where were you when I laid the foundations of the earth?"}
	if items := boundedEvidence(refs, texts); len(items) != 2 {
		t.Fatal(items)
	}
	st := &fakeStore{refs: []domain.PassageReference{ref("Job", 38, 1)}, text: texts}
	synth := &fakeSynthesizer{reply: `{"answer":"Jó 38:1–4","summary":"Deus fala com Jó","citedPassageIndexes":[0,1],"confidence":"high"}`}
	result, err := (&Service{Store: st, Synthesizer: synth}).Ask(context.Background(), "Önde Deus conversa com Jó?")
	if err != nil || len(result.PassageReferences) != 2 || !strings.Contains(synth.gotUser, "Job 38:4") {
		t.Fatalf("%+v %v", result, err)
	}
}
func TestEvidenceBoundAndOversizedText(t *testing.T) {
	refs := []domain.PassageReference{}
	texts := map[string]string{}
	for chapter := 1; chapter <= 12; chapter++ {
		refs = append(refs, ref("Ps", chapter, 8))
	}
	refs = contextReferences(refs)
	if len(refs) > maxEvidenceVerses {
		t.Fatal(len(refs))
	}
	for _, r := range refs {
		texts[r.Key()] = strings.Repeat("x", 1000)
	}
	if items := boundedEvidence(refs, texts); len(items) != 20 {
		t.Fatal(len(items))
	}
	texts[refs[0].Key()] = strings.Repeat("x", maxEvidenceBytes+1)
	for _, item := range boundedEvidence(refs, texts) {
		if item.ref.Key() == refs[0].Key() {
			t.Fatal("oversized source admitted")
		}
	}
}

type forbiddenEmbedder struct{}

func (forbiddenEmbedder) Embed(context.Context, string) ([]float32, error) {
	panic("direct reference must not call a provider")
}
func TestExactPortugueseReferenceSkipsEmbedding(t *testing.T) {
	st := &fakeStore{refs: []domain.PassageReference{ref("Job", 38, 4)}, text: map[string]string{"Job.38.4": "Where were you?"}}
	synth := &fakeSynthesizer{reply: `{"answer":"Jó 38:4","summary":"Passagem","citedPassageIndexes":[0],"confidence":"high"}`}
	_, err := (&Service{Store: st, Embedder: forbiddenEmbedder{}, Synthesizer: synth}).Ask(context.Background(), "Jó 38:4")
	if err != nil {
		t.Fatal(err)
	}
}
func TestUnsupportedSynthesisOffersOnlyStoredCandidates(t *testing.T) {
	st := twoVerseStore()
	st.refs = append(st.refs, ref("Job", 99, 99))
	synth := &fakeSynthesizer{reply: `{"answer":"Invented statement","summary":"Unsupported","citedPassageIndexes":[999],"confidence":"high"}`}
	result, err := (&Service{Store: st, Synthesizer: synth}).Ask(context.Background(), "unsupported claim")
	if err != nil || result.Answer != "" || result.Confidence != "low" || len(result.PassageReferences) != 2 {
		t.Fatal(result, err)
	}
}

func TestDiverseSeedsPreserveRankAndRoomForAnotherScene(t *testing.T) {
	candidates := []domain.PassageReference{
		ref("John", 11, 39), ref("John", 11, 40), ref("John", 11, 43),
		ref("John", 12, 1), ref("John", 11, 5), ref("John", 11, 6),
	}
	seeds := diverseSeeds(candidates, 3)
	if len(seeds) != 3 || seeds[0].Key() != "John.11.39" || seeds[1].Key() != "John.12.1" || seeds[2].Key() != "John.11.5" {
		t.Fatal(seeds)
	}
	for _, key := range []string{"John.11.40", "John.11.43"} {
		found := false
		for _, item := range contextReferences(seeds) {
			found = found || item.Key() == key
		}
		if !found {
			t.Fatal("neighbor removed from seeds must still reach the evidence", key)
		}
	}
}
