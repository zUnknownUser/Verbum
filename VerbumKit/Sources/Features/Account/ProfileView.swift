import Clients
import ComposableArchitecture
import DesignSystem
import Models
import SwiftUI

struct ProfileView: View {
    let account: StoreOf<AccountFeature>
    let store: StoreOf<ProfileFeature>
    let onOpenPassage: (PassageReference) -> Void
    let onSignOut: () -> Void
    @Environment(\.openURL) private var openURL

    var body: some View {
        VStack(spacing: Spacing.xxl) {
            identity
            journey
            usage
            section("readingSection") {
                link("bibleVersion", symbol: "book.closed", value: HelloAOTranslation.name(for: HelloAOTranslation.id(for: .current))) {
                    ProfileInformationView(title: "bibleVersion", paragraphs: ["bibleVersionBody", "bibleComparisonBody"])
                }
                Divider().overlay(Palette.rule)
                link("bibleLanguage", symbol: "character.book.closed", value: language) {
                    ProfileInformationView(title: "bibleLanguage", paragraphs: ["bibleLanguageBody"], settings: true)
                }
                Divider().overlay(Palette.rule)
                link("readingPreferences", symbol: "textformat.size") {
                    ReaderSettingsView(store: store.scope(state: \.readingSettings, action: \.readingSettings))
                        .frame(maxHeight: .infinity, alignment: .top).background(Palette.paper)
                        .navigationTitle(t("readingPreferences"))
                }
                Divider().overlay(Palette.rule)
                link("audioVoice", symbol: "headphones") {
                    ProfileInformationView(title: "audioVoice", paragraphs: ["audioBody", "voiceBody"], settings: true)
                }
            }
            section("yourVerbum") {
                collectionLink("savedPassages", symbol: "bookmark", values: store.bookmarks)
                Divider().overlay(Palette.rule)
                collectionLink("highlights", symbol: "highlighter", values: store.highlights)
                Divider().overlay(Palette.rule)
                collectionLink("notes", symbol: "note.text", values: store.notes)
                Divider().overlay(Palette.rule)
                link("readingHistory", symbol: "clock.arrow.circlepath") { history }
            }
            section("preferencesSection") {
                link("appearance", symbol: "circle.lefthalf.filled", value: t(store.appearance.rawValue)) { appearance }
                Divider().overlay(Palette.rule)
                link("notifications", symbol: "bell") {
                    ScrollView {
                        VStack(alignment: .leading, spacing: Spacing.xl) {
                            Text(t("notificationsBody")).foregroundStyle(Palette.inkSecondary)
                            DailyVerseView(store: store.scope(state: \.notifications, action: \.notifications)).mornings
                        }.padding(Spacing.readingMargin)
                            .task { await store.send(.notifications(.task)).finish() }
                    }.background(Palette.paper).navigationTitle(t("notifications"))
                }
                Divider().overlay(Palette.rule)
                link("appLanguage", symbol: "globe", value: language) {
                    ProfileInformationView(title: "appLanguage", paragraphs: ["appLanguageBody"], settings: true)
                }
            }
            section("verbumSection") {
                link("help", symbol: "questionmark.circle") {
                    ProfileInformationView(title: "help", paragraphs: ["helpReading", "helpSaving", "helpOffline"])
                }
                Divider().overlay(Palette.rule)
                link("privacy", symbol: "hand.raised") {
                    ProfileInformationView(title: "privacy", paragraphs: ["privacyLocal", "privacyAccount", "privacyAI"])
                }
                Divider().overlay(Palette.rule)
                link("about", symbol: "sparkle") {
                    ProfileInformationView(title: "about", paragraphs: ["aboutBody"], version: true)
                }
            }
            section("account") {
                if account.session != nil {
                    Button { account.send(.page(.account)) } label: { ProfileRow(title: t("accountDetails"), symbol: "person.crop.circle") }
                    Divider().overlay(Palette.rule)
                    Button(action: onSignOut) { ProfileRow(title: t("signOut"), symbol: "rectangle.portrait.and.arrow.right", disclosure: false) }
                        .disabled(account.busy)
                } else {
                    Button { account.send(.page(.welcome)) } label: { ProfileRow(title: t("signIn"), symbol: "person.crop.circle") }
                }
            }
            Text(t("deviceLocal"))
                .font(Typography.caption).foregroundStyle(Palette.inkTertiary)
                .multilineTextAlignment(.center)
        }
        .buttonStyle(.plain)
        .task { await store.send(.task).finish() }
    }

    private var usage: some View {
        section("usageTitle") {
            VStack(alignment: .leading, spacing: Spacing.md) {
                if store.usageFailed {
                    Text(t("usageUnavailable")).font(Typography.footnote)
                } else if let status = store.usage {
                    Text(t("plan_" + status.plan)).font(Typography.editorialHeadline)
                    ForEach([("ask", "usageAsk"), ("voice", "usageVoice")], id: \.0) { item in
                        HStack { Text(t(item.1)); Spacer(); Text(String(status.remaining[item.0] ?? 0)).monospacedDigit() }.font(Typography.footnote)
                    }
                    Text(t("usageTTS")).font(Typography.footnote)
                    if let date = status.resetDate { Text(t("usageReset") + " " + date.formatted(Date.FormatStyle(date: .abbreviated, time: .shortened).locale(BookLanguage.current.locale))).font(Typography.caption) }
                    if status.restricted { Text(t("usageRestricted")).font(Typography.footnote) }
                    Text(t("usageCache")).font(Typography.caption).foregroundStyle(Palette.inkSecondary)
                } else { Text(t("plan_guest")).font(Typography.editorialHeadline) }
            }.padding(.vertical, Spacing.lg)
        }
    }

    private var identity: some View {
        VStack(spacing: Spacing.sm) {
            ZStack {
                Circle().fill(Palette.accent.opacity(0.09))
                Circle().strokeBorder(Palette.accent.opacity(0.2), lineWidth: 1)
                if let initials = account.session?.profileInitials {
                    Text(initials).font(.system(size: 30, weight: .medium, design: .serif))
                } else {
                    Image(systemName: "person.crop.circle").font(.system(size: 36, weight: .light))
                }
            }
            .foregroundStyle(Palette.accent).frame(width: 88, height: 88).accessibilityHidden(true)
            Text(account.session?.displayName?.nilIfBlank ?? t("yourReadingSpace"))
                .font(Typography.editorialHeadline).foregroundStyle(Palette.ink)
                .multilineTextAlignment(.center)
            if let email = account.session?.email {
                HStack(spacing: Spacing.xs) {
                    Text(email).textSelection(.enabled)
                    if account.session?.isEmailVerified == true {
                        Image(systemName: "checkmark.seal.fill").foregroundStyle(Palette.accent)
                            .accessibilityLabel(t("verified"))
                    }
                }.font(Typography.footnote).foregroundStyle(Palette.inkSecondary)
            } else {
                Text(t("profileGuestBody")).font(Typography.footnote).foregroundStyle(Palette.inkSecondary)
                    .multilineTextAlignment(.center)
            }
            if account.session == nil || account.session?.isAnonymous == true {
                Button(t("createYourAccount")) { account.send(.page(.welcome)) }
                    .font(Typography.footnote.weight(.medium)).foregroundStyle(Palette.accent).padding(.top, Spacing.xs)
            }
        }.frame(maxWidth: .infinity).padding(.top, Spacing.sm)
    }

    private var journey: some View {
        VStack(alignment: .leading, spacing: Spacing.lg) {
            HStack {
                Text(t("yourJourney")).overline(color: Palette.accent)
                Spacer()
                Image(systemName: "leaf").foregroundStyle(Palette.accent).accessibilityHidden(true)
            }
            Text(t(store.activity.visits.isEmpty ? "journeyBeginning" : "journeyContinuing"))
                .font(Typography.editorialHeadline).foregroundStyle(Palette.ink)
            HStack(alignment: .top, spacing: Spacing.md) {
                metric(store.activity.days.count, "readingDays")
                metric(store.activity.visits.count, "chaptersOpened")
                metric(store.loadFailed || store.loading ? nil : store.bookmarks.count, "savedPassages")
            }
            if let reference = store.activity.visits.first?.reference ?? store.lastRead {
                Divider().overlay(Palette.rule)
                Button { onOpenPassage(reference) } label: {
                    HStack {
                        Text(t("continueReading")).font(Typography.footnote)
                        Spacer()
                        Text(reference.formatted).font(Typography.navigationSerif)
                        Image(systemName: "arrow.right").font(Typography.footnote)
                    }.foregroundStyle(Palette.accent)
                }
            }
        }
        .padding(Spacing.xl)
        .background(Palette.accent.opacity(0.055), in: RoundedRectangle(cornerRadius: Radius.lg))
        .overlay(RoundedRectangle(cornerRadius: Radius.lg).strokeBorder(Palette.accent.opacity(0.15), lineWidth: 1))
    }

    private func metric(_ value: Int?, _ key: String) -> some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text(value.map(String.init) ?? "—").font(.system(.title, design: .serif)).monospacedDigit()
            Text(t(key)).font(Typography.caption).foregroundStyle(Palette.inkSecondary)
                .fixedSize(horizontal: false, vertical: true)
        }.frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityElement(children: .combine)
    }

    private func section<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Text(t(title)).overline(color: Palette.inkTertiary).padding(.horizontal, Spacing.xs)
            VStack(spacing: 0, content: content).padding(.horizontal, Spacing.lg)
                .background(Palette.paperElevated, in: RoundedRectangle(cornerRadius: Radius.lg))
                .overlay(RoundedRectangle(cornerRadius: Radius.lg).strokeBorder(Palette.rule, lineWidth: 1))
        }
    }

    private func link<Destination: View>(_ key: String, symbol: String, value: String? = nil,
                                         @ViewBuilder destination: () -> Destination) -> some View {
        NavigationLink(destination: destination) { ProfileRow(title: t(key), symbol: symbol, value: value) }
    }

    private func collectionLink(_ title: String, symbol: String, values: [ReaderAnnotation]) -> some View {
        link(title, symbol: symbol, value: store.loading || store.loadFailed ? nil : String(values.count)) {
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.lg) {
                    if store.loading { ProgressView().frame(maxWidth: .infinity) }
                    else if store.loadFailed {
                        ContentUnavailableView(t("collectionLoadFailed"), systemImage: "exclamationmark.circle")
                        Button(t("tryAgain")) { store.send(.task) }
                    } else if values.isEmpty {
                        ContentUnavailableView(t(title), systemImage: symbol, description: Text(t("collectionEmpty")))
                    } else {
                        ForEach(values) { annotation in
                            Button { onOpenPassage(annotation.reference) } label: {
                                VStack(alignment: .leading, spacing: Spacing.sm) {
                                    ProfileRow(title: annotation.reference.formatted, symbol: symbol)
                                    if !annotation.note.isEmpty {
                                        Text(annotation.note).font(Typography.body).foregroundStyle(Palette.inkSecondary)
                                            .multilineTextAlignment(.leading).lineLimit(4).padding(.bottom, Spacing.md)
                                    }
                                }
                            }.buttonStyle(.plain)
                            Divider().overlay(Palette.rule)
                        }
                    }
                }.padding(Spacing.readingMargin)
            }.background(Palette.paper).navigationTitle(t(title))
        }
    }

    private var history: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.md) {
                Text(t("historyBody")).font(Typography.footnote).foregroundStyle(Palette.inkSecondary)
                if store.activity.visits.isEmpty {
                    ContentUnavailableView(t("historyEmpty"), systemImage: "book")
                }
                ForEach(store.activity.visits) { visit in
                    Button { onOpenPassage(visit.reference) } label: {
                        ProfileRow(title: visit.reference.formatted, symbol: "book", value: visit.lastOpened.formatted(Date.FormatStyle(date: .abbreviated, time: .omitted).locale(BookLanguage.current.locale)))
                    }.buttonStyle(.plain)
                    Divider().overlay(Palette.rule)
                }
            }.padding(Spacing.readingMargin)
        }.background(Palette.paper).navigationTitle(t("readingHistory"))
    }

    private var appearance: some View {
        List {
            ForEach(ProfileAppearance.allCases, id: \.self) { value in
                Button { store.send(.appearanceChanged(value)) } label: {
                    HStack {
                        Text(t(value.rawValue)).foregroundStyle(Palette.ink)
                        Spacer()
                        if store.appearance == value { Image(systemName: "checkmark").foregroundStyle(Palette.accent) }
                    }
                }.listRowBackground(Palette.paperElevated)
            }
        }.scrollContentBackground(.hidden).background(Palette.paper).navigationTitle(t("appearance"))
    }

    private var language: String { BookLanguage.current == .portuguese ? "Português" : "English" }
    private func t(_ key: String) -> String { AccountCopy.text(key) }
}

struct ProfileRow: View {
    let title: String
    let symbol: String
    var value: String? = nil
    var disclosure = true
    var body: some View {
        HStack(spacing: Spacing.md) {
            Image(systemName: symbol).font(.system(size: 19, weight: .regular))
                .foregroundStyle(Palette.accent).frame(width: 24).accessibilityHidden(true)
            Text(title).font(Typography.body).foregroundStyle(Palette.ink)
            Spacer(minLength: Spacing.sm)
            if let value { Text(value).font(Typography.footnote).foregroundStyle(Palette.inkSecondary).multilineTextAlignment(.trailing) }
            if disclosure { Image(systemName: "chevron.right").font(.system(size: 11, weight: .semibold)).foregroundStyle(Palette.inkTertiary).accessibilityHidden(true) }
        }.padding(.vertical, Spacing.lg).frame(minHeight: 52).contentShape(Rectangle())
    }
}

struct ProfileInformationView: View {
    let title: String
    let paragraphs: [String]
    var settings = false
    var version = false
    @Environment(\.openURL) private var openURL
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.xl) {
                Text(AccountCopy.text(title)).font(Typography.editorialTitle).foregroundStyle(Palette.ink)
                ForEach(paragraphs, id: \.self) { key in
                    Text(AccountCopy.text(key)).font(Typography.body).foregroundStyle(Palette.inkSecondary)
                }
                if settings {
                    Button(AccountCopy.text("openSystemSettings")) {
                        if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
                    }.buttonStyle(.bordered).tint(Palette.accent)
                }
                if version {
                    Text("Verbum \(Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0")")
                        .font(Typography.footnote).foregroundStyle(Palette.inkTertiary)
                }
            }.frame(maxWidth: Spacing.readingMaxWidth, alignment: .leading)
                .frame(maxWidth: .infinity, alignment: .center).padding(Spacing.readingMargin)
        }.background(Palette.paper).navigationTitle(AccountCopy.text(title)).navigationBarTitleDisplayMode(.inline)
    }
}

extension String {
    fileprivate var nilIfBlank: String? { trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : self }
}

extension AuthSession {
    var profileInitials: String? {
        let parts = (displayName?.nilIfBlank ?? "").split(whereSeparator: \.isWhitespace)
        if let first = parts.first?.first {
            return (String(first) + (parts.count > 1 ? String(parts.last!.first!) : "")).uppercased()
        }
        return email?.first.map { String($0).uppercased() }
    }
}
