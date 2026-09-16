package ask

import (
	"context"
	"errors"
	"reflect"
	"strings"
	"testing"

	"verbum/backend/internal/domain"
)

func ref(bookID string, chapter, verse int) domain.PassageReference {
	v := verse
	return domain.PassageReference{BookID: bookID, Chapter: chapter, VerseStart: &v, VerseEnd: &v}
}

type fakeStore struct {
	refs           []domain.PassageReference
	text           map[string]string
	entities       []string
	entitiesErr    error
	gotEntityQuery []domain.PassageReference
	searchErr      error
	textErr        error
	gotEmbedding   []float32
	searchCalled   bool
}

func (f *fakeStore) SearchPassages(_ context.Context, _ string, embedding []float32, _ int) ([]domain.PassageReference, error) {
	f.searchCalled = true
	f.gotEmbedding = embedding
	if f.searchErr != nil {
		return nil, f.searchErr
	}
	return f.refs, nil
}

func (f *fakeStore) PassageText(_ context.Context, _ string, _ []domain.PassageReference) (map[string]string, error) {
	if f.textErr != nil {
		return nil, f.textErr
	}
	return f.text, nil
}

func (f *fakeStore) EntitiesForPassages(_ context.Context, refs []domain.PassageReference) ([]string, error) {
	f.gotEntityQuery = refs
	if f.entitiesErr != nil {
		return nil, f.entitiesErr
	}
	return f.entities, nil
}

type fakeEmbedder struct {
	vector []float32
	err    error
}

func (f *fakeEmbedder) Embed(_ context.Context, _ string) ([]float32, error) { return f.vector, f.err }

type fakeSynthesizer struct {
	reply   string
	err     error
	called  bool
	gotUser string
}

func (f *fakeSynthesizer) Complete(_ context.Context, _, user string) (string, error) {
	f.called = true
	f.gotUser = user
	if f.err != nil {
		return "", f.err
	}
	return f.reply, nil
}

func twoVerseStore() *fakeStore {
	return &fakeStore{
		refs: []domain.PassageReference{ref("Job", 1, 21), ref("Job", 2, 10)},
		text: map[string]string{
			"Job.1.21": "The Lord gave, and the Lord has taken away.",
			"Job.2.10": "Shall we receive good at the hand of God, and shall we not receive evil?",
		},
	}
}

func TestAskReturnsFallbackWithoutCallingSynthesizerWhenNoEvidence(t *testing.T) {
	store := &fakeStore{refs: []domain.PassageReference{}}
	synth := &fakeSynthesizer{}
	svc := &Service{Store: store, Synthesizer: synth}

	got, err := svc.Ask(context.Background(), "why did job suffer")
	if err != nil {
		t.Fatal(err)
	}
	if synth.called {
		t.Error("synthesizer must not be called when retrieval found nothing (§73)")
	}
	if got.Confidence != "low" || got.Answer != "" || len(got.PassageReferences) != 0 {
		t.Errorf("got %+v", got)
	}
}

func TestAskRejectsEmptyQuestion(t *testing.T) {
	svc := &Service{Store: &fakeStore{}, Synthesizer: &fakeSynthesizer{}}
	if _, err := svc.Ask(context.Background(), "   "); err == nil {
		t.Fatal("want an error for a blank question")
	}
}

func TestAskSuccessfulSynthesisWithValidCitations(t *testing.T) {
	store := twoVerseStore()
	synth := &fakeSynthesizer{reply: `{"answer":"Job accepted suffering as from God's hand.",
		"summary":"Job's response to loss.","citedPassageIndexes":[0,1],
		"confidence":"medium","interpretiveVariance":true}`}
	svc := &Service{Store: store, Synthesizer: synth, Translation: "WEB"}

	got, err := svc.Ask(context.Background(), "why did job suffer")
	if err != nil {
		t.Fatal(err)
	}
	if len(got.PassageReferences) != 2 {
		t.Fatalf("passageReferences = %+v", got.PassageReferences)
	}
	if got.Confidence != "medium" || !got.InterpretiveVariance {
		t.Errorf("confidence/variance = %q/%v", got.Confidence, got.InterpretiveVariance)
	}
	if len(got.SourceReferences) != 1 || got.SourceReferences[0].ID != "web-translation" {
		t.Errorf("sourceReferences = %+v", got.SourceReferences)
	}
	if len(got.EntityReferences) != 0 {
		t.Errorf("entityReferences = %+v, want empty when the store has no linked entities", got.EntityReferences)
	}
	if len(store.gotEntityQuery) != 2 {
		t.Errorf("entity linking queried %+v, want exactly the 2 cited passages", store.gotEntityQuery)
	}
	if !strings.Contains(synth.gotUser, "[0] Job 1:21") || !strings.Contains(synth.gotUser, "[1] Job 2:10") {
		t.Errorf("prompt did not include expected numbered excerpts:\n%s", synth.gotUser)
	}
}

func TestAskPopulatesEntityReferencesFromCitedPassagesOnly(t *testing.T) {
	store := twoVerseStore()
	store.entities = []string{"fixture.person.job", "fixture.person.eliphaz"}
	synth := &fakeSynthesizer{reply: `{"answer":"a","summary":"b","citedPassageIndexes":[0,1],
		"confidence":"medium","interpretiveVariance":false}`}
	svc := &Service{Store: store, Synthesizer: synth}

	got, err := svc.Ask(context.Background(), "q")
	if err != nil {
		t.Fatal(err)
	}
	want := []string{"fixture.person.eliphaz", "fixture.person.job"} // sorted
	if !reflect.DeepEqual(got.EntityReferences, want) {
		t.Errorf("entityReferences = %+v, want %+v", got.EntityReferences, want)
	}
}

func TestAskDegradesWhenEntityLinkingFails(t *testing.T) {
	store := twoVerseStore()
	store.entitiesErr = errors.New("boom")
	synth := &fakeSynthesizer{reply: `{"answer":"a","summary":"b","citedPassageIndexes":[0],
		"confidence":"medium","interpretiveVariance":false}`}
	svc := &Service{Store: store, Synthesizer: synth}

	got, err := svc.Ask(context.Background(), "q")
	if err != nil {
		t.Fatalf("entity-linking failure must not fail the whole answer: %v", err)
	}
	if len(got.EntityReferences) != 0 || got.Answer == "" {
		t.Errorf("got %+v", got)
	}
}

func TestAskDropsOutOfRangeCitationIndexes(t *testing.T) {
	store := twoVerseStore()
	synth := &fakeSynthesizer{reply: `{"answer":"x","summary":"y","citedPassageIndexes":[0,5,-1,0],
		"confidence":"high","interpretiveVariance":false}`}
	svc := &Service{Store: store, Synthesizer: synth}

	got, err := svc.Ask(context.Background(), "q")
	if err != nil {
		t.Fatal(err)
	}
	// Index 0 deduplicated, 5 and -1 dropped (never trusted): exactly one real citation.
	if len(got.PassageReferences) != 1 || !reflect.DeepEqual(got.PassageReferences[0], ref("Job", 1, 21)) {
		t.Errorf("passageReferences = %+v", got.PassageReferences)
	}
}

func TestAskFallsBackWhenNoCitationSurvivesValidation(t *testing.T) {
	store := twoVerseStore()
	synth := &fakeSynthesizer{reply: `{"answer":"a guess with nothing to back it",
		"summary":"s","citedPassageIndexes":[7,8],"confidence":"high","interpretiveVariance":false}`}
	svc := &Service{Store: store, Synthesizer: synth}

	got, err := svc.Ask(context.Background(), "q")
	if err != nil {
		t.Fatal(err)
	}
	if got.Answer != "" || got.Confidence != "low" {
		t.Errorf("got %+v, want the no-evidence fallback (§31: cite or don't answer)", got)
	}
}

func TestAskCapsConfidenceWhenEvidenceIsThin(t *testing.T) {
	store := &fakeStore{
		refs: []domain.PassageReference{ref("Job", 1, 21)},
		text: map[string]string{"Job.1.21": "The Lord gave, and the Lord has taken away."},
	}
	synth := &fakeSynthesizer{reply: `{"answer":"x","summary":"y","citedPassageIndexes":[0],
		"confidence":"high","interpretiveVariance":false}`}
	svc := &Service{Store: store, Synthesizer: synth}

	got, err := svc.Ask(context.Background(), "q")
	if err != nil {
		t.Fatal(err)
	}
	if got.Confidence != "low" {
		t.Errorf("confidence = %q, want low with only one piece of evidence regardless of the model's claim", got.Confidence)
	}
}

func TestAskDegradesToLexicalOnlyWhenEmbedderFails(t *testing.T) {
	store := twoVerseStore()
	synth := &fakeSynthesizer{reply: `{"answer":"x","summary":"y","citedPassageIndexes":[0],
		"confidence":"medium","interpretiveVariance":false}`}
	svc := &Service{Store: store, Embedder: &fakeEmbedder{err: errors.New("boom")}, Synthesizer: synth}

	got, err := svc.Ask(context.Background(), "q")
	if err != nil {
		t.Fatal(err)
	}
	if store.gotEmbedding != nil {
		t.Errorf("embedding passed to retrieval = %v, want nil after embedder failure", store.gotEmbedding)
	}
	if len(got.PassageReferences) == 0 {
		t.Error("ask should still succeed on lexical-only retrieval")
	}
}

func TestAskPropagatesSynthesizerFailure(t *testing.T) {
	store := twoVerseStore()
	synth := &fakeSynthesizer{err: errors.New("upstream exploded")}
	svc := &Service{Store: store, Synthesizer: synth}

	if _, err := svc.Ask(context.Background(), "q"); err == nil {
		t.Fatal("want an error when synthesis itself fails (unlike an optional embedder)")
	}
}

func TestAskRejectsMalformedSynthesisJSON(t *testing.T) {
	store := twoVerseStore()
	synth := &fakeSynthesizer{reply: "not json"}
	svc := &Service{Store: store, Synthesizer: synth}

	if _, err := svc.Ask(context.Background(), "q"); err == nil {
		t.Fatal("want an error for a non-JSON synthesis reply")
	}
}

func TestAskAppendsProfessionalHelpNoteOnHighStakesQuestions(t *testing.T) {
	store := twoVerseStore()
	synth := &fakeSynthesizer{reply: `{"answer":"Hope in God even in despair.","summary":"s",
		"citedPassageIndexes":[0,1],"confidence":"high","interpretiveVariance":false}`}
	svc := &Service{Store: store, Synthesizer: synth}

	got, err := svc.Ask(context.Background(), "I feel hopeless and depressed, what does the bible say")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(got.Answer, "professional") {
		t.Errorf("answer = %q, want the professional-help note appended (model prompting alone was found unreliable for this)", got.Answer)
	}
}

func TestAskDoesNotDuplicateProfessionalHelpNote(t *testing.T) {
	store := twoVerseStore()
	synth := &fakeSynthesizer{reply: `{"answer":"Hope in God, and please also see a therapist.",
		"summary":"s","citedPassageIndexes":[0],"confidence":"high","interpretiveVariance":false}`}
	svc := &Service{Store: store, Synthesizer: synth}

	got, err := svc.Ask(context.Background(), "I feel hopeless and depressed")
	if err != nil {
		t.Fatal(err)
	}
	if strings.Count(strings.ToLower(got.Answer), "therapist") != 1 {
		t.Errorf("answer = %q, want no duplicated note", got.Answer)
	}
}

func TestAskDefaultsUnknownConfidenceToLow(t *testing.T) {
	store := twoVerseStore()
	synth := &fakeSynthesizer{reply: `{"answer":"x","summary":"y","citedPassageIndexes":[0,1],
		"confidence":"very sure","interpretiveVariance":false}`}
	svc := &Service{Store: store, Synthesizer: synth}

	got, err := svc.Ask(context.Background(), "q")
	if err != nil {
		t.Fatal(err)
	}
	if got.Confidence != "low" {
		t.Errorf("confidence = %q, want low for an unrecognized value (never trust the model's enum blindly)", got.Confidence)
	}
}

func TestSelectedPassageAnchorsAnOtherwiseAmbiguousQuestion(t *testing.T) {
	selected := ref("John", 1, 14)
	st := twoVerseStore()
	st.text[selected.Key()] = "The Word became flesh and lived among us."
	synth := &fakeSynthesizer{reply: `{"answer":"The Word became flesh.","summary":"Incarnation","citedPassageIndexes":[0],"confidence":"medium","interpretiveVariance":false}`}
	svc := &Service{Store: st, Synthesizer: synth}
	got, err := svc.Ask(WithPassage(context.Background(), selected), "What does this expression mean?")
	if err != nil {
		t.Fatal(err)
	}
	if len(got.PassageReferences) != 1 || got.PassageReferences[0].Key() != selected.Key() {
		t.Fatalf("lost selected verse: %+v", got)
	}
	if !strings.Contains(synth.gotUser, "[0] John 1:14") || !strings.Contains(synth.gotUser, "reader selected the first 1") {
		t.Fatal("selected evidence is not identified")
	}
}

func TestSelectedPassageRequiresRealCorpusEvenWhenOtherEvidenceExists(t *testing.T) {
	synth := &fakeSynthesizer{}
	svc := &Service{Store: twoVerseStore(), Synthesizer: synth}
	got, err := svc.Ask(WithPassage(context.Background(), ref("John", 1, 14)), "What does this mean?")
	if err != nil {
		t.Fatal(err)
	}
	if synth.called || got.Answer != "" {
		t.Fatal("must not substitute unrelated evidence for a missing selected passage")
	}
}

func TestSelectedRangeIsKeptWhenRetrievalLimitIsSmaller(t *testing.T) {
	selected := ref("Job", 1, 21)
	end := 22
	selected.VerseEnd = &end
	st := &fakeStore{text: map[string]string{"Job.1.21": "Verse twenty one", "Job.1.22": "Verse twenty two"}}
	synth := &fakeSynthesizer{reply: `{"answer":"a","summary":"s","citedPassageIndexes":[0,1],"confidence":"medium","interpretiveVariance":false}`}
	svc := &Service{Store: st, Synthesizer: synth, EvidenceLimit: 1}
	got, err := svc.Ask(WithPassage(context.Background(), selected), "Explain this passage")
	if err != nil {
		t.Fatal(err)
	}
	if len(got.PassageReferences) != 2 || !strings.Contains(synth.gotUser, "[1] Job 1:22") {
		t.Fatalf("range truncated: %+v", got)
	}
}
