import Clients
import ComposableArchitecture
import Models

/// Optional account boundary: Scripture remains available without authentication.
@Reducer
public struct AccountFeature {
    public enum Page: Equatable, Sendable { case welcome, signIn, register, reset, account, delete }
    public enum Operation: Equatable, Sendable { case signIn, register, anonymous, reset, verify, refresh, signOut, delete }
    @ObservableState
    public struct State: Equatable {
        public var presented = false
        public var page: Page = .welcome
        public var session: AuthSession?
        public var email = ""
        public var password = ""
        public var confirmation = ""
        public var busy = false
        public var failure: AccountFailure?
        public var notice: String?
        public var verificationSent = false
        public init() {}
        mutating func clearSecrets() { password = ""; confirmation = "" }
    }
    public enum Action: Equatable {
        case task, open, close
        case page(Page), email(String), password(String), confirmation(String)
        case sessionChanged(AuthSession?)
        case perform(Operation)
        case completed(Operation, AuthSession?)
        case failed(AccountFailure)
        case verificationCooldownEnded(String?)
    }
    @Dependency(\.accountClient) var client
    @Dependency(\.continuousClock) var clock
    public init() {}
    public var body: some ReducerOf<Self> {
        Reduce { state, action in
            switch action {
            case .task:
                return .run { [client] send in
                    for await session in client.sessions() { await send(.sessionChanged(session)) }
                }.cancellable(id: "account-session", cancelInFlight: true)
            case .open:
                state.presented = true
                state.page = state.session == nil ? .welcome : .account
                state.failure = nil; state.notice = nil
                return .none
            case .close:
                guard !state.busy else { return .none }
                state.presented = false; state.clearSecrets()
                return .none
            case let .page(page):
                guard !state.busy else { return .none }
                state.page = page; state.clearSecrets(); state.failure = nil; state.notice = nil
                return .none
            case let .email(value): state.email = value; state.failure = nil; return .none
            case let .password(value): state.password = value; state.failure = nil; return .none
            case let .confirmation(value): state.confirmation = value; state.failure = nil; return .none
            case let .sessionChanged(session):
                if state.session?.id != session?.id { state.verificationSent = false }
                state.session = session
                if !state.busy, state.page == .account, session == nil { state.page = .welcome }
                return .none
            case let .perform(operation):
                guard !state.busy else { return .none }
                state.failure = nil; state.notice = nil
                if [.signIn, .register, .reset].contains(operation),
                   let failure = AccountValidation.validate(email: state.email, password: state.password,
                       confirmation: operation == .register ? state.confirmation : nil, reset: operation == .reset) {
                    state.failure = failure
                    return .none
                }
                if operation == .delete, state.session?.isAnonymous == false, state.password.isEmpty {
                    state.failure = .passwordRequired; return .none
                }
                if operation == .verify, state.verificationSent { return .none }
                state.busy = true
                let email = AccountValidation.email(state.email), password = state.password
                return .run { [client] send in
                    do {
                        let session: AuthSession?
                        switch operation {
                        case .signIn: session = try await client.signIn(email, password)
                        case .register: session = try await client.register(email, password)
                        case .anonymous: session = try await client.anonymous()
                        case .reset: try await client.resetPassword(email); session = nil
                        case .verify: try await client.sendVerification(); session = nil
                        case .refresh: session = try await client.refresh()
                        case .signOut: try await client.signOut(); session = nil
                        case .delete: try await client.deleteAccount(password); session = nil
                        }
                        await send(.completed(operation, session))
                    } catch { await send(.failed(error as? AccountFailure ?? .unexpected)) }
                }
            case let .completed(operation, session):
                state.busy = false; state.clearSecrets()
                switch operation {
                case .signIn, .register, .refresh:
                    state.session = session; state.page = session == nil ? .welcome : .account
                case .anonymous:
                    state.session = session; state.presented = false
                case .reset: state.notice = "resetSent"
                case .verify:
                    state.verificationSent = true; state.notice = "verificationSent"
                    return .run { [clock, id = state.session?.id] send in
                        try await clock.sleep(for: .seconds(60))
                        await send(.verificationCooldownEnded(id))
                    }.cancellable(id: "account-verification", cancelInFlight: true)
                case .signOut, .delete:
                    state.session = nil; state.page = .welcome; state.email = ""; state.verificationSent = false
                    if operation == .delete { state.notice = "deleted" }
                }
                return .none
            case let .failed(failure):
                state.busy = false; state.failure = failure; state.clearSecrets()
                return .none
            case let .verificationCooldownEnded(id):
                if state.session?.id == id { state.verificationSent = false }
                return .none
            }
        }
    }
}
