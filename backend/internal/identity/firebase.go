// Package identity verifies Firebase client ID tokens using the official Admin SDK.
package identity

import (
	"context"
	"errors"
	"os"

	firebase "firebase.google.com/go/v4"
)

func New(ctx context.Context, projectID string) (func(context.Context, string) (string, error), error) {
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
	return func(ctx context.Context, raw string) (string, error) {
		// Includes signature, issuer, audience, expiry and user revocation/disablement.
		// Requires ADC credentials with Firebase Authentication read permission.
		token, err := client.VerifyIDTokenAndCheckRevoked(ctx, raw)
		if err != nil {
			return "", errors.New("identity verification failed")
		}
		return token.UID, nil
	}, nil
}
