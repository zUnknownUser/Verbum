import Clients
import ComposableArchitecture
import DesignSystem
import Models
import SwiftUI

/// The §13.2 shape on paper: short answer, the answer, key passages, explore
/// further, perspectives (only when they differ), sources. Nothing is a
/// revelation and nothing is certain beyond what the server says it is.
public struct AskView: View {
    let store: StoreOf<AskFeature>
    let embedded: Bool

    public init(store: StoreOf<AskFeature>, embedded: Bool = false) { self.store = store; self.embedded = embedded }

    public var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: Spacing.xxl) {
                VStack(alignment: .leading, spacing: Spacing.sm) {
                    Text(L10n.t("Ask Scripture")).overline(color: Palette.accent)
                    Text(store.question).font(Typography.editorialTitle)
                }
                switch store.content {
                case .idle, .asking:
                    VStack(alignment: .leading, spacing: Spacing.md) {
                        ProgressView().accessibilityLabel(L10n.t("Searching Scripture"))
                        Text(L10n.t("Reading the passages this question touches before answering."))
                            .font(Typography.subheadline).foregroundStyle(Palette.inkSecondary)
                    }
                case .failed(let error):
                    failure(error)
                case .answered(let page):
                    if page.answer.isEmpty {
                        noAnswer(page.answer)
                    } else {
                        answered(page)
                    }
                }
            }
            .foregroundStyle(Palette.ink)
            .frame(maxWidth: Spacing.readingMaxWidth, alignment: .leading)
            .frame(maxWidth: .infinity)
            .padding(.horizontal, Spacing.readingMargin)
            .padding(.vertical, Spacing.xxl)
        }
        .background(Palette.paper.ignoresSafeArea())
        .navigationTitle(L10n.t("Ask"))
        .navigationBarTitleDisplayMode(.inline)
        .tint(Palette.accent)
        .task { await store.send(.task).finish() }
    }

    // MARK: Answered

    @ViewBuilder
    private func answered(_ page: AskFeature.Page) -> some View {
        let answer = page.answer
        section(L10n.t("Short answer")) {
            Text(answer.summary).font(Typography.editorialHeadline)
        }
        VStack(alignment: .leading, spacing: Spacing.md) {
            Text(answer.answer)
                .font(Typography.scripture)
                .lineSpacing(Typography.scriptureLineSpacing)
            confidenceLine(answer.confidence)
            if !embedded { Button { store.send(.talkTapped) } label: {
                Label(L10n.t("Go on out loud"), systemImage: "waveform.and.mic")
                    .font(Typography.subheadline.weight(.semibold))
            }
            .padding(.top, Spacing.xs) }
        }
        if !answer.passageReferences.isEmpty {
            section(L10n.t("Key passages")) {
                ForEach(answer.passageReferences, id: \.self) { passageRow($0) }
            }
        }
        if !page.entities.isEmpty {
            section(L10n.t("Explore further")) {
                ForEach(page.entities) { entity in
                    Button { store.send(.entityTapped(entity)) } label: {
                        VStack(alignment: .leading, spacing: Spacing.xs) {
                            Label(entity.name, systemImage: symbol(for: entity.type)).font(Typography.editorialHeadline)
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
        if answer.interpretiveVariance {
            section(L10n.t("Perspectives")) {
                Text(L10n.t("Christian traditions and scholars read this question differently. The answer above is one reading of the passages, not the only one."))
                    .font(Typography.subheadline)
            }
        }
        section(L10n.t("Sources")) {
            ForEach(answer.sourceReferences) { source in
                VStack(alignment: .leading, spacing: Spacing.xs) {
                    Text(source.citation).font(Typography.footnote)
                    if let address = source.url, let url = URL(string: address), url.scheme == "https" {
                        Link(L10n.t("Open source"), destination: url).font(Typography.footnote)
                    }
                }
            }
            Text(L10n.t("Written from the passages above by a language model, then checked so every reference is real. It is a study aid, not a word from God."))
                .font(Typography.footnote).foregroundStyle(Palette.inkSecondary)
        }
    }

    /// §31: confidence is shown, never implied.
    private func confidenceLine(_ confidence: ScriptureAnswer.Confidence) -> some View {
        let text: String = switch confidence {
        case .high: L10n.t("Well supported by the passages cited.")
        case .medium: L10n.t("Partly supported by the passages cited — read them.")
        case .low: L10n.t("Weakly supported — treat as a starting point, not a conclusion.")
        }
        return Label(text, systemImage: confidence == .high ? "checkmark.seal" : "questionmark.circle")
            .font(Typography.footnote)
            .foregroundStyle(Palette.inkSecondary)
    }

    // MARK: Nothing to stand behind (§51)

    @ViewBuilder
    private func noAnswer(_ answer: ScriptureAnswer) -> some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            Text(L10n.t("No reliable answer"))
                .font(Typography.editorialHeadline)
            Text(answer.passageReferences.isEmpty
                 ? L10n.t("I could not build a reliable answer from the available sources.")
                 : L10n.t("I could not build a reliable answer from the available sources. Here are the closest passages I found."))
                .font(Typography.subheadline)
            if !embedded { Button(L10n.t("See search results")) { store.send(.searchInsteadTapped) }
                .font(Typography.subheadline.weight(.semibold)) }
        }
        if !answer.passageReferences.isEmpty {
            section(L10n.t("Closest passages")) {
                ForEach(answer.passageReferences, id: \.self) { passageRow($0) }
            }
        }
    }

    // MARK: Failed (§52)

    private func failure(_ error: AskScriptureError) -> some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            switch error {
            case .unavailable:
                Text(L10n.t("Ask isn't available on this server yet")).font(Typography.editorialHeadline)
                Text(L10n.t("Search still works — try the same words there.")).font(Typography.subheadline)
            case .networkUnavailable:
                Text(L10n.t("Verbum can't be reached")).font(Typography.editorialHeadline)
                Text(L10n.t("Asking needs a connection. Reading and search of what this device knows keep working.")).font(Typography.subheadline)
                Button(L10n.t("Try Again")) { store.send(.retryTapped) }.font(Typography.subheadline.weight(.semibold))
            case .failed:
                Text(L10n.t("Couldn't answer this time")).font(Typography.editorialHeadline)
                Button(L10n.t("Try Again")) { store.send(.retryTapped) }.font(Typography.subheadline.weight(.semibold))
            }
            if !embedded { Button(L10n.t("See search results")) { store.send(.searchInsteadTapped) }
                .font(Typography.subheadline.weight(.semibold)) }
        }
    }

    // MARK: Pieces

    private func section<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            Text(title).overline().accessibilityAddTraits(.isHeader)
            content()
        }
    }

    private func passageRow(_ reference: PassageReference) -> some View {
        Button { store.send(.passageTapped(reference)) } label: {
            Label(reference.formatted, systemImage: "book")
                .font(Typography.subheadline)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.vertical, Spacing.sm)
                .contentShape(Rectangle())
        }
    }

    private func symbol(for type: BibleEntityType) -> String {
        switch type {
        case .person: "person"
        case .place: "mappin.and.ellipse"
        case .event: "clock"
        case .theme: "sparkle"
        default: "point.3.connected.trianglepath.dotted"
        }
    }
}

#Preview {
    NavigationStack {
        AskView(store: Store(initialState: AskFeature.State(question: "How did David defeat Goliath?")) { AskFeature() })
    }
}
