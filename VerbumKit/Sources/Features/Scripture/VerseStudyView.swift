import Clients
import ComposableArchitecture
import DesignSystem
import Models
import SwiftUI

struct VerseStudyView: View {
    @Bindable var store: StoreOf<VerseStudyFeature>
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        VStack(spacing: 0) {
            HStack {
                if store.ask != nil {
                    Button { store.send(.closeAnswer) } label: { Label(L10n.t("Study"), systemImage: "chevron.left") }
                }
                Text(store.reference.formatted).font(Typography.navigationSerif)
                Spacer()
                Button { store.send(.bookmarkToggled) } label: {
                    Image(systemName: store.annotation.bookmarked == true ? "bookmark.fill" : "bookmark")
                }
                .accessibilityLabel(AccountCopy.text(store.annotation.bookmarked == true ? "removeBookmark" : "savePassage"))
                .disabled(store.saving)
                Button(L10n.t("Done")) { store.send(.done) }.disabled(store.saving)
            }.padding(.horizontal, Spacing.lg).padding(.top, Spacing.xl).padding(.bottom, Spacing.md)
            if let ask = store.scope(state: \.ask, action: \.ask) {
                AskView(store: ask, embedded: true)
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: Spacing.lg) {
                        Text(store.text).font(Typography.scripture).lineLimit(4)
                            .foregroundStyle(Palette.ink).textSelection(.enabled)
                        ScrollView(.horizontal) {
                            HStack(spacing: Spacing.md) {
                                ForEach(VerseStudyFeature.Tab.allCases, id: \.self) { tab in
                                    Button { store.send(.tabChanged(tab)) } label: {
                                        VStack(spacing: Spacing.xs) {
                                            Image(systemName: symbol(tab)).font(.system(size: 17, weight: .regular))
                                            Text(title(tab)).font(Typography.caption)
                                        }.frame(minWidth: 56).padding(.vertical, Spacing.sm)
                                            .foregroundStyle(store.tab == tab ? Palette.accent : Palette.inkSecondary)
                                    }.buttonStyle(.plain).accessibilityAddTraits(store.tab == tab ? .isSelected : [])
                                }
                            }
                        }.scrollIndicators(.hidden)
                        Divider().overlay(Palette.rule)
                        content
                    }
                    .frame(maxWidth: Spacing.readingMaxWidth, alignment: .leading)
                    .frame(maxWidth: .infinity)
                    .padding(.horizontal, Spacing.lg).padding(.bottom, Spacing.xxl)
                }
            }
        }
        .foregroundStyle(Palette.ink).tint(Palette.accent)
        .interactiveDismissDisabled(store.saving || store.annotation.note != store.savedNote)
        .task { await store.send(.task).finish() }
    }
    @ViewBuilder private var content: some View {
        switch store.tab {
        case .highlight:
            Text(L10n.t("Mark what speaks to you")).font(Typography.editorialHeadline)
            Picker(L10n.t("Highlight style"), selection: Binding(get: { store.annotation.highlightStyle ?? .background }, set: { store.send(.highlightStyleChanged($0)) })) {
                Text(L10n.t("Soft background")).tag(HighlightStyle.background)
                Text(L10n.t("Underline")).tag(HighlightStyle.underline)
                Text(L10n.t("Margin mark")).tag(HighlightStyle.margin)
            }.pickerStyle(.segmented).disabled(store.saving)
            HStack(spacing: Spacing.xl) {
                ForEach(HighlightColor.allCases, id: \.self) { color in
                    Button { store.send(.highlightChanged(store.annotation.highlight == color ? nil : color)) } label: {
                        Circle().fill(swatch(color).opacity(0.5)).frame(width: 42, height: 42)
                            .overlay { if store.annotation.highlight == color { Image(systemName: "checkmark").foregroundStyle(Palette.ink) } }
                    }.accessibilityLabel(color == .gold ? L10n.t("Gold highlight") : color == .sage ? L10n.t("Sage highlight") : L10n.t("Rose highlight"))
                }
                if store.annotation.highlight != nil { Button(L10n.t("Remove highlight")) { store.send(.highlightChanged(nil)) }.font(Typography.footnote) }
            }
            .disabled(store.saving)
            saveStatus
            Text(L10n.t("Highlights and notes stay on this device.")).font(Typography.footnote).foregroundStyle(Palette.inkSecondary)
        case .note:
            Text(L10n.t("Your note")).font(Typography.editorialHeadline)
            TextEditor(text: Binding(get: { store.annotation.note }, set: { store.send(.noteChanged($0)) }))
                .frame(minHeight: 140).scrollContentBackground(.hidden)
                .font(Typography.body).padding(Spacing.sm).background(Palette.paper.opacity(0.5), in: .rect(cornerRadius: Radius.md))
                .accessibilityLabel(L10n.t("Your note")).disabled(store.saving)
            HStack {
                Text(L10n.t("Only you write here.")).font(Typography.footnote).foregroundStyle(Palette.inkSecondary)
                Spacer()
                Button(L10n.t("Save note")) { store.send(.save) }.disabled(store.saving)
            }
            saveStatus
        case .compare:
            Text(HelloAOTranslation.name(for: store.translationID)).font(Typography.editorialHeadline)
            Text(store.text).font(Typography.scripture)
            Divider()
            if store.comparisonLoading { ProgressView() }
            else if let comparison = store.comparison {
                Text(comparison.translationId == "por_bsl" ? "Bíblia Portuguesa Mundial" : "World English Bible").font(Typography.editorialHeadline)
                Text(comparison.text).font(Typography.scripture).textSelection(.enabled)
                Text(comparison.translationId == "por_bsl" ? L10n.t("Public domain · translation under revision") : L10n.t("Public domain")).font(Typography.footnote).foregroundStyle(Palette.inkSecondary)
                Link(L10n.t("Open source"), destination: URL(string: comparison.translationId == "por_bsl" ? "https://ebible.org/Scriptures/details.php?id=porbrbsl" : "https://worldenglish.bible")!)
            } else { retry(.compare) }
        case .context:
            if store.entityLoading { ProgressView() }
            else if let detail = store.entity {
                Button { store.send(.closeEntity) } label: { Label(L10n.t("Chapter context"), systemImage: "chevron.left") }.font(Typography.footnote)
                Text(detail.entity.name).font(Typography.editorialTitle)
                if let summary = detail.entity.summary { Text(summary).font(Typography.scripture) }
                if let role = detail.role { Text(role).font(Typography.subheadline) }
                if let dates = detail.approximateDates { Text(dates).font(Typography.footnote) }
                ForEach(detail.keyPassages, id: \.self) { passage($0) }
                sources(detail.sources)
            } else if !store.candidates.isEmpty {
                Text(L10n.t("People and places in this chapter")).font(Typography.editorialHeadline)
                ForEach(store.candidates) { entity in entityButton(entity) }
            } else {
                Text(L10n.t("Chapter context")).font(Typography.editorialHeadline)
                Text(L10n.t("Connections documented for this chapter; not every link is a direct parallel to this verse.")).font(Typography.footnote).foregroundStyle(Palette.inkSecondary)
                if store.contextLoading { ProgressView() }
                else if let context = store.context {
                    ForEach(context.entities.prefix(24)) { entity in entityButton(entity) }
                    sources(context.sources)
                } else if store.contextFailed || store.entityFailed { retry(.context) }
                else { Text(L10n.t("No sourced context is available for this passage yet.")).font(Typography.subheadline) }
            }
        case .references:
            Text(L10n.t("Follow the references")).font(Typography.editorialHeadline)
            if store.contextLoading { ProgressView() }
            else if let context = store.context, !context.relatedPassages.isEmpty {
                Text(L10n.t("Related passages from the chapter’s documented connections.")).font(Typography.footnote)
                ForEach(context.relatedPassages, id: \.self) { passage($0) }
                sources(context.sources)
            } else if store.contextFailed { retry(.references) }
            else { Text(L10n.t("No sourced references are available here yet.")).font(Typography.subheadline) }
        case .ask:
            Text(L10n.t("Investigate this passage")).font(Typography.editorialHeadline)
            TextField(L10n.t("What would you like to understand?"), text: Binding(get: { store.question }, set: { store.send(.questionChanged($0)) }), axis: .vertical)
                .lineLimit(2...5).textFieldStyle(.plain).padding(Spacing.md)
                .background(Palette.paper.opacity(0.5), in: .rect(cornerRadius: Radius.md))
            Text(L10n.t("The answer will use this passage and show its biblical references. Your note is never included.")).font(Typography.footnote).foregroundStyle(Palette.inkSecondary)
            Button(L10n.t("Ask about this passage")) { store.send(.submitQuestion) }
                .disabled(store.question.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
        }
    }
    @ViewBuilder private var saveStatus: some View {
        if store.saving { ProgressView() }
        else if store.saveFailed { Text(L10n.t("Could not save. Your note is still here; try again.")).foregroundStyle(.red) }
        else if store.saved { Label(L10n.t("Saved"), systemImage: "checkmark").font(Typography.footnote) }
    }
    private func retry(_ tab: VerseStudyFeature.Tab) -> some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Text(L10n.t("Could not load this study layer.")).font(Typography.subheadline)
            Button(L10n.t("Try Again")) { store.send(.tabChanged(tab)) }
        }
    }
    private func entityButton(_ entity: BibleEntity) -> some View {
        Button { store.send(.entityTapped(entity)) } label: {
            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text(entity.name).font(Typography.editorialHeadline)
                if let summary = entity.summary { Text(summary).font(Typography.subheadline).foregroundStyle(Palette.inkSecondary) }
            }.frame(maxWidth: .infinity, alignment: .leading).padding(.vertical, Spacing.xs)
        }.buttonStyle(.plain)
    }
    private func passage(_ reference: PassageReference) -> some View {
        Button { store.send(.passageTapped(reference)) } label: {
            Label(reference.formatted, systemImage: "arrow.turn.down.right").font(Typography.navigationSerif)
        }
    }
    private func sources(_ values: [SourceReference]) -> some View {
        DisclosureGroup(L10n.t("Sources")) {
            ForEach(values) { source in
                VStack(alignment: .leading, spacing: Spacing.xs) {
                    Text(source.citation).font(Typography.footnote)
                    if let address = source.url, let url = URL(string: address), url.scheme == "https" { Link(L10n.t("Open source"), destination: url).font(Typography.footnote) }
                }.frame(maxWidth: .infinity, alignment: .leading).padding(.vertical, Spacing.sm)
            }
        }.font(Typography.subheadline)
    }
    private func swatch(_ color: HighlightColor) -> Color { switch color { case .gold: Color(red: 0.66, green: 0.51, blue: 0.28); case .sage: Color(red: 0.40, green: 0.53, blue: 0.44); case .rose: Color(red: 0.65, green: 0.43, blue: 0.46) } }
    private func title(_ tab: VerseStudyFeature.Tab) -> String {
        switch tab { case .highlight: L10n.t("Highlight"); case .note: L10n.t("Note"); case .compare: L10n.t("Compare"); case .context: L10n.t("Context"); case .references: L10n.t("References"); case .ask: L10n.t("Ask") }
    }
    private func symbol(_ tab: VerseStudyFeature.Tab) -> String {
        switch tab { case .highlight: "highlighter"; case .note: "square.and.pencil"; case .compare: "rectangle.split.2x1"; case .context: "text.book.closed"; case .references: "arrow.triangle.branch"; case .ask: "questionmark.bubble" }
    }
}
