package store

import (
	"context"
	"golang.org/x/text/language"
	"strings"
)

type languageKey struct{}

// WithLanguage carries presentation language through the existing store boundary.
// Source language, Unicode forms and external IDs never depend on this value.
func WithLanguage(ctx context.Context, requested string) context.Context {
	lang := "en"
	tag, err := language.Parse(strings.ReplaceAll(strings.TrimSpace(requested), "_", "-"))
	if err == nil {
		base, _ := tag.Base()
		region, _ := tag.Region()
		if base.String() == "pt" || region.String() == "BR" {
			lang = "pt-BR"
		}
	}
	return context.WithValue(ctx, languageKey{}, lang)
}

func Language(ctx context.Context) string {
	if lang, ok := ctx.Value(languageKey{}).(string); ok {
		return lang
	}
	return "en"
}
