import ComposableArchitecture
import DesignSystem
import Models
import SwiftUI

/// Spec §7: the paths into the graph, as an index page.
struct ExploreView: View {
    let store: StoreOf<ExploreFeature>

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.xl) {
                VStack(alignment: .leading, spacing: Spacing.sm) {
                    Text(L10n.t("Explore"))
                        .font(Typography.editorialTitle)
                        .foregroundStyle(Palette.ink)
                    Text(L10n.t("Move between people, places, themes, events and the books themselves."))
                        .font(Typography.subheadline)
                        .foregroundStyle(Palette.inkSecondary)
                }
                .padding(.top, Spacing.xl)

                VStack(spacing: Spacing.md) {
                    ForEach(ExploreFeature.Entry.allCases, id: \.self) { entry in
                        EntryRow(entry: entry) { store.send(.entryTapped(entry)) }
                    }
                }
            }
            .frame(maxWidth: Spacing.readingMaxWidth, alignment: .leading)
            .frame(maxWidth: .infinity)
            .padding(.horizontal, Spacing.readingMargin)
            .padding(.bottom, Spacing.xxxl * 2)
        }
        .scrollIndicators(.hidden)
        .background(Palette.paper)
        .navigationTitle(L10n.t("Explore"))
        .toolbar(.hidden, for: .navigationBar)
    }
}

private struct EntryRow: View {
    let entry: ExploreFeature.Entry
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: Spacing.lg) {
                EditorialIcon(symbol)
                VStack(alignment: .leading, spacing: Spacing.xxs) {
                    Text(title)
                        .font(.system(.title3, design: .serif).weight(.semibold))
                        .foregroundStyle(Palette.ink)
                    Text(subtitle)
                        .font(Typography.footnote)
                        .foregroundStyle(Palette.inkSecondary)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(Typography.caption.weight(.semibold))
                    .foregroundStyle(Palette.inkTertiary)
            }
            .padding(Spacing.lg)
            .editorialSurface()
            .multilineTextAlignment(.leading)
            .contentShape(Rectangle())
        }
        .buttonStyle(EditorialButtonStyle())
    }

    private var title: String {
        switch entry {
        case .people: L10n.t("People")
        case .places: L10n.t("Places")
        case .themes: L10n.t("Themes")
        case .events: L10n.t("Events")
        case .timeline: L10n.t("Timeline")
        case .books: L10n.t("Books")
        }
    }

    private var subtitle: String {
        switch entry {
        case .people: L10n.t("Who they were, where they appear, who they knew.")
        case .places: L10n.t("Where it happened, and what happened there.")
        case .themes: L10n.t("Ideas that run through the whole of Scripture.")
        case .events: L10n.t("What happened, before and after.")
        case .timeline: L10n.t("Periods and events in order, with how sure the dates are.")
        case .books: L10n.t("The canon as a shelf, by kind.")
        }
    }

    private var symbol: String {
        switch entry {
        case .people: "person.2"
        case .places: "mappin.and.ellipse"
        case .themes: "text.book.closed"
        case .events: "flag"
        case .timeline: "point.topleft.down.to.point.bottomright.curvepath"
        case .books: "books.vertical"
        }
    }
}

/// People / Places / Themes / Events: one list, serif names, summaries.
public struct EntityListView: View {
    let store: StoreOf<EntityListFeature>

    public init(store: StoreOf<EntityListFeature>) {
        self.store = store
    }

    public var body: some View {
        Group {
            if store.type == .person { PeopleCatalogView(store: store) }
            else { legacyList }
        }
        .task { await store.send(.task).finish() }
        .onDisappear { store.send(.cancel) }
    }

    private var legacyList: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Text(title)
                    .font(Typography.editorialTitle)
                    .foregroundStyle(Palette.ink)
                    .padding(.top, Spacing.lg)
                    .padding(.bottom, Spacing.lg)
                if store.isLoading {
                    ProgressView().tint(Palette.inkTertiary).frame(maxWidth: .infinity).padding(.top, Spacing.xxl)
                }
                ForEach(store.entities) { entity in
                    Button { store.send(.entityTapped(entity)) } label: {
                        HStack(spacing: Spacing.md) {
                            VStack(alignment: .leading, spacing: Spacing.xxs) {
                                Text(entity.name)
                                    .font(.system(.body, design: .serif).weight(.semibold))
                                    .foregroundStyle(Palette.ink)
                                if let summary = entity.summary {
                                    Text(summary)
                                        .font(Typography.footnote)
                                        .foregroundStyle(Palette.inkSecondary)
                                        .lineLimit(2)
                                }
                            }
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(Typography.caption.weight(.semibold))
                                .foregroundStyle(Palette.inkTertiary)
                        }
                        .padding(.vertical, Spacing.md)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .overlay(alignment: .bottom) { Rectangle().fill(Palette.rule).frame(height: 1) }
                }
            }
            .frame(maxWidth: Spacing.readingMaxWidth, alignment: .leading)
            .frame(maxWidth: .infinity)
            .padding(.horizontal, Spacing.readingMargin)
            .padding(.bottom, Spacing.xxxl * 2)
        }
        .scrollIndicators(.hidden)
        .background(Palette.paper)
        .navigationTitle(title)
        .toolbarTitleDisplayMode(.inline)
        .toolbarBackground(.hidden, for: .navigationBar)
    }

    private var title: String {
        switch store.type {
        case .person: L10n.t("People")
        case .place: L10n.t("Places")
        case .theme: L10n.t("Themes")
        case .event: L10n.t("Events")
        default: L10n.t("Explore")
        }
    }
}
