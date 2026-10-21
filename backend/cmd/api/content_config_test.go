package main

import (
	"strings"
	"testing"
)

func TestContentConfiguration(t *testing.T) {
	const databaseURL = "postgres://test:private-password@localhost/verbum"
	for _, tc := range []struct {
		name    string
		mode    string
		env     string
		url     string
		wantURL string
		wantErr bool
	}{
		{"missing configuration", "", "", "", "", true},
		{"missing production database", "", "production", "", "", true},
		{"blank database", "", "production", "   ", "", true},
		{"default postgres", "", "", databaseURL, databaseURL, false},
		{"explicit postgres", "postgres", "production", databaseURL, databaseURL, false},
		{"development does not implicitly enable fixtures", "", "development", "", "", true},
		{"explicit development fixtures", "fixtures", "development", "", "", false},
		{"fixtures without environment", "fixtures", "", "", "", true},
		{"production fixtures rejected", "fixtures", "production", "", "", true},
		{"fixture database conflict", "fixtures", "development", databaseURL, "", true},
		{"unknown content mode", "postgress", "production", databaseURL, "", true},
	} {
		t.Run(tc.name, func(t *testing.T) {
			values := map[string]string{
				"VERBUM_CONTENT_MODE": tc.mode,
				"VERBUM_ENV":          tc.env,
				"VERBUM_DATABASE_URL": tc.url,
			}
			got, err := contentDatabaseURL(func(key string) string { return values[key] })
			if (err != nil) != tc.wantErr || got != tc.wantURL {
				t.Fatalf("unexpected content configuration result: error=%v", err)
			}
			if err != nil && strings.Contains(err.Error(), "private-password") {
				t.Fatal("configuration error exposed credentials")
			}
		})
	}
}
