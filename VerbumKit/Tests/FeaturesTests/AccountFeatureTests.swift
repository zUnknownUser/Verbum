import Clients
import ComposableArchitecture
import Models
import Testing
@testable import Features

@MainActor
struct AccountFeatureTests {
    private let guest = AuthSession(id: "guest", email: nil, isAnonymous: true, isEmailVerified: false)
    @Test func optionalEntryAndSecretsClearedOnDismiss() async {
        let store = TestStore(initialState: AccountFeature.State()) { AccountFeature() }
        await store.send(.open) { $0.presented = true }
        await store.send(.page(.signIn)) { $0.page = .signIn }
        await store.send(.password("not-a-real-password")) { $0.password = "not-a-real-password" }
        await store.send(.close) { $0.presented = false; $0.password = "" }
    }
    @Test func invalidFormNeverCallsFirebase() async {
        let store = TestStore(initialState: AccountFeature.State()) { AccountFeature() }
        await store.send(.perform(.register)) { $0.failure = .invalidEmail }
    }
    @Test func anonymousSuccessDismissesWithoutForcingRegistration() async {
        let guest = guest
        let store = TestStore(initialState: AccountFeature.State()) { AccountFeature() } withDependencies: {
            $0.accountClient.anonymous = { guest }
        }
        await store.send(.open) { $0.presented = true }
        await store.send(.perform(.anonymous)) { $0.busy = true }
        await store.receive(.completed(.anonymous, guest)) { $0.busy = false; $0.session = guest; $0.presented = false }
    }
    @Test func busyPreventsDismissalAndDuplicateRequests() async {
        var state = AccountFeature.State(); state.busy = true; state.presented = true
        let store = TestStore(initialState: state) { AccountFeature() }
        await store.send(.perform(.anonymous))
        await store.send(.close)
        await store.send(.page(.register))
    }
    @Test func registrationKeepsGuestIdentityAndClearsSecrets() async {
        var state = AccountFeature.State()
        state.session = guest; state.email = " reader@example.com "; state.password = "password123"; state.confirmation = "password123"
        let registered = AuthSession(id: guest.id, email: "reader@example.com", isAnonymous: false, isEmailVerified: false)
        let store = TestStore(initialState: state) { AccountFeature() } withDependencies: {
            $0.accountClient.register = { email, password in
                #expect(email == "reader@example.com"); #expect(password == "password123")
                return registered
            }
        }
        await store.send(.perform(.register)) { $0.busy = true }
        await store.receive(.completed(.register, registered)) {
            $0.busy = false; $0.session = registered; $0.page = .account; $0.password = ""; $0.confirmation = ""
        }
    }
    @Test func recoveryHasNeutralResponse() async {
        var state = AccountFeature.State(); state.email = "reader@example.com"; state.page = .reset
        let store = TestStore(initialState: state) { AccountFeature() } withDependencies: {
            $0.accountClient.resetPassword = { _ in }
        }
        await store.send(.perform(.reset)) { $0.busy = true }
        await store.receive(.completed(.reset, nil)) { $0.busy = false; $0.notice = "resetSent" }
    }
    @Test func failureIsSafeAndClearsPassword() async {
        var state = AccountFeature.State(); state.email = "reader@example.com"; state.password = "password123"
        let store = TestStore(initialState: state) { AccountFeature() } withDependencies: {
            $0.accountClient.signIn = { _, _ in throw AccountFailure.network }
        }
        await store.send(.perform(.signIn)) { $0.busy = true }
        await store.receive(.failed(.network)) { $0.busy = false; $0.failure = .network; $0.password = "" }
    }
    @Test func restoredSessionOpensAccountAndDeletionClearsIt() async {
        let store = TestStore(initialState: AccountFeature.State()) { AccountFeature() } withDependencies: {
            $0.accountClient.deleteAccount = { _ in }
        }
        await store.send(.sessionChanged(guest)) { $0.session = guest }
        await store.send(.open) { $0.presented = true; $0.page = .account }
        await store.send(.page(.delete)) { $0.page = .delete }
        await store.send(.perform(.delete)) { $0.busy = true }
        await store.receive(.completed(.delete, nil)) {
            $0.busy = false; $0.session = nil; $0.page = .welcome; $0.notice = "deleted"
        }
    }
}
