package main

import (
	"fmt"
	"strings"
)

// A missing database must never silently publish the demonstration catalogue.
// Fixture mode is deliberately restricted to an explicit development invocation.
func contentDatabaseURL(getenv func(string) string) (string, error) {
	mode := getenv("VERBUM_CONTENT_MODE")
	if mode == "" {
		mode = "postgres"
	}
	databaseURL := getenv("VERBUM_DATABASE_URL")
	switch mode {
	case "postgres":
		if strings.TrimSpace(databaseURL) == "" {
			return "", fmt.Errorf("VERBUM_DATABASE_URL is required in postgres mode")
		}
		return databaseURL, nil
	case "fixtures":
		if getenv("VERBUM_ENV") != "development" {
			return "", fmt.Errorf("fixture mode requires VERBUM_ENV=development")
		}
		if databaseURL != "" {
			return "", fmt.Errorf("fixture mode cannot be combined with VERBUM_DATABASE_URL")
		}
		return "", nil
	default:
		return "", fmt.Errorf("VERBUM_CONTENT_MODE must be postgres or fixtures")
	}
}
