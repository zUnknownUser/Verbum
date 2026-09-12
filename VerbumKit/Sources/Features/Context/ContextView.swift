import ComposableArchitecture
import DesignSystem
import Models
import SwiftUI

public struct ContextView: View {
    let store: StoreOf<ContextFeature>

    public init(store: StoreOf<ContextFeature>) { self.store = store }

    public var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: Spacing.xxl) {
                VStack(alignment: .leading, spacing: Spacing.sm) {
                    Text(L10n.t("Chapter context")).overline(color: Palette.accent)
                    Text(store.reference.formatted).font(Typography.editorialTitle)
                    passageRow(store.reference, title: L10n.t("Read chapter"))
                }
                switch store.content {
                case .idle, .loading:
                    ProgressView().accessibilityLabel(L10n.t("Loading context"))
                case .unavailable:
                    Text(L10n.t("Context is not available for this chapter yet. You can keep reading the surrounding chapters below."))
                        .font(Typography.subheadline)
                case .failed:
                    VStack(alignment: .leading, spacing: Spacing.md) {
                        Text(L10n.t("Couldn't load this page"))
                        Button(L10n.t("Try Again")) { store.send(.retryTapped) }
                    }
                case .loaded(let context):
                    if context.isFixture {
                        Text(L10n.t("Demonstration content · Connections from the local sample collection, not a complete or reviewed commentary. Entity descriptions are currently in English."))
                            .font(Typography.footnote).foregroundStyle(Palette.inkSecondary)
                    }
                    Text(L10n.t("Explore the people, places and themes connected to this chapter. Related readings below share an entity in this collection; they are not necessarily parallel accounts."))
                        .font(Typography.subheadline)
                    entities(context, type: .person, title: L10n.t("People"), symbol: "person")
                    entities(context, type: .place, title: L10n.t("Places"), symbol: "mappin.and.ellipse")
                    entities(context, type: .event, title: L10n.t("Events"), symbol: "clock")
                    entities(context, type: .theme, title: L10n.t("Themes"), symbol: "sparkle")
                    if !context.relatedPassages.isEmpty {
                        section(L10n.t("Related passages")) {
                            ForEach(context.relatedPassages, id: \.self) { passageRow($0) }
                        }
                    }
                    section(L10n.t("Sources")) {
                        ForEach(context.sources) { source in
                            VStack(alignment: .leading, spacing: Spacing.xs) {
                                Text(source.citation).font(Typography.footnote)
                                if let address = source.url, let url = URL(string: address), url.scheme == "https" {
                                    Link(L10n.t("Open source"), destination: url).font(Typography.footnote)
                                }
                            }
                        }
                    }
                }
                section(L10n.t("Before and after")) {
                    if let previous = ChapterNavigation.previous(before: store.reference) { passageRow(previous) }
                    if let next = ChapterNavigation.next(after: store.reference) { passageRow(next) }
                }
            }
            .foregroundStyle(Palette.ink)
            .frame(maxWidth: Spacing.readingMaxWidth, alignment: .leading)
            .frame(maxWidth: .infinity)
            .padding(.horizontal, Spacing.readingMargin)
            .padding(.vertical, Spacing.xxl)
        }
        .background(Palette.paper.ignoresSafeArea())
        .navigationTitle(L10n.t("Context"))
        .navigationBarTitleDisplayMode(.inline)
        .tint(Palette.accent)
        .task { await store.send(.task).finish() }
    }

    private func section<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            Text(title).overline().accessibilityAddTraits(.isHeader)
            content()
        }
    }

    @ViewBuilder
    private func entities(_ context: PassageContext, type: BibleEntityType, title: String, symbol: String) -> some View {
        let entities = context.entities.filter { $0.type == type }
        if !entities.isEmpty {
            section(title) {
                ForEach(entities) { entity in
                    Button { store.send(.entityTapped(entity)) } label: {
                        VStack(alignment: .leading, spacing: Spacing.xs) {
                            Label(entity.name, systemImage: symbol).font(Typography.editorialHeadline)
                            if let summary = entity.summary {
                                Text(summary).font(Typography.subheadline).foregroundStyle(Palette.inkSecondary)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.vertical, Spacing.sm)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private func passageRow(_ reference: PassageReference, title: String? = nil) -> some View {
        Button { store.send(.passageTapped(reference)) } label: {
            Label(title ?? reference.formatted, systemImage: "book")
                .font(Typography.subheadline)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.vertical, Spacing.sm)
                .contentShape(Rectangle())
        }
    }
}
