package httpapi

import (
	"context"
	"fmt"
	"log/slog"
	"net"
	"net/http"
	"net/netip"
	"strings"
	"sync"
	"time"

	"golang.org/x/time/rate"
	"verbum/backend/internal/realtime"
	"verbum/backend/internal/usage"
)

// VerifyIdentity verifies an SDK ID token, never a client-supplied UID.
type VerifyIdentity func(context.Context, string) (string, error)

type accessContextKey struct{}

// AccessOptions configures the public edge. A nil verifier disables paid operations
// (fail closed); public search still works without a paid query embedding.
type AccessOptions struct {
	Identify       func(context.Context, string) (usage.Principal, error)
	Verify         VerifyIdentity
	TrustedProxies []netip.Prefix
}

type routeBudget struct{ perUser, perIP, global, concurrent int }

var accessBudgets = map[string]routeBudget{
	"GET /v1/realtime/connect":  {3, 10, 30, 4},
	"GET /v1/me/usage":          {30, 60, 300, 8},
	"GET /v1/search":            {60, 120, 600, 16},
	"POST /v1/ask":              {10, 30, 60, 8},
	"POST /v1/tts":              {10, 30, 60, 4},
	"POST /v1/realtime/session": {3, 10, 30, 4},
}

// Protect is installed once by cmd/api. Limits are per process; shared deployments
// must enforce an additional distributed limit at their edge. No tokens or UIDs
// are logged, and idle limit records expire from bounded memory.
func Protect(next http.Handler, options AccessOptions) http.Handler {
	return newAccess(next, options, time.Now)
}

func newAccess(next http.Handler, options AccessOptions, now func() time.Time) http.Handler {
	limits := &accessLimits{entries: make(map[string]*limitEntry), maxEntries: 10000}
	gates := make(map[string]chan struct{})
	for route, b := range accessBudgets {
		gates[route] = make(chan struct{}, b.concurrent)
	}
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		route := r.Method + " " + r.URL.Path
		budget, protected := accessBudgets[route]
		if !protected {
			next.ServeHTTP(w, r)
			return
		}
		ip := clientAddress(r, options.TrustedProxies)
		if !limits.allow(route+"/global", budget.global, now()) || !limits.allow(route+"/ip/"+ip, budget.perIP, now()) {
			rejectAccess(w, route, http.StatusTooManyRequests, CodeRateLimited)
			return
		}
		select {
		case gates[route] <- struct{}{}:
			defer func() { <-gates[route] }()
		default:
			rejectAccess(w, route, http.StatusTooManyRequests, CodeRateLimited)
			return
		}
		if route == "GET /v1/realtime/connect" {
			next.ServeHTTP(w, r)
			return
		}
		isSearch := route == "GET /v1/search"
		principal := usage.Principal{Anonymous: true}
		uid := ""
		if auth := r.Header.Get("Authorization"); auth != "" {
			parts := strings.Fields(auth)
			if len(parts) != 2 || !strings.EqualFold(parts[0], "Bearer") || len(auth) > 8192 || len(r.Header.Values("Authorization")) != 1 {
				rejectAccess(w, route, http.StatusUnauthorized, CodeUnauthenticated)
				return
			}
			if options.Verify == nil && options.Identify == nil {
				rejectAccess(w, route, http.StatusServiceUnavailable, CodeAuthUnavailable)
				return
			}
			ctx, cancel := context.WithTimeout(r.Context(), 5*time.Second)
			var err error
			if options.Identify != nil {
				principal, err = options.Identify(ctx, parts[1])
				uid = principal.UID
			} else {
				uid, err = options.Verify(ctx, parts[1])
				principal.UID = uid
			}
			cancel()
			if err != nil || uid == "" {
				rejectAccess(w, route, http.StatusUnauthorized, CodeUnauthenticated)
				return
			}
		} else if !isSearch {
			if options.Verify == nil && options.Identify == nil {
				rejectAccess(w, route, http.StatusServiceUnavailable, CodeAuthUnavailable)
			} else {
				rejectAccess(w, route, http.StatusUnauthorized, CodeUnauthenticated)
			}
			return
		}
		if uid != "" && !limits.allow(route+"/uid/"+uid, budget.perUser, now()) {
			rejectAccess(w, route, http.StatusTooManyRequests, CodeRateLimited)
			return
		}
		if route == "POST /v1/realtime/session" {
			// A public client cannot select a more expensive arbitrary model.
			for _, model := range r.URL.Query()["model"] {
				if model != "" && model != realtime.DefaultModel {
					rejectAccess(w, route, http.StatusBadRequest, CodeMalformedRequest)
					return
				}
			}
		}
		if isSearch {
			// Search contains no private data, but authenticated semantic results must
			// not be shared with an unauthenticated lexical request by a proxy.
			w.Header().Add("Vary", "Authorization")
			r = r.WithContext(context.WithValue(r.Context(), accessContextKey{}, uid == ""))
		}
		principal.IP = ip
		device := r.Header.Get("X-Verbum-Installation")
		if len(device) >= 16 && len(device) <= 80 {
			principal.Device = device
		}
		r = r.WithContext(usage.WithPrincipal(r.Context(), principal))
		next.ServeHTTP(w, r)
	})
}

func rejectAccess(w http.ResponseWriter, route string, status int, code string) {
	w.Header().Set("Cache-Control", "no-store")
	if status == http.StatusTooManyRequests {
		w.Header().Set("Retry-After", "60")
	}
	if status == http.StatusUnauthorized {
		w.Header().Set("WWW-Authenticate", "Bearer")
	}
	slog.Warn("request rejected", "route", route, "status", status, "code", code)
	writeProblem(w, status, code, http.StatusText(status))
}

type limitEntry struct {
	limiter *rate.Limiter
	seen    time.Time
}
type accessLimits struct {
	mu         sync.Mutex
	entries    map[string]*limitEntry
	lastSweep  time.Time
	maxEntries int
}

func (l *accessLimits) allow(key string, perMinute int, now time.Time) bool {
	l.mu.Lock()
	defer l.mu.Unlock()
	if now.Sub(l.lastSweep) >= time.Minute {
		for key, e := range l.entries {
			if now.Sub(e.seen) >= time.Hour {
				delete(l.entries, key)
			}
		}
		l.lastSweep = now
	}
	e := l.entries[key]
	if e == nil {
		// Never evict an active record and accidentally reset its quota.
		if len(l.entries) >= l.maxEntries {
			return false
		}
		e = &limitEntry{limiter: rate.NewLimiter(rate.Limit(float64(perMinute)/60), perMinute)}
		l.entries[key] = e
	}
	e.seen = now
	return e.limiter.AllowN(now, 1)
}

func clientAddress(r *http.Request, trusted []netip.Prefix) string {
	host, _, err := net.SplitHostPort(r.RemoteAddr)
	if err != nil {
		host = r.RemoteAddr
	}
	addr, err := netip.ParseAddr(host)
	if err != nil {
		return "unknown"
	}
	addr = addr.Unmap()
	isTrusted := func(a netip.Addr) bool {
		for _, prefix := range trusted {
			if prefix.Contains(a) {
				return true
			}
		}
		return false
	}
	if isTrusted(addr) {
		// Walk from the peer towards the client; never trust the leftmost header
		// a caller can prepend. Malformed/oversized chains retain the socket peer.
		forwarded := strings.Join(r.Header.Values("X-Forwarded-For"), ",")
		parts := strings.Split(forwarded, ",")
		if len(forwarded) <= 2048 && len(parts) <= 32 {
			candidate := addr
			for i := len(parts) - 1; i >= 0 && isTrusted(candidate); i-- {
				parsed, err := netip.ParseAddr(strings.TrimSpace(parts[i]))
				if err != nil {
					candidate = addr
					break
				}
				candidate = parsed.Unmap()
			}
			addr = candidate
		}
	}
	if addr.Is6() {
		return netip.PrefixFrom(addr, 64).Masked().String()
	}
	return addr.String()
}

// ParseTrustedProxies is intentionally explicit: do not trust forwarding headers
// just because the application happens to be deployed behind a reverse proxy.
func ParseTrustedProxies(value string) ([]netip.Prefix, error) {
	var prefixes []netip.Prefix
	if strings.TrimSpace(value) == "" {
		return prefixes, nil
	}
	for _, item := range strings.Split(value, ",") {
		prefix, err := netip.ParsePrefix(strings.TrimSpace(item))
		if err != nil {
			return nil, err
		}
		if prefix.Bits() == 0 {
			return nil, fmt.Errorf("cannot trust all addresses")
		}
		prefixes = append(prefixes, prefix.Masked())
	}
	return prefixes, nil
}
