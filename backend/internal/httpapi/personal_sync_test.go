package httpapi

import (
	"context"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
	"verbum/backend/internal/personalsync"
	"verbum/backend/internal/usage"
)

type syncFake struct {
	uid   string
	calls int
}

func (s *syncFake) Exchange(_ context.Context, uid string, _ personalsync.Request) (personalsync.Response, error) {
	s.uid = uid
	s.calls++
	return personalsync.Response{Records: []personalsync.Record{}, Accepted: []personalsync.Record{}}, nil
}
func (s *syncFake) Delete(_ context.Context, uid string) error { s.uid = uid; s.calls++; return nil }
func TestPersonalDataRequiresVerifiedRegisteredAccount(t *testing.T) {
	for _, route := range []string{"POST /v1/me/sync", "DELETE /v1/me/data"} {
		for _, token := range []string{"", "invalid", "guest", "registered"} {
			storage := &syncFake{}
			handler := New(nil, time.Now, nil, nil, nil, nil, EconomicOptions{PersonalData: storage})
			handler = Protect(handler, AccessOptions{Identify: func(_ context.Context, raw string) (usage.Principal, error) {
				if raw == "invalid" {
					return usage.Principal{}, context.Canceled
				}
				return usage.Principal{AuthenticatedAt: time.Now().Unix(), UID: "verified-uid", Anonymous: raw == "guest"}, nil
			}})
			parts := strings.Split(route, " ")
			r := httptest.NewRequest(parts[0], parts[1], strings.NewReader(`{"cursor":0,"changes":[]}`))
			if token != "" {
				r.Header.Set("Authorization", "Bearer "+token)
			}
			w := httptest.NewRecorder()
			handler.ServeHTTP(w, r)
			allowed := token == "registered" || (token == "guest" && parts[0] == "DELETE")
			if allowed {
				if w.Code >= 300 || storage.uid != "verified-uid" {
					t.Fatalf("%s %s: %d", route, token, w.Code)
				}
			} else if storage.calls != 0 || w.Code < 400 {
				t.Fatal("unauthorized storage access")
			}
			if w.Header().Get("Cache-Control") != "no-store" {
				t.Fatal("private response cacheable")
			}
		}
	}
}

func TestDeletePersonalDataRequiresRecentAuthentication(t *testing.T) {
	now := time.Now()
	storage := &syncFake{}
	handler := New(nil, func() time.Time { return now }, nil, nil, nil, nil, EconomicOptions{PersonalData: storage})
	handler = Protect(handler, AccessOptions{Identify: func(context.Context, string) (usage.Principal, error) {
		return usage.Principal{UID: "registered", AuthenticatedAt: now.Add(-6 * time.Minute).Unix()}, nil
	}})
	r := httptest.NewRequest("DELETE", "/v1/me/data", nil)
	r.Header.Set("Authorization", "Bearer valid-but-old-login")
	w := httptest.NewRecorder()
	handler.ServeHTTP(w, r)
	if w.Code != 403 || storage.calls != 0 || !strings.Contains(w.Body.String(), "recent_login_required") {
		t.Fatal("deletion allowed without reauthentication", w.Code)
	}
}
