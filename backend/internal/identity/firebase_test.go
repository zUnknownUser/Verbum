package identity

import (
	"context"
	"testing"
)

func TestRejectsMissingProjectBeforeCredentialDiscovery(t *testing.T) {
	if verify, err := New(context.Background(), ""); err == nil || verify != nil {
		t.Fatal("missing project must never create a verifier")
	}
}

func TestRefusesUnsignedEmulatorTokensInAPIBinary(t *testing.T) {
	t.Setenv("FIREBASE_AUTH_EMULATOR_HOST", "localhost:9099")
	if verify, err := New(context.Background(), "test-project"); err == nil || verify != nil {
		t.Fatal("emulator must never create a verifier")
	}
}
