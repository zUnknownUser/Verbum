# Paid API access — 2026-09-15

> Atualização de 16/09/2026: [COST_CONTROL.md](../docs/COST_CONTROL.md) define as cotas persistentes, planos, texto TTS canônico obrigatório e voz via relay. Exemplos antigos de texto livre não são aceitos pelo backend protegido.

## Behavior

The app creates a Firebase anonymous identity on the first Ask, voice-session or
cloud-reading request if there is no existing user. Registered sessions are reused.
Firebase owns token refresh and persistence; the API client attaches the current
ID token as `Authorization: Bearer ...`. Tokens are not put in request URLs or
application cache files. Reading, entities, graphs, context, timeline, the daily
verse and the speech-version manifest remain public and do not create identities.

Search without a token uses direct references, entities and lexical Scripture
retrieval. Search with a verified existing identity also enables query embeddings.
Typing does not create an anonymous account. The mobile search caches distinguish
lexical and authenticated results; the HTTP response varies by Authorization.

The Go access layer verifies tokens with Firebase Admin SDK 4.19.0, including
signature, project audience/issuer, expiry and revoked/disabled/deleted users.
The SDK is the added dependency for this security boundary; no custom JWT
verification, authentication in views, or Firebase dependencies in Android's pure
JVM clients/models were introduced. Revocation checking makes a Firebase user
lookup per authenticated request, bounded by a five-second context.

The API rejects authentication-emulator configuration because that mode accepts
unsigned tokens. Test doubles exercise the access layer locally without real
accounts or paid provider calls.

Reference: [Firebase ID-token verification](https://firebase.google.com/docs/auth/admin/verify-id-tokens).

## Limits

Initial token-bucket defaults, per API process. Each bucket starts with the listed
capacity and refills continuously at that many requests per minute. These values
are abuse controls, not subscription entitlements or a monetary spending guarantee.

| Route | Per UID | Per IP | Whole process | Concurrent requests |
| --- | ---: | ---: | ---: | ---: |
| GET /v1/search | 60 | 120 | 600 | 16 |
| POST /v1/ask | 10 | 30 | 60 | 8 |
| POST /v1/tts | 10 | 30 | 60 | 4 |
| POST /v1/realtime/session | 3 | 10 | 30 | 4 |

IP/process limits apply before verification, so invalid credentials also consume
capacity. A UID limit survives changes of IP; process limits survive creation of
new anonymous accounts. Overload rejects immediately rather than creating an
unbounded queue. Limit records expire after an idle hour and have a hard 10,000-entry
cap; reaching the cap rejects new entries without resetting active users' quotas.
IPv6 addresses share a /64 budget. Denials log route/status/code, never token, UID,
question or source text. `rate_limited` is distinct from provider `tts_rate_limited`.

Limits reset on restart and multiply with replicas. Before adding replicas, supply
shared limits at the edge or a shared store. Realtime limits count issued credentials,
not duration/usage after the app connects to the provider. Provider budget alerts,
distributed quotas and app attestation remain separate operational improvements.

## Server configuration and deployment

1. Enable Firebase Anonymous authentication in the apps' existing Firebase project.
2. Set `VERBUM_FIREBASE_PROJECT_ID` to that project's ID.
3. Configure ADC through `GOOGLE_APPLICATION_CREDENTIALS` or the existing
   `GOOGLE_APPLICATION_CREDENTIALS_JSON` materialization. The service account needs
   Firebase Authentication user-read permission (`firebaseauth.users.get`) in the
   configured project. If the existing TTS account has only speech permissions,
   grant the required access through the project's normal IAM administration.
4. Leave `FIREBASE_AUTH_EMULATOR_HOST` unset. The API refuses to initialize with it.
5. Deploy/test the new mobile clients and configure the backend before enforcing
   this release for users. Old clients do not attach tokens and receive 401 on paid
   POSTs. There is deliberately no header/secret-based bypass for legacy clients.

Missing `VERBUM_FIREBASE_PROJECT_ID` disables paid POSTs with 503 `auth_unavailable`;
public, unauthenticated search stays lexical. Invalid Firebase initialization fails
startup. Verification failures deny the request, never invoke a paid provider.
Verify real project permissions before rollout: local fake-token tests do not
establish that IAM or Anonymous authentication are configured in production.

`VERBUM_TRUSTED_PROXIES` optionally accepts comma-separated IP CIDRs for reverse
proxies that append/sanitize `X-Forwarded-For`. Default: trust no forwarding headers,
use the connection peer. All-address prefixes are rejected. The chain is walked
right-to-left until the first untrusted address. Do not set this from a guessed
private range: establish the ingress topology first. Until then, traffic behind a
proxy shares that proxy IP's budget, which is conservative but can limit users early.
Production traffic must use HTTPS; existing debug LAN HTTP overrides remain for
controlled local development only.

Realtime accepts only an omitted/empty model or `gpt-realtime`. Other model selections
return 400 before the broker. Ask requires JSON, at most 8 KiB, one object and no
unknown fields. Its question limit is 500 Unicode code points, aligned with OpenAPI;
search allows 200. Ask's write deadline now covers its existing synthesis timeout.

## Response contract

- 401 `unauthenticated`: missing or unverifiable identity; `WWW-Authenticate: Bearer`.
- 503 `auth_unavailable`: Firebase verification is not configured.
- 429 `rate_limited`: UID/IP/process/concurrency or limiter-capacity limit;
  `Retry-After: 60` and `Cache-Control: no-store`.
- 400 `malformed_request`: invalid body or prohibited Realtime model.
- 415 `malformed_request`: Ask body is not application/json.

Existing safe mobile error states remain in use. No automatic replay of a paid
request is introduced. Audio keeps its existing explicitly labeled recording fallback.

## Verification

```sh
cd backend
go test ./...
go test -race ./internal/httpapi
go vet ./...
```

The access tests verify rejection before invoking providers, valid guest access,
public-route independence, search embedding gating, per-UID/IP/process budgets,
spoofed forwarding headers, concurrency release, IPv6 grouping and bounded memory.
Ask tests cover Unicode limits, extra JSON, unknown fields and body limits.

Mobile regression sources cover token headers on JSON/audio, no account creation
for public browsing, distinct search caches and no paid transport on credential
failure. Build results are recorded in ROADMAP.md; mobile test execution and real
account/device QA remain with the owner, as previously requested.

Manual acceptance on a configured environment: fresh installation → open reader
(no Firebase identity) → Ask (anonymous identity created, answer returned) → audio
and voice (same UID) → register guest (same UID) → delete/disable the disposable
account (old token denied). Verify public reading still works after sign-out.
For a direct TTS script test, supply a current SDK ID token in the process-only
`VERBUM_FIREBASE_ID_TOKEN` variable; Test-TTS.ps1 passes it as a header. Never put it
in a committed file or terminal output. No real account, token or paid operation
was created as part of this implementation.


## Railway rollout configuration — 2026-09-15

Verified both mobile configurations target Firebase project `verbum-sw`. Anonymous sign-in was
already enabled. Set `VERBUM_FIREBASE_PROJECT_ID=verbum-sw` on Railway's api service. The existing
backend ADC service account (`verbum-tts@verbum-app1.iam.gserviceaccount.com`) received
`roles/firebaseauth.viewer` on `verbum-sw`, enabling revoked/disabled-user checks without user
mutation permissions. No service-account key was added to the repository and no intermediate
deploy was triggered by the variable change. Updated mobile binaries are required for paid POSTs.
Enabled `identitytoolkit.googleapis.com` in the ADC service account's project (`verbum-app1`),
which is distinct from the mobile Firebase project. Verified Firebase configuration and user-read
access using that service account; this configuration check did not create user accounts or call paid APIs.

After STEP publication, a temporary anonymous Firebase identity was used for one real Portuguese
Ask request and one short TTS request. Both returned HTTP 200; Ask included verified passage
references and STEP attribution. The temporary account was deleted immediately afterwards.
Tokens and credentials were not written to the repository or test reports. Device QA remains separate.
