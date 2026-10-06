import ComposableArchitecture
import DesignSystem
import Models
import SwiftUI

struct HomeView: View {
    let store: StoreOf<HomeFeature>
    @Environment(\.openAccount) private var openAccount

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.xxl) {
                header
                Button { store.send(.searchTapped) } label: {
                    HStack(spacing: Spacing.md) {
                        Image(systemName: "magnifyingglass")
                            .font(.body.weight(.medium)).foregroundStyle(Palette.accent)
                        Text(L10n.t("Ask anything about Scripture"))
                            .font(Typography.body).foregroundStyle(Palette.inkSecondary)
                            .multilineTextAlignment(.leading)
                        Spacer(minLength: 0)
                        Image(systemName: "arrow.up.right").font(.footnote.weight(.semibold))
                            .foregroundStyle(Palette.accent).accessibilityHidden(true)
                    }
                    .padding(Spacing.lg)
                    .frame(minHeight: 56)
                    .editorialSurface()
                }
                .buttonStyle(EditorialButtonStyle())
                .accessibilityLabel(L10n.t("Search"))

                if let lastRead = store.lastRead {
                    continueReading(lastRead)
                }

                VStack(alignment: .leading, spacing: Spacing.md) {
                    Text(L10n.t("Today")).overline()
                    DailyVerseView(store: store.scope(state: \.dailyVerse, action: \.dailyVerse))
                }
                PassageCard(
                    title: L10n.t("How are you feeling today?"),
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

    private var header: some View {
        VStack(alignment: .leading, spacing: Spacing.xl) {
            HStack {
                Text(verbatim: "verbum").font(.system(.title2, design: .serif).weight(.semibold))
                    .tracking(-0.5).foregroundStyle(Palette.ink)
                Spacer()
                Button { openAccount() } label: {
                    Image(systemName: "person.crop.circle")
                        .font(.system(size: 23, weight: .regular))
                        .foregroundStyle(Palette.accent)
                        .frame(width: 48, height: 48)
                        .background(Palette.paperElevated, in: Circle())
                        .overlay(Circle().strokeBorder(Palette.rule, lineWidth: 1))
                }
                .buttonStyle(EditorialButtonStyle())
                .accessibilityLabel(AccountCopy.text("profile"))
            }
            VStack(alignment: .leading, spacing: Spacing.sm) {
                Text(greeting).overline(color: Palette.accent)
                Text(L10n.t("What do you want to understand?"))
                    .font(Typography.editorialTitle)
                    .tracking(-0.7)
                    .foregroundStyle(Palette.ink)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityAddTraits(.isHeader)
            }
        }
        .padding(.top, Spacing.lg)
    }

    private func continueReading(_ reference: PassageReference) -> some View {
        Button { store.send(.continueReadingTapped) } label: {
            VStack(alignment: .leading, spacing: Spacing.xl) {
                HStack(spacing: Spacing.sm) {
                    Image(systemName: "book.pages").accessibilityHidden(true)
                    Text(L10n.t("Continue reading"))
                }.font(Typography.subheadline.weight(.medium))
                HStack(alignment: .bottom, spacing: Spacing.md) {
                    VStack(alignment: .leading, spacing: Spacing.xs) {
                        Text(reference.formatted).font(Typography.editorialHeadline)
                        Text(BibleBook.book(id: reference.bookId)?.division.localizedTitle ?? "")
                            .font(Typography.footnote)
                    }.frame(maxWidth: .infinity, alignment: .leading)
                    Image(systemName: "arrow.right")
                        .font(.body.weight(.semibold))
                        .frame(width: 44, height: 44)
                        .background(Palette.onForest.opacity(0.12), in: Circle())
                        .accessibilityHidden(true)
                }
            }
            .foregroundStyle(Palette.onForest)
            .multilineTextAlignment(.leading)
            .padding(Spacing.xl)
            .background(Palette.forest, in: RoundedRectangle(cornerRadius: Radius.lg))
        }
        .buttonStyle(EditorialButtonStyle())
    }

    private var greeting: String {
        switch store.greeting {
        case .morning: L10n.t("Good morning")
        case .afternoon: L10n.t("Good afternoon")
        case .evening: L10n.t("Good evening")
        }
    }
}

struct PassageCard: View {
    let title: String
    let subtitle: String
    let symbol: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(alignment: .top, spacing: Spacing.lg) {
                EditorialIcon(symbol)
                VStack(alignment: .leading, spacing: Spacing.xs) {
                    Text(title).font(Typography.navigationSerif).foregroundStyle(Palette.ink)
                    if !subtitle.isEmpty {
                        Text(subtitle).font(Typography.footnote).foregroundStyle(Palette.inkSecondary)
                    }
                }.frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: "arrow.up.right")
                    .font(.footnote.weight(.semibold)).foregroundStyle(Palette.accent)
                    .padding(.top, Spacing.xs).accessibilityHidden(true)
            }
            .multilineTextAlignment(.leading)
            .padding(Spacing.lg)
            .editorialSurface()
            .contentShape(Rectangle())
        }
        .buttonStyle(EditorialButtonStyle())
    }
}
