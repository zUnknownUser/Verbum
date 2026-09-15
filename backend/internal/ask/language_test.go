package ask

import (
	"context"
	"strings"
	"testing"
	"verbum/backend/internal/store"
)

func TestPortugueseNoEvidenceAndPrompt(t *testing.T) {
	ctx := store.WithLanguage(context.Background(), "pt-BR")
	synth := &fakeSynthesizer{}
	service := Service{Store: &fakeStore{}, Synthesizer: synth}
	answer, err := service.Ask(ctx, "Pergunta sem evidência")
	if err != nil || !strings.Contains(answer.Summary, "Não foram encontradas") || synth.called {
		t.Fatalf("answer=%+v err=%v", answer, err)
	}
	if !strings.Contains(localizedSystemPrompt("pt-BR"), "Brazilian Portuguese") || !strings.Contains(localizedSystemPrompt("en"), "in English") {
		t.Fatal("language prompt missing")
	}
	if !needsProfessionalHelpNote("Estou sem esperança e deprimido") || !mentionsProfessionalHelp("Procure um psicólogo") {
		t.Fatal("Portuguese support note detection failed")
	}
}
