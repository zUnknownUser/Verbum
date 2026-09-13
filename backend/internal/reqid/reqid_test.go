package reqid

import (
	"context"
	"testing"
)

func TestFromReturnsPlaceholderWithoutContext(t *testing.T) {
	if got := From(context.Background()); got != "-" {
		t.Errorf("From(no id) = %q, want -", got)
	}
}

func TestNewContextAttachesANonEmptyID(t *testing.T) {
	ctx := NewContext(context.Background())
	got := From(ctx)
	if got == "" || got == "-" {
		t.Errorf("From(NewContext(...)) = %q, want a real id", got)
	}
}

func TestNewContextIDsAreNotConstant(t *testing.T) {
	a := From(NewContext(context.Background()))
	b := From(NewContext(context.Background()))
	if a == b {
		t.Errorf("two NewContext calls produced the same id %q", a)
	}
}
