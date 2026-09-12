import ComposableArchitecture
import DesignSystem
import Models
import SwiftUI

/// Spec §5: a greeting, one question, a way back in, the verse of the day.
struct HomeView: View {
    let store: StoreOf<HomeFeature>

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.xxl) {
                VStack(alignment: .leading, spacing: Spacing.sm) {
                    Text(greeting).overline(color: Palette.accent)
                    Text(L10n.t("What do you want to understand?"))
                        .font(Typography.editorialTitle)
                        .foregroundStyle(Palette.ink)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(.top, Spacing.xl)

                Button { store.send(.searchTapped) } label: {
                    HStack(spacing: Spacing.md) {
                        Image(systemName: "magnifyingglass")
                            .foregroundStyle(Palette.inkTertiary)
                        Text(L10n.t("Ask anything about Scripture"))
                            .font(.system(.body, design: .serif))
                            .foregroundStyle(Palette.inkSecondary)
                        Spacer()
                    }
                    .padding(.horizontal, Spacing.lg)
                    .frame(height: 52)
                    .background(Palette.paperElevated, in: .rect(cornerRadius: Radius.lg))
                    .overlay(RoundedRectangle(cornerRadius: Radius.lg).strokeBorder(Palette.rule, lineWidth: 1))
                }
                .buttonStyle(.plain)
                .accessibilityLabel(L10n.t("Search"))

                if let lastRead = store.lastRead {
                    section(L10n.t("Continue reading")) {
                        PassageCard(
                            title: lastRead.formatted,
                            subtitle: BibleBook.book(id: lastRead.bookId)?.division.localizedTitle ?? "",
                            symbol: "book"
                        ) { store.send(.continueReadingTapped) }
                    }
                }

                section(L10n.t("Today")) {
                    DailyVerseView(store: store.scope(state: \.dailyVerse, action: \.dailyVerse))
                }
                PassageCard(
                    title: L10n.t("How are you arriving today?"),
                    subtitle: L10n.t("A starting point for exploring Scripture, at your own pace."),
                    symbol: "leaf"
                ) { store.send(.arrivalTapped) }
            }
            .frame(maxWidth: Spacing.readingMaxWidth, alignment: .leading)
            .frame(maxWidth: .infinity)
            .padding(.horizontal, Spacing.readingMargin)
            .padding(.bottom, Spacing.xxxl * 2)
        }
        .scrollIndicators(.hidden)
        .background(Palette.paper)
        .navigationTitle(L10n.t("Home"))
        .toolbar(.hidden, for: .navigationBar)
        .task { await store.send(.task).finish() }
    }

    private var greeting: String {
        switch store.greeting {
        case .morning: L10n.t("Good morning")
        case .afternoon: L10n.t("Good afternoon")
        case .evening: L10n.t("Good evening")
        }
    }


    private func section<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            Text(title).overline()
            content()
        }
    }
}

/// A passage as a quiet card: reference in serif, a line of context, an arrow.
struct PassageCard: View {
    let title: String
    let subtitle: String
    let symbol: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: Spacing.lg) {
                Image(systemName: symbol)
                    .font(.system(size: 20, weight: .light))
                    .foregroundStyle(Palette.accent)
                    .frame(width: 28)
                VStack(alignment: .leading, spacing: Spacing.xxs) {
                    Text(title)
                        .font(.system(.title3, design: .serif).weight(.semibold))
                        .foregroundStyle(Palette.ink)
                    if !subtitle.isEmpty {
                        Text(subtitle)
                            .font(Typography.footnote)
                            .foregroundStyle(Palette.inkSecondary)
                    }
                }
                Spacer()
                Image(systemName: "arrow.right")
                    .font(Typography.subheadline.weight(.semibold))
                    .foregroundStyle(Palette.accent)
            }
            .padding(Spacing.lg)
            .background(Palette.paperElevated, in: .rect(cornerRadius: Radius.lg))
            .overlay(RoundedRectangle(cornerRadius: Radius.lg).strokeBorder(Palette.rule, lineWidth: 1))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}
