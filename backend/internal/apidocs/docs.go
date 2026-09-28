// Package apidocs serves the canonical API contract and a self-hosted Swagger UI.
package apidocs

import (
	"bytes"
	"crypto/sha256"
	"embed"
	"fmt"
	"io/fs"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"
)

//go:embed assets
var assets embed.FS

// New snapshots the contract and its JSON examples at startup. Documentation has no
// credentials or provider clients, and browsing it cannot start a paid operation.
// next keeps the application's authentication and cost controls unchanged.
func New(contractDir string, next http.Handler) (http.Handler, error) {
	spec, err := os.ReadFile(filepath.Join(contractDir, "openapi.yaml"))
	if err != nil {
		return nil, fmt.Errorf("read API contract: %w", err)
	}
	mux := http.NewServeMux()
	mux.Handle("/", next)
	mux.HandleFunc("GET /docs", func(w http.ResponseWriter, r *http.Request) {
		http.Redirect(w, r, "/docs/", http.StatusTemporaryRedirect)
	})
	mux.HandleFunc("GET /swagger", func(w http.ResponseWriter, r *http.Request) {
		http.Redirect(w, r, "/docs/", http.StatusTemporaryRedirect)
	})
	mux.Handle("GET /openapi.yaml", content(spec, "application/yaml; charset=utf-8", "no-cache"))
	err = fs.WalkDir(assets, "assets", func(path string, entry fs.DirEntry, err error) error {
		if err != nil || entry.IsDir() {
			return err
		}
		data, err := assets.ReadFile(path)
		if err != nil {
			return err
		}
		url, kind := "/docs/"+path, "text/plain; charset=utf-8"
		switch filepath.Ext(path) {
		case ".html":
			url, kind = "/docs/{$}", "text/html; charset=utf-8"
		case ".css":
			kind = "text/css; charset=utf-8"
		case ".js":
			kind = "text/javascript; charset=utf-8"
		}
		mux.Handle("GET "+url, content(data, kind, "no-cache"))
		return nil
	})
	if err != nil {
		return nil, err
	}
	// Only checked-in JSON examples are published; no directory listing or arbitrary
	// filesystem access is exposed. Relative externalValue links resolve at /examples/.
	examples := os.DirFS(filepath.Join(contractDir, "examples"))
	err = fs.WalkDir(examples, ".", func(path string, entry fs.DirEntry, err error) error {
		if err != nil || entry.IsDir() || !strings.HasSuffix(path, ".json") {
			return err
		}
		data, err := fs.ReadFile(examples, path)
		if err != nil {
			return err
		}
		mux.Handle("GET /examples/"+path, content(data, "application/json; charset=utf-8", "no-cache"))
		return nil
	})
	return mux, err
}

func content(data []byte, kind, cache string) http.Handler {
	etag := fmt.Sprintf(`"%x"`, sha256.Sum256(data))
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", kind)
		w.Header().Set("Cache-Control", cache)
		w.Header().Set("ETag", etag)
		w.Header().Set("X-Content-Type-Options", "nosniff")
		w.Header().Set("Referrer-Policy", "no-referrer")
		w.Header().Set("Content-Security-Policy", "default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; font-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'")
		http.ServeContent(w, r, "", time.Time{}, bytes.NewReader(data))
	})
}
