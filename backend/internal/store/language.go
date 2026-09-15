package store

import (
	"context"
	"strings"
)

type languageKey struct{}

// WithLanguage carries presentation language through the existing store boundary.
// Source language, Unicode forms and external IDs never depend on this value.
func WithLanguage(ctx context.Context, language string) context.Context {
	lang := "en"
	switch strings.ToLower(strings.TrimSpace(language)) {
	case "pt", "pt-br":
		lang = "pt-BR"
	}
	return context.WithValue(ctx, languageKey{}, lang)
}

func Language(ctx context.Context) string {
	if lang, ok := ctx.Value(languageKey{}).(string); ok {
		return lang
	}
	return "en"
}
