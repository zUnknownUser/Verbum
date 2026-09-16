import Clients
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
    @Shared(.profileAppearance) private var appearance
    private let onOpenPassage: (PassageReference) -> Void
    private let content: Content
    public init(onOpenPassage: @escaping (PassageReference) -> Void, @ViewBuilder content: () -> Content) {
        self.onOpenPassage = onOpenPassage; self.content = content()
    }
    public var body: some View {
        content
            .environment(\.openAccount, AccountOpener(store: store))
            .sheet(isPresented: Binding(get: { store.presented }, set: { if !$0 { store.send(.close) } })) {
                AccountView(store: store, onOpenPassage: onOpenPassage)
            }
            .preferredColorScheme(appearance == .automatic ? nil : appearance == .dark ? .dark : .light)
            .task { await store.send(.task).finish() }
    }
}

struct AccountView: View {
    let store: StoreOf<AccountFeature>
    let onOpenPassage: (PassageReference) -> Void
    @State private var profile = Store(initialState: ProfileFeature.State()) { ProfileFeature() } withDependencies: { $0.readerAnnotations = .forCurrentAccount() }
    @State private var showPassword = false
    @State private var confirmSignOut = false
    @FocusState private var focus: Field?
    private enum Field: Hashable { case email, password, confirmation }
    private func t(_ key: String) -> String { AccountCopy.text(key) }
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.xl) {
                    if store.page != .profile {
                    Text("VERBUM").overline()
                    Text(t(title)).font(Typography.editorialTitle).foregroundStyle(Palette.ink)
                        .accessibilityAddTraits(.isHeader)
                    Text(t(subtitle)).font(Typography.subheadline).foregroundStyle(Palette.inkSecondary)
                    }
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
            .navigationTitle(t(store.page == .profile ? "profile" : store.page == .account ? "accountDetails" : "account"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(t("close"), systemImage: "xmark") { store.send(.close) }.disabled(store.busy)
                }
                if store.page != .profile {
                    ToolbarItem(placement: .confirmationAction) {
                        Button(t("back")) { store.send(.page(backPage)) }
                            .disabled(store.busy)
                    }
                }
            }
        }
        .preferredColorScheme(profile.appearance == .automatic ? nil : profile.appearance == .dark ? .dark : .light)
        .interactiveDismissDisabled(store.busy)
        .onChange(of: store.page) { _, _ in showPassword = false; focus = nil }
        .confirmationDialog(t("signOutBody"), isPresented: $confirmSignOut, titleVisibility: .visible) {
            Button(t("signOut"), role: .destructive) { store.send(.perform(.signOut)) }
            Button(t("cancel"), role: .cancel) {}
        }
    }
    @ViewBuilder private var content: some View {
        switch store.page {
        case .profile:
            ProfileView(account: store, store: profile, onOpenPassage: { reference in
                store.send(.close); onOpenPassage(reference)
            }, onSignOut: { confirmSignOut = true })
        case .editName:
            TextField(t("displayName"), text: Binding(get: { store.displayName }, set: { store.send(.displayName($0)) }))
                .textContentType(.name).textFieldStyle(.roundedBorder).disabled(store.busy)
            primary("saveChanges") { store.send(.perform(.updateName)) }
        case .changeEmail:
            TextField(t("newEmail"), text: Binding(get: { store.email }, set: { store.send(.email($0)) }))
                .textContentType(.emailAddress).keyboardType(.emailAddress).textInputAutocapitalization(.never)
                .autocorrectionDisabled().textFieldStyle(.roundedBorder).disabled(store.busy)
            SecureField(t("password"), text: Binding(get: { store.password }, set: { store.send(.password($0)) }))
                .textContentType(.password).textFieldStyle(.roundedBorder).disabled(store.busy)
            primary("confirmEmailChange") { store.send(.perform(.changeEmail)) }
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
                VStack(alignment: .leading, spacing: Spacing.lg) {
                    if !session.isAnonymous {
                        detail("displayName", value: session.displayName ?? t("nameNotSet"))
                        Button(t("editName")) { store.send(.page(.editName)) }.disabled(store.busy)
                        Divider().overlay(Palette.rule)
                        detail("email", value: session.email ?? "—")
                        Label(t(session.isEmailVerified ? "verified" : "unverified"), systemImage: session.isEmailVerified ? "checkmark.seal.fill" : "envelope")
                            .font(Typography.footnote).foregroundStyle(Palette.accent)
                        if !session.isEmailVerified {
                            Button(t("verify")) { store.send(.perform(.verify)) }.disabled(store.busy || store.verificationSent)
                        }
                        Button(t("refresh")) { store.send(.perform(.refresh)) }.disabled(store.busy)
                    }
                    Divider().overlay(Palette.rule)
                    detail("authentication", value: authentication(session))
                    if let date = session.createdAt {
                        detail("memberSince", value: date.formatted(.dateTime.month(.wide).year().locale(BookLanguage.current.locale)))
                    }
                    if session.isAnonymous {
                        primary("register") { store.send(.page(.register)) }
                        Button(t("signIn")) { store.send(.page(.signIn)) }.disabled(store.busy)
                    } else if session.hasPassword {
                        Divider().overlay(Palette.rule)
                        Button(t("changeEmail")) { store.send(.page(.changeEmail)) }.disabled(store.busy)
                        Button(t("changePassword")) { store.send(.perform(.resetCurrentPassword)) }.disabled(store.busy)
                        Text(t("passwordResetBody")).font(Typography.footnote).foregroundStyle(Palette.inkSecondary)
                    }
                    Divider().overlay(Palette.rule)
                    Button(t("delete"), role: .destructive) { store.send(.page(.delete)) }.disabled(store.busy)
                }
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
        case .profile: "profile"
        case .editName: "editName"
        case .changeEmail: "changeEmail"
        case .welcome: "welcome"
        case .signIn: "signInTitle"
        case .register: "registerTitle"
        case .reset: "resetTitle"
        case .account: "accountDetails"
        case .delete: "deleteTitle"
        }
    }
    private var subtitle: String {
        switch store.page {
        case .profile: "profileGuestBody"
        case .editName: "editNameBody"
        case .changeEmail: "changeEmailBody"
        case .welcome: "intro"
        case .signIn: "signInBody"
        case .register: "registerBody"
        case .reset: "resetBody"
        case .account: store.session?.isAnonymous == true ? "guestBody" : "accountBody"
        case .delete: store.session?.isAnonymous == true ? "deleteGuestBody" : "deleteBody"
        }
    }
    private var backPage: AccountFeature.Page {
        switch store.page {
        case .account, .welcome, .profile: .profile
        case .delete, .editName, .changeEmail: .account
        case .signIn, .register: .welcome
        case .reset: store.session?.isAnonymous == false ? .account : .signIn
        }
    }
    private func detail(_ key: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text(t(key)).font(Typography.caption).foregroundStyle(Palette.inkSecondary)
            Text(value).font(Typography.body).foregroundStyle(Palette.ink).textSelection(.enabled)
        }
    }
    private func authentication(_ session: AuthSession) -> String {
        if session.isAnonymous { return t("guest") }
        let names = session.providers.map { provider in
            switch provider {
            case "password": t("emailPassword")
            case "apple.com": "Apple"
            case "google.com": "Google"
            default: t("connectedAccount")
            }
        }
        return names.isEmpty ? t("connectedAccount") : names.joined(separator: ", ")
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
