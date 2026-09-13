import ComposableArchitecture
import DesignSystem
import Models
import SwiftUI

/// An entity's page on paper: kind, name, other names, a line of facts, the
/// summary, then the passages and the people, places, events and themes one
/// hop away. Sources close the page (spec §33).
public struct EntityDetailView: View {
    let store: StoreOf<EntityDetailFeature>

    public init(store: StoreOf<EntityDetailFeature>) {
        self.store = store
    }

    public var body: some View {
        ZStack {
            Palette.paper.ignoresSafeArea()
            switch store.content {
            case .idle, .loading:
                ProgressView().tint(Palette.inkTertiary)
            case .failed:
                unavailable
            case .loaded(let page):
                pageBody(page)
            }
        }
        .navigationTitle(loadedName)
        .toolbarTitleDisplayMode(.inline)
        .toolbarBackground(.hidden, for: .navigationBar)
        .task { await store.send(.task).finish() }
    }

    private var loadedName: String {
        if case .loaded(let page) = store.content { return page.entity.name }
        return ""
    }

    private func pageBody(_ page: EntityDetailFeature.Page) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.xxl) {
                header(page)
                if !page.neighborhood.nodes.isEmpty {
                    Row(title: L10n.t("Explore the graph"), subtitle: L10n.t("Connections around \(page.entity.name), one step at a time."), symbol: "point.3.connected.trianglepath.dotted") {
                        store.send(.graphTapped)
                    }
                }
                if page.isOnTimeline {
                    Row(title: L10n.t("View in timeline"), subtitle: L10n.t("Where \(page.entity.name) sits in biblical history."), symbol: "clock") {
                        store.send(.timelineTapped)
                    }
                }
                Row(title: L10n.t("Talk about \(page.entity.name)"), subtitle: L10n.t("Ask out loud; the companion answers from Scripture."), symbol: "waveform.and.mic") {
                    store.send(.talkTapped)
                }
                if !page.passages.isEmpty {
                    section(L10n.t("Key passages")) {
                        ForEach(page.passages, id: \.self) { reference in
                            Row(title: reference.formatted, symbol: "book") { store.send(.passageTapped(reference)) }
                        }
                    }
                }
                relatedSection(L10n.t("People"), page.related(.person), symbol: "person")
                relatedSection(L10n.t("Places"), page.related(.place), symbol: "mappin.and.ellipse")
                relatedSection(L10n.t("Events"), page.related(.event), symbol: "clock")
                relatedSection(L10n.t("Themes"), page.related(.theme), symbol: "sparkle")
                sources(page.sources)
            }
            .frame(maxWidth: Spacing.readingMaxWidth, alignment: .leading)
            .frame(maxWidth: .infinity)
            .padding(.horizontal, Spacing.readingMargin)
            .padding(.top, Spacing.lg)
            .padding(.bottom, Spacing.xxxl * 2)
        }
        .scrollIndicators(.hidden)
    }

    private func header(_ page: EntityDetailFeature.Page) -> some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Text(kindTitle(page.entity.type)).overline(color: Palette.accent)
            Text(page.entity.name)
                .font(Typography.editorialTitle)
                .foregroundStyle(Palette.ink)
            if !page.detail.aliases.isEmpty {
                Text(page.detail.aliases.joined(separator: " · "))
                    .font(.system(.subheadline, design: .serif).italic())
                    .foregroundStyle(Palette.inkSecondary)
            }
            VStack(alignment: .leading, spacing: Spacing.xs) {
                if let role = page.detail.role { fact(L10n.t("Role"), role) }
                if let dates = page.detail.approximateDates { fact(L10n.t("When"), dates) }
                if let modern = page.detail.modernGeography { fact(L10n.t("Today"), modern) }
            }
            .padding(.top, Spacing.xs)
            if let summary = page.entity.summary {
                Text(summary)
                    .font(Typography.scripture)
                    .foregroundStyle(Palette.ink)
                    .lineSpacing(Typography.scriptureLineSpacing)
                    .padding(.top, Spacing.md)
            }
        }
        .accessibilityElement(children: .combine)
    }

    private func fact(_ label: String, _ value: String) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: Spacing.sm) {
            Text(label)
                .font(Typography.caption.weight(.semibold))
                .foregroundStyle(Palette.inkTertiary)
                .frame(width: 52, alignment: .leading)
            Text(value)
                .font(Typography.subheadline)
                .foregroundStyle(Palette.inkSecondary)
        }
    }

    @ViewBuilder
    private func relatedSection(_ title: String, _ entities: [BibleEntity], symbol: String) -> some View {
        if !entities.isEmpty {
            section(title) {
                ForEach(entities) { entity in
                    Row(title: entity.name, subtitle: entity.summary, symbol: symbol) { store.send(.entityTapped(entity)) }
                }
            }
        }
    }

    private func section<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(title).overline().padding(.bottom, Spacing.xs)
            content()
        }
    }

    private func sources(_ sources: [SourceReference]) -> some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Text(L10n.t("Sources")).overline()
            ForEach(sources) { source in
                Text(source.citation)
                    .font(Typography.footnote)
                    .foregroundStyle(Palette.inkSecondary)
            }
        }
        .padding(.top, Spacing.md)
    }

    private var unavailable: some View {
        VStack(spacing: Spacing.md) {
            Text(L10n.t("Couldn't load this page"))
                .font(Typography.editorialHeadline)
                .foregroundStyle(Palette.ink)
            Button(L10n.t("Try Again")) { store.send(.retryTapped) }
                .buttonStyle(.borderedProminent)
        }
        .padding(Spacing.xxl)
    }

    private func kindTitle(_ type: BibleEntityType) -> String {
        switch type {
        case .person: L10n.t("Person")
        case .place: L10n.t("Place")
        case .event: L10n.t("Event")
        case .theme: L10n.t("Theme")
        case .passage: L10n.t("Passage")
        case .book: L10n.t("Book")
        case .prophecy: L10n.t("Prophecy")
        case .originalTerm: L10n.t("Original term")
        case .historicalPeriod: L10n.t("Period")
        }
    }
}

private struct Row: View {
    let title: String
    var subtitle: String? = nil
    let symbol: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: Spacing.md) {
                Image(systemName: symbol)
                    .font(Typography.subheadline)
                    .foregroundStyle(Palette.accent)
                    .frame(width: 22)
                VStack(alignment: .leading, spacing: Spacing.xxs) {
                    Text(title)
                        .font(.system(.body, design: .serif).weight(.semibold))
                        .foregroundStyle(Palette.ink)
                    if let subtitle, !subtitle.isEmpty {
                        Text(subtitle)
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

