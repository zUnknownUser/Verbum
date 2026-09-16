# Account integration — delivery 2026-09-13

> Planos e cotas persistentes: [COST_CONTROL.md](COST_CONTROL.md). Premium vem de `usage_entitlements` no backend; nenhuma flag do cliente concede acesso. Voz usa ticket de uso único para o relay Verbum.

## Update — paid API identity, 2026-09-15

Ask, voice-session creation and cloud audio now create a Firebase anonymous identity
on first use if needed, with the owner's approval. Existing users are reused;
ordinary reading/exploration does not create an identity. Both HTTP clients attach
SDK ID tokens; the backend verifies them including revocation and applies UID/IP/
process limits. Registration still links guest credentials to the existing UID.
No cloud sync or user-data storage was added. Configuration and rollout requirements:
[backend/SECURITY.md](../backend/SECURITY.md). The remaining sections describe the
original account delivery; their statement that backend authorization is unimplemented
is superseded by this update. Real-account/device validation is still pending.

Additive implementation report for PRODUCT.md §48. PRODUCT.md and DESIGN_SYSTEM.md are unchanged.

## Entry and scope

Home → account icon beside the greeting. Accounts are optional; closing the account sheet or
choosing “Not now” keeps Scripture accessible without a Firebase request. “Continue as guest”
is different: it creates a real anonymous Firebase identity and needs a connection.

Both apps support email/password sign-in, create account, recovery, guest access, session
restoration, verification email, refresh verification, sign-out and account deletion.
Copy is localized in English and pt-BR. No Google/Apple sign-in is advertised by this block.

Creating an account while anonymous links credentials to that UID. Signing into an existing
account switches identities; it does not merge guest data. Authentication is not cloud sync:
notes, saved passages, journey data, subscriptions and backend authorization are not implemented
by this block. Account deletion removes the Firebase identity, not local reading preferences;
the confirmation says this explicitly. Server-side user-data deletion must be added before
personal data is synchronized to a backend.

## Configuration required before device QA

1. In the Firebase project represented by the supplied files, enable **Authentication → Sign-in
   method → Email/Password** and **Anonymous**. Configuration files alone do not enable providers.
2. iOS Debug/Release now use `com.nexussoft.verbum`, matching the supplied plist, after explicit
   owner approval. The previous `com.NexusSoft.Verbum` app may be treated as a separate installation;
   provisioning for a physical device must match the new ID. Bootstrap still rejects mismatched
   configuration safely if a wrong plist is supplied in a future build.
3. Android application ID matches `com.nexussoft.verbum`. Its Firebase file is now
   `android/app/google-services.json`; iOS uses `Verbum/GoogleService-Info.plist` as an app resource.
4. Review Firebase email templates/sender identity and password policy. The mobile baseline for
   new passwords is eight Unicode scalar values, with confirmation; Firebase remains authoritative.
   Sign-in does not impose this new minimum on existing passwords. Recovery uses neutral copy
   and Firebase-hosted reset links; verification is refreshed explicitly after returning to the app.

No live accounts were created and no real verification/recovery messages were sent during development.

## Architecture and performance

- `AuthSession` and validation: pure models, no SDK objects in feature state.
- `AccountClient`: the sole feature-facing boundary. Firebase persists credentials; the app never
  writes passwords to preferences, saved-state bundles, logs or disk.
- iOS: TCA `AccountFeature` and `AccountContainer`, independent of the reading/audio store.
  A stable account-opener value avoids Home invalidations from changing closures.
  Firebase observer registration/removal is paired, with its opaque token protected by a lock.
- Android: matching reducer and ViewModel-owned store, an Android-only `:core:auth` SDK adapter,
  lifecycle-aware UI collection and `callbackFlow` listener cleanup.
- Network operations are asynchronous. Busy state prevents duplicate submissions/navigation during
  mutations. Passwords clear on completion/failure, page change and dismissal. Verification resend
  has a 60-second cooldown; Firebase remains the server-side rate limiter.
- Native controls, existing paper/ink/bronze tokens, scrollable forms, scalable text, labeled actions,
  keyboard submit flow and password-manager hints. No images, animations or polling loops were added.

## Owner QA checklist (not executed)

- Open Home → Account in English and pt-BR, light/dark, large text, VoiceOver/TalkBack and small
  screens with the keyboard visible. Confirm the reader and audio remain usable after dismissing.
- Invalid email, empty password, short registration password and mismatched confirmation: no request.
- Create account, verify email, return and refresh; resend after cooldown. Recover a password through
  the received email link and sign in with the new password. Unknown email gets neutral recovery copy.
- Wrong password, existing email, offline network, disabled provider/account and repeated taps:
  safe errors, no stuck spinner or duplicate operations.
- Create guest → register: UID stays the same in Firebase. Guest → existing sign-in: warning is shown,
  no automatic merge is claimed. Cancel/sign-out behavior is clear.
- Relaunch and Android rotation: session survives, no duplicate listeners. Passwords are not restored
  after process death. Verify a deleted/disabled session does not grant backend access (backend auth
  integration is a separate future step; never rely on UI session state for authorization).
- Delete a disposable registered account: wrong password must fail; correct password must remove the
  Firebase user. Guest deletion works without password. Local reading data remains as disclosed.

## Verification commands (builds only)

Both app Debug builds succeeded. Android unit-test sources compile. Test source additions cover
validation, optional entry, guest completion, credential clearing, duplicate-submit protection,
registration results, neutral recovery, errors and deletion. No test suite was executed.
The package-wide iOS `build-for-testing` process exited with code 137 before the feature-test
sources finished compiling; iOS test compilation is therefore not claimed as validated.

```sh
xcodebuild -project Verbum.xcodeproj -scheme Verbum -destination 'generic/platform=iOS Simulator' -skipMacroValidation CODE_SIGNING_ALLOWED=NO build
cd android
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:assembleDebug :feature:scripture:compileDebugUnitTestKotlin :core:models:compileTestKotlin
```

Device success is still pending the Firebase console configuration and the owner's QA.
Roadmap status remains 🟡, not ✅.
