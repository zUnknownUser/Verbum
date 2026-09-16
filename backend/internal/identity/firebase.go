// Package identity verifies Firebase client ID tokens using the official Admin SDK.
package identity

import (
	"context"
	"errors"
	"os"

	firebase "firebase.google.com/go/v4"
	"verbum/backend/internal/usage"
)

func New(ctx context.Context, projectID string) (func(context.Context, string) (string, error), error) {
	identify, err := NewPrincipal(ctx, projectID)
	if err != nil {
		return nil, err
	}
	return func(ctx context.Context, raw string) (string, error) { p, e := identify(ctx, raw); return p.UID, e }, nil
}
func NewPrincipal(ctx context.Context, projectID string) (func(context.Context, string) (usage.Principal, error), error) {
	if projectID == "" {
		return nil, errors.New("Firebase project ID is required")
	}
	// The emulator accepts unsigned tokens. Never enable it in the API binary.
	if os.Getenv("FIREBASE_AUTH_EMULATOR_HOST") != "" {
		return nil, errors.New("Firebase auth emulator is not supported by the API")
	}
	app, err := firebase.NewApp(ctx, &firebase.Config{ProjectID: projectID})
	if err != nil {
		return nil, err
	}
	client, err := app.Auth(ctx)
	if err != nil {
		return nil, err
	}
	return func(ctx context.Context, raw string) (usage.Principal, error) {
		// Includes signature, issuer, audience, expiry and user revocation/disablement.
		// Requires ADC credentials with Firebase Authentication read permission.
		token, err := client.VerifyIDTokenAndCheckRevoked(ctx, raw)
		if err != nil {
			return usage.Principal{}, errors.New("identity verification failed")
		}
		firebaseClaims, _ := token.Claims["firebase"].(map[string]interface{})
		provider, _ := firebaseClaims["sign_in_provider"].(string)
		return usage.Principal{UID: token.UID, Anonymous: provider == "anonymous" || provider == ""}, nil
	}, nil
}
