package apidocs

import (
	"bufio"
	"go/ast"
	"go/parser"
	"go/token"
	"net/http"
	"net/http/httptest"
	"os"
	"regexp"
	"strconv"
	"strings"
	"testing"
)

func TestDocumentationAndExamplesAreSelfHosted(t *testing.T) {
	h, err := New("../../../api", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/v1/protected" {
			w.WriteHeader(http.StatusUnauthorized)
			return
		}
		http.NotFound(w, r)
	}))
	if err != nil {
		t.Fatal(err)
	}
	for _, path := range []string{"/docs/", "/openapi.yaml", "/docs/assets/swagger-ui-bundle.js", "/docs/assets/swagger-ui.css", "/examples/tts/request-pt-BR.json"} {
		r := httptest.NewRecorder()
		h.ServeHTTP(r, httptest.NewRequest("GET", path, nil))
		if r.Code != 200 || r.Body.Len() == 0 || r.Header().Get("ETag") == "" || r.Header().Get("X-Content-Type-Options") != "nosniff" {
			t.Fatalf("%s: %d %v", path, r.Code, r.Header())
		}
		if !strings.Contains(r.Header().Get("Content-Security-Policy"), "connect-src 'self'") {
			t.Fatal("documentation can send tokens to another origin")
		}
		conditional := httptest.NewRequest("GET", path, nil)
		conditional.Header.Set("If-None-Match", r.Header().Get("ETag"))
		cached := httptest.NewRecorder()
		h.ServeHTTP(cached, conditional)
		if cached.Code != 304 {
			t.Fatal("unchanged asset should revalidate", path, cached.Code)
		}
	}
	for _, path := range []string{"/docs", "/swagger"} {
		r := httptest.NewRecorder()
		h.ServeHTTP(r, httptest.NewRequest("GET", path, nil))
		if r.Code != 307 || r.Header().Get("Location") != "/docs/" {
			t.Fatal(path, r.Code)
		}
	}
	for path, status := range map[string]int{"/examples/": 404, "/docs/assets/nonexistent": 404, "/v1/protected": 401} {
		r := httptest.NewRecorder()
		h.ServeHTTP(r, httptest.NewRequest("GET", path, nil))
		if r.Code != status {
			t.Fatal(path, r.Code)
		}
	}
}

func TestMissingContractPreventsIncompleteDocumentation(t *testing.T) {
	if _, err := New(t.TempDir(), http.NotFoundHandler()); err == nil {
		t.Fatal("missing contract was silently accepted")
	}
}

// The contract uses block-form path/method keys. Compare its inventory to the
// actual mux registrations, including optional metered routes, so drift fails CI.
// OpenAPI schema validation is also run with Swagger Parser before publication.
func TestContractCoversEveryRegisteredAPIRoute(t *testing.T) {
	file, err := parser.ParseFile(token.NewFileSet(), "../httpapi/server.go", nil, 0)
	if err != nil {
		t.Fatal(err)
	}
	routes := map[string]bool{}
	ast.Inspect(file, func(node ast.Node) bool {
		call, ok := node.(*ast.CallExpr)
		if !ok || len(call.Args) < 1 {
			return true
		}
		method, ok := call.Fun.(*ast.SelectorExpr)
		if !ok || (method.Sel.Name != "Handle" && method.Sel.Name != "HandleFunc") {
			return true
		}
		literal, ok := call.Args[0].(*ast.BasicLit)
		if ok && literal.Kind == token.STRING {
			route, _ := strconv.Unquote(literal.Value)
			routes[route] = true
		}
		return true
	})
	spec, err := os.Open("../../../api/openapi.yaml")
	if err != nil {
		t.Fatal(err)
	}
	defer spec.Close()
	pathPattern := regexp.MustCompile(`^  (/[^ ]+):$`)
	methodPattern := regexp.MustCompile(`^    (get|post|put|patch|delete|head|options):$`)
	path := ""
	count := 0
	scanner := bufio.NewScanner(spec)
	for scanner.Scan() {
		if matches := pathPattern.FindStringSubmatch(scanner.Text()); matches != nil {
			path = matches[1]
		}
		if matches := methodPattern.FindStringSubmatch(scanner.Text()); matches != nil {
			route := strings.ToUpper(matches[1]) + " " + path
			if !routes[route] {
				t.Errorf("documented route is not registered: %s", route)
			}
			delete(routes, route)
			count++
		}
	}
	if err := scanner.Err(); err != nil {
		t.Fatal(err)
	}
	for route := range routes {
		t.Errorf("registered route missing from contract: %s", route)
	}
	if count == 0 {
		t.Fatal("empty route inventory")
	}
}
