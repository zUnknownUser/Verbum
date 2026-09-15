import ComposableArchitecture
import DesignSystem
import Models
import SwiftUI

enum AccountCopy {
    static func text(_ key: String) -> String {
        String(localized: String.LocalizationValue(key), table: "Account", bundle: L10n.bundle)
    }
}
extension EnvironmentValues {
    @Entry var openAccount = AccountOpener(store: nil)
}
/// Stable identity avoids invalidating Home when unrelated account state changes.
struct AccountOpener: Equatable {
    let store: StoreOf<AccountFeature>?
    static func == (lhs: Self, rhs: Self) -> Bool { lhs.store === rhs.store }
    @MainActor func callAsFunction() { store?.send(.open) }
}

public struct AccountContainer<Content: View>: View {
    @State private var store = Store(initialState: AccountFeature.State()) { AccountFeature() }
    private let content: Content
    public init(@ViewBuilder content: () -> Content) { self.content = content() }
    public var body: some View {
        content
            .environment(\.openAccount, AccountOpener(store: store))
            .sheet(isPresented: Binding(get: { store.presented }, set: { if !$0 { store.send(.close) } })) {
                AccountView(store: store)
            }
            .task { await store.send(.task).finish() }
    }
}

struct AccountView: View {
    let store: StoreOf<AccountFeature>
    @State private var showPassword = false
    @State private var confirmSignOut = false
    @FocusState private var focus: Field?
    private enum Field: Hashable { case email, password, confirmation }
    private func t(_ key: String) -> String { AccountCopy.text(key) }
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.xl) {
                    Text("VERBUM").overline()
                    Text(t(title)).font(Typography.editorialTitle).foregroundStyle(Palette.ink)
                        .accessibilityAddTraits(.isHeader)
                    Text(t(subtitle)).font(Typography.subheadline).foregroundStyle(Palette.inkSecondary)
                    content
                    if let notice = store.notice {
                        Label(t(notice), systemImage: "checkmark.circle")
                            .font(Typography.subheadline).foregroundStyle(Palette.inkSecondary)
                    }
                    if let failure = store.failure {
                        Label(t(String(describing: failure)), systemImage: "exclamationmark.circle")
                            .font(Typography.subheadline).foregroundStyle(Palette.ink)
                            .accessibilityIdentifier("account.error")
                    }
                    if store.busy { ProgressView(t("working")).frame(maxWidth: .infinity) }
                }
                .frame(maxWidth: Spacing.readingMaxWidth, alignment: .leading)
                .frame(maxWidth: .infinity)
                .padding(Spacing.readingMargin)
            }
            .scrollDismissesKeyboard(.interactively)
            .background(Palette.paper)
            .navigationTitle(t("account"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(t("close"), systemImage: "xmark") { store.send(.close) }.disabled(store.busy)
                }
                if store.page != .welcome && store.page != .account {
                    ToolbarItem(placement: .confirmationAction) {
                        Button(t("back")) { store.send(.page(store.session == nil ? .welcome : .account)) }
                            .disabled(store.busy)
                    }
                }
            }
        }
        .interactiveDismissDisabled(store.busy)
        .onChange(of: store.page) { _, _ in showPassword = false; focus = nil }
        .confirmationDialog(t("signOutBody"), isPresented: $confirmSignOut, titleVisibility: .visible) {
            Button(t("signOut"), role: .destructive) { store.send(.perform(.signOut)) }
            Button(t("cancel"), role: .cancel) {}
        }
    }
    @ViewBuilder private var content: some View {
        switch store.page {
        case .welcome:
            primary("register") { store.send(.page(.register)) }
            Button(t("signIn")) { store.send(.page(.signIn)) }.buttonStyle(.bordered).controlSize(.large).disabled(store.busy)
                .frame(maxWidth: .infinity)
            Button(t("anonymous")) { store.send(.perform(.anonymous)) }.frame(maxWidth: .infinity).disabled(store.busy)
            Button(t("later")) { store.send(.close) }.frame(maxWidth: .infinity).disabled(store.busy)
        case .signIn, .register, .reset:
            VStack(alignment: .leading, spacing: Spacing.lg) {
                TextField(t("email"), text: Binding(get: { store.email }, set: { store.send(.email($0)) }))
                    .textContentType(.username).keyboardType(.emailAddress)
                    .textInputAutocapitalization(.never).autocorrectionDisabled()
                    .focused($focus, equals: .email).submitLabel(store.page == .reset ? .send : .next)
                    .onSubmit { if store.page == .reset { submit() } else { focus = .password } }
                    .accessibilityIdentifier("account.email")
                if store.page != .reset {
                    passwordField(t("password"), field: .password,
                        value: Binding(get: { store.password }, set: { store.send(.password($0)) }))
                    if store.page == .register {
                        passwordField(t("confirm"), field: .confirmation,
                            value: Binding(get: { store.confirmation }, set: { store.send(.confirmation($0)) }))
                    }
                    Toggle(t("show"), isOn: $showPassword).font(Typography.footnote)
                }
            }.textFieldStyle(.roundedBorder).disabled(store.busy)
            primary(store.page == .reset ? "sendReset" : store.page == .register ? "register" : "signIn", action: submit)
            if store.page == .signIn {
                Button(t("forgot")) { store.send(.page(.reset)) }.disabled(store.busy)
            }
            if store.session?.isAnonymous == true && store.page == .signIn {
                Text(t("existingGuest")).font(Typography.footnote).foregroundStyle(Palette.inkSecondary)
            }
        case .account:
            if let session = store.session {
                Label(session.email ?? t("guest"), systemImage: "person.crop.circle")
                    .font(Typography.editorialHeadline).textSelection(.enabled)
                if session.isAnonymous {
                    primary("register") { store.send(.page(.register)) }
                    Button(t("signIn")) { store.send(.page(.signIn)) }.disabled(store.busy)
                } else {
                    Label(t(session.isEmailVerified ? "verified" : "unverified"), systemImage: session.isEmailVerified ? "checkmark.seal" : "envelope")
                    if !session.isEmailVerified {
                        Button(t("verify")) { store.send(.perform(.verify)) }.disabled(store.busy || store.verificationSent)
                        Button(t("refresh")) { store.send(.perform(.refresh)) }.disabled(store.busy)
                    }
                }
                Divider()
                Button(t("signOut")) { confirmSignOut = true }.disabled(store.busy)
                Button(t("delete"), role: .destructive) { store.send(.page(.delete)) }.disabled(store.busy)
            }
        case .delete:
            if store.session?.isAnonymous == false {
                SecureField(t("password"), text: Binding(get: { store.password }, set: { store.send(.password($0)) }))
                    .textContentType(.password).textFieldStyle(.roundedBorder).disabled(store.busy)
            }
            Button(t("delete"), role: .destructive) { store.send(.perform(.delete)) }
                .buttonStyle(.bordered).controlSize(.large).disabled(store.busy)
        }
    }
    private var title: String {
        switch store.page {
        case .welcome: "welcome"
        case .signIn: "signInTitle"
        case .register: "registerTitle"
        case .reset: "resetTitle"
        case .account: "account"
        case .delete: "deleteTitle"
        }
    }
    private var subtitle: String {
        switch store.page {
        case .welcome: "intro"
        case .signIn: "signInBody"
        case .register: "registerBody"
        case .reset: "resetBody"
        case .account: store.session?.isAnonymous == true ? "guestBody" : "accountBody"
        case .delete: store.session?.isAnonymous == true ? "deleteGuestBody" : "deleteBody"
        }
    }
    private func primary(_ key: String, action: @escaping () -> Void) -> some View {
        Button(action: action) { Text(t(key)).frame(maxWidth: .infinity) }
            .buttonStyle(.borderedProminent).controlSize(.large).disabled(store.busy)
    }
    @ViewBuilder private func passwordField(_ title: String, field: Field, value: Binding<String>) -> some View {
        Group {
            if showPassword { TextField(title, text: value) } else { SecureField(title, text: value) }
        }
        .textContentType(store.page == .register ? .newPassword : .password)
        .textInputAutocapitalization(.never).autocorrectionDisabled()
        .focused($focus, equals: field)
        .submitLabel(store.page == .register && field == .password ? .next : .go)
        .onSubmit { if store.page == .register && field == .password { focus = .confirmation } else { submit() } }
    }
    private func submit() {
        focus = nil
        store.send(.perform(store.page == .register ? .register : store.page == .reset ? .reset : .signIn))
    }
}
