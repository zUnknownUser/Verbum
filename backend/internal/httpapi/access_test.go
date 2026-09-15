package httpapi

import (
	"context"
	"errors"
	"fmt"
	"net/http"
	"net/http/httptest"
	"net/netip"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"verbum/backend/internal/store/memory"
)

func validIdentity(_ context.Context, token string) (string, error) {
	if strings.HasPrefix(token, "valid-") {
		return strings.TrimPrefix(token, "valid-"), nil
	}
	return "", errors.New("invalid or revoked")
}
func accessRequest(h http.Handler, method, path, token, addr string) *httptest.ResponseRecorder {
	r := httptest.NewRequest(method, path, nil)
	r.RemoteAddr = addr
	if token != "" {
		r.Header.Set("Authorization", "Bearer "+token)
	}
	w := httptest.NewRecorder()
	h.ServeHTTP(w, r)
	return w
}

func TestPaidAccessFailsClosed(t *testing.T) {
	for route := range accessBudgets {
		if strings.HasPrefix(route, "GET") {
			continue
		}
		for _, test := range []struct {
			name, token string
			verify      VerifyIdentity
			status      int
		}{
			{"missing configuration", "", nil, 503},
			{"missing token", "", validIdentity, 401},
			{"invalid token", "tampered", validIdentity, 401},
			{"valid token", "valid-guest", validIdentity, 204},
		} {
			t.Run(route+"/"+test.name, func(t *testing.T) {
				called := false
				h := Protect(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) { called = true; w.WriteHeader(204) }), AccessOptions{Verify: test.verify})
				parts := strings.SplitN(route, " ", 2)
				w := accessRequest(h, parts[0], parts[1], test.token, "192.0.2.1:1000")
				if w.Code != test.status || called != (test.status == 204) {
					t.Fatalf("status=%d provider called=%v", w.Code, called)
				}
				if test.status != 204 && w.Header().Get("Cache-Control") != "no-store" {
					t.Fatal("rejection is cacheable")
				}
			})
		}
	}
}

func TestPublicContentNeverVerifiesIdentity(t *testing.T) {
	h := Protect(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) { w.WriteHeader(204) }), AccessOptions{Verify: func(context.Context, string) (string, error) { t.Fatal("public route verified token"); return "", nil }})
	for _, path := range []string{"/healthz", "/v1/entities", "/v1/tts/config?language=pt-BR", "/v1/passages/John.3/context"} {
		if w := accessRequest(h, "GET", path, "", "192.0.2.1:1000"); w.Code != 204 {
			t.Fatalf("%s: %d", path, w.Code)
		}
	}
}

func TestSearchOnlyPaysForVerifiedIdentity(t *testing.T) {
	base, err := memory.Load("../../db/seed/fixtures.json")
	if err != nil {
		t.Fatal(err)
	}
	rec := &recordingStore{Store: base}
	h := Protect(New(rec, time.Now, nil, &fakeEmbedder{vector: []float32{1}}, nil, nil), AccessOptions{Verify: validIdentity})
	for _, token := range []string{"", "valid-guest"} {
		w := accessRequest(h, "GET", "/v1/search?q=David", token, "192.0.2.1:1000")
		if w.Code != 200 || (len(rec.gotEmbedding) > 0) != (token != "") {
			t.Fatalf("status=%d embedding=%v", w.Code, rec.gotEmbedding)
		}
		if w.Header().Get("Vary") != "Authorization" {
			t.Fatal("search cache must vary by authorization")
		}
	}
}

func TestUIDBudgetSurvivesChangingIPAndRecovers(t *testing.T) {
	now := time.Now()
	h := newAccess(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) { w.WriteHeader(204) }), AccessOptions{Verify: validIdentity}, func() time.Time { return now })
	for i := 0; i < 11; i++ {
		w := accessRequest(h, "POST", "/v1/ask", "valid-user", fmt.Sprintf("192.0.2.%d:1000", i+1))
		want := 204
		if i == 10 {
			want = 429
		}
		if w.Code != want {
			t.Fatalf("request %d: %d", i, w.Code)
		}
		if want == 429 && w.Header().Get("Retry-After") == "" {
			t.Fatal("missing retry delay")
		}
	}
	now = now.Add(time.Minute)
	if w := accessRequest(h, "POST", "/v1/ask", "valid-user", "192.0.2.1:1000"); w.Code != 204 {
		t.Fatal("quota did not recover")
	}
}

func TestGlobalBudgetSurvivesNewUsersAndIPs(t *testing.T) {
	now := time.Now()
	h := newAccess(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) { w.WriteHeader(204) }), AccessOptions{Verify: validIdentity}, func() time.Time { return now })
	for i := 0; i < 31; i++ {
		w := accessRequest(h, "POST", "/v1/realtime/session", fmt.Sprintf("valid-user%d", i), fmt.Sprintf("192.0.2.%d:1000", i+1))
		want := 204
		if i == 30 {
			want = 429
		}
		if w.Code != want {
			t.Fatalf("request %d: %d", i, w.Code)
		}
	}
}

func TestIPBudgetIgnoresSpoofedForwardingHeaders(t *testing.T) {
	h := Protect(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) { w.WriteHeader(204) }), AccessOptions{Verify: validIdentity})
	for i := 0; i < 11; i++ {
		r := httptest.NewRequest("POST", "/v1/realtime/session", nil)
		r.RemoteAddr = "192.0.2.1:1000"
		r.Header.Set("X-Forwarded-For", fmt.Sprintf("203.0.113.%d", i+1))
		r.Header.Set("Authorization", fmt.Sprintf("Bearer valid-user%d", i))
		w := httptest.NewRecorder()
		h.ServeHTTP(w, r)
		want := 204
		if i == 10 {
			want = 429
		}
		if w.Code != want {
			t.Fatalf("request %d: %d", i, w.Code)
		}
	}
}

func TestConcurrencyBoundAndRelease(t *testing.T) {
	entered := make(chan struct{}, 4)
	release := make(chan struct{})
	h := Protect(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) { entered <- struct{}{}; <-release; w.WriteHeader(204) }), AccessOptions{Verify: validIdentity})
	var wg sync.WaitGroup
	for i := 0; i < 4; i++ {
		wg.Add(1)
		go func(i int) {
			defer wg.Done()
			accessRequest(h, "POST", "/v1/tts", fmt.Sprintf("valid-%d", i), "192.0.2.1:1000")
		}(i)
	}
	for i := 0; i < 4; i++ {
		<-entered
	}
	w := accessRequest(h, "POST", "/v1/tts", "valid-extra", "192.0.2.1:1000")
	close(release)
	wg.Wait()
	if w.Code != 429 {
		t.Fatalf("concurrent overflow: %d", w.Code)
	}
	if w = accessRequest(h, "POST", "/v1/tts", "valid-extra", "192.0.2.1:1000"); w.Code != 204 {
		t.Fatal("slot leaked")
	}
}

func TestModelSelectionBlockedBeforeBroker(t *testing.T) {
	var calls atomic.Int32
	h := Protect(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) { calls.Add(1); w.WriteHeader(204) }), AccessOptions{Verify: validIdentity})
	w := accessRequest(h, "POST", "/v1/realtime/session?model=expensive", "valid-guest", "192.0.2.1:1000")
	if w.Code != 400 || calls.Load() != 0 {
		t.Fatal("arbitrary model reached broker")
	}
}

func TestClientAddressTrustAndIPv6(t *testing.T) {
	proxies := []netip.Prefix{netip.MustParsePrefix("10.0.0.0/24")}
	for _, test := range []struct{ remote, header, want string }{
		{"10.0.0.1:80", "203.0.113.99, 192.0.2.5, 10.0.0.2", "192.0.2.5"},
		{"192.0.2.1:80", "203.0.113.99", "192.0.2.1"},
		{"10.0.0.1:80", "bad", "10.0.0.1"},
		{"[2001:db8::1]:80", "", "2001:db8::/64"},
	} {
		r := httptest.NewRequest("POST", "/v1/ask", nil)
		r.RemoteAddr = test.remote
		r.Header.Set("X-Forwarded-For", test.header)
		if got := clientAddress(r, proxies); got != test.want {
			t.Fatalf("got=%s want=%s", got, test.want)
		}
	}
	for _, invalid := range []string{"0.0.0.0/0", "::/0", "not-a-network"} {
		if _, err := ParseTrustedProxies(invalid); err == nil {
			t.Fatalf("accepted %s", invalid)
		}
	}
}

func TestLimitStorageIsBoundedWithoutResettingActiveQuotas(t *testing.T) {
	l := &accessLimits{entries: map[string]*limitEntry{}, maxEntries: 2}
	now := time.Now()
	if !l.allow("a", 1, now) || !l.allow("b", 1, now) || l.allow("c", 1, now) || l.allow("a", 1, now) {
		t.Fatal("capacity/quota failure")
	}
	if !l.allow("c", 1, now.Add(time.Hour)) || len(l.entries) != 1 {
		t.Fatal("idle records not expired")
	}
}
