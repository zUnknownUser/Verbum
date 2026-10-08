import ComposableArchitecture
import DesignSystem
import Foundation
import Models
import SwiftUI

struct ThemeCategory: Identifiable {
    let id: String
    let title: String.LocalizationValue
    let subtitle: String.LocalizationValue
    let icon: String
    static let all: [Self] = [
        .init(id: "with-god", title: "Life with God", subtitle: "Faith, prayer and worship", icon: "sun.max"),
        .init(id: "emotions", title: "Emotions and difficulties", subtitle: "Anxiety, grief and hope", icon: "heart"),
        .init(id: "relationships", title: "Relationships", subtitle: "Love, family and forgiveness", icon: "person.2"),
        .init(id: "character", title: "Character and choices", subtitle: "Wisdom, humility and courage", icon: "leaf"),
        .init(id: "daily-life", title: "Everyday life", subtitle: "Work, money and rest", icon: "house"),
        .init(id: "foundations", title: "Foundations of faith", subtitle: "Grace, salvation and covenant", icon: "book.closed"),
        .init(id: "community", title: "Life in community", subtitle: "Service, unity and mission", icon: "person.3"),
        .init(id: "eternity", title: "Life and eternity", subtitle: "Resurrection and new creation", icon: "sparkles")
    ]
}

struct ThemeDiscoveryView: View {
    let store: StoreOf<EntityListFeature>
    @ScaledMetric(relativeTo: .body) private var minimumCardWidth: CGFloat = 150
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.xl) {
                VStack(alignment: .leading, spacing: Spacing.sm) {
                    Text(L10n.t("Explore by subject")).font(Typography.editorialTitle)
                    Text(L10n.t("Find a starting point for what you are living or want to understand."))
                        .font(Typography.subheadline).foregroundStyle(Palette.inkSecondary)
                }
                VStack(alignment: .leading, spacing: Spacing.sm) {
                    Text(L10n.t("Where to begin")).overline()
                    HStack(spacing: Spacing.sm) {
                        featured("fixture.theme.faith", title: "Faith", icon: "sun.max")
                        featured("fixture.theme.prayer", title: "Prayer", icon: "hands.sparkles")
                        featured("editorial.theme.hope", title: "Hope", icon: "leaf")
                    }
                }
                VStack(alignment: .leading, spacing: Spacing.md) {
                    Text(L10n.t("Browse categories")).overline()
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: minimumCardWidth), spacing: Spacing.md)], spacing: Spacing.md) {
                        ForEach(ThemeCategory.all) { category in
                            Button { store.send(.categoryChanged(category.id)) } label: {
                                VStack(alignment: .leading, spacing: Spacing.sm) {
                                    Image(systemName: category.icon).font(.title2).foregroundStyle(Palette.accent).accessibilityHidden(true)
                                    Text(L10n.t(category.title)).font(.system(.headline, design: .serif)).foregroundStyle(Palette.ink).fixedSize(horizontal: false, vertical: true)
                                    Text(L10n.t(category.subtitle)).font(Typography.footnote).foregroundStyle(Palette.inkSecondary)
                                    Spacer(minLength: 0)
                                }.frame(minWidth: 0, maxWidth: .infinity, minHeight: 128, alignment: .topLeading)
                                    .padding(Spacing.lg).background(Palette.paperElevated, in: RoundedRectangle(cornerRadius: 18))
                            }.buttonStyle(EditorialButtonStyle()).accessibilityIdentifier("themes.category.\(category.id)")
                        }
                    }
                }
                Button { store.send(.categoryChanged("")) } label: {
                    HStack { Text(L10n.t("All themes")); Spacer(); Image(systemName: "arrow.right") }
                        .font(Typography.navigationSerif).padding(.vertical, Spacing.lg).contentShape(Rectangle())
                }.buttonStyle(.plain).accessibilityIdentifier("themes.all")
            }.frame(maxWidth: Spacing.readingMaxWidth).frame(maxWidth: .infinity)
                .padding(.horizontal, Spacing.readingMargin).padding(.vertical, Spacing.xl)
        }.scrollDismissesKeyboard(.interactively)
    }
    private func featured(_ id: String, title: String.LocalizationValue, icon: String) -> some View {
        Button { store.send(.entityTapped(.init(id: id, type: .theme, name: L10n.t(title), summary: nil))) } label: {
            VStack(spacing: Spacing.sm) {
                Image(systemName: icon).accessibilityHidden(true)
                Text(L10n.t(title)).font(Typography.footnote)
            }.frame(maxWidth: .infinity, minHeight: 76).padding(Spacing.sm)
                .foregroundStyle(Palette.onForest).background(Palette.forest, in: RoundedRectangle(cornerRadius: 16))
        }.buttonStyle(EditorialButtonStyle())
    }
}
