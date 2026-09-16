package identity

import (
	"context"
	"errors"
	firebase "firebase.google.com/go/v4"
)

// Firebase verifies signature, expiry and project audience. The explicit app allowlist
// prevents another registered application in the project from obtaining access.
func NewAppCheck(ctx context.Context, project string, allowed []string) (func(string) error, error) {
	if project == "" || len(allowed) == 0 {
		return nil, errors.New("App Check project and app IDs required")
	}
	app, err := firebase.NewApp(ctx, &firebase.Config{ProjectID: project})
	if err != nil {
		return nil, err
	}
	client, err := app.AppCheck(ctx)
	if err != nil {
		return nil, err
	}
	return func(raw string) error {
		token, err := client.VerifyToken(raw)
		if err != nil {
			return errors.New("invalid app attestation")
		}
		for _, id := range allowed {
			if token.AppID == id {
				return nil
			}
		}
		return errors.New("unapproved app")
	}, nil
}
