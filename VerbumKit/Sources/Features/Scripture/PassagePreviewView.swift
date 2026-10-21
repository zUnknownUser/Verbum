import Clients
import ComposableArchitecture
import DesignSystem
import Models
import SwiftUI

struct PassagePreviewView: View {
    let store: StoreOf<PassagePreviewFeature>

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.lg) {
                    ScripturePassagePreviewContent(
                        reference: store.reference, passage: store.passage,
                        loading: store.loading, error: store.error,
                        retry: { store.send(.retry) }, openReader: { store.send(.openReader) }
                    )
                }
                .frame(maxWidth: Spacing.readingMaxWidth, alignment: .leading)
                .frame(maxWidth: .infinity)
                .padding(Spacing.readingMargin)
            }
            .background(Palette.paper)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(StudyGuideCopy.back) { store.send(.close) }
                }
            }
            .toolbarTitleDisplayMode(.inline)
        }
        .tint(Palette.accent)
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
        .task { await store.send(.task).finish() }
    }
}

/// Shared rendering for both the reader's study sheet and theme previews.
/// Data loading and navigation stay with their owning reducers.
struct ScripturePassagePreviewContent: View {
    let reference: PassageReference
    let passage: BiblePassage?
    let loading: Bool
    let error: ReaderError?
    var readerDisabled = false
    let retry: () -> Void
    let openReader: () -> Void

    var body: some View {
        Text(reference.formatted).font(Typography.editorialTitle)
        if loading { ProgressView() }
        else if let passage {
            Text(HelloAOTranslation.name(for: passage.translationId))
                .font(Typography.caption).foregroundStyle(Palette.inkSecondary)
            Text(passage.text).font(Typography.scripture).textSelection(.enabled)
            Button(StudyGuideCopy.openReader, action: openReader).disabled(readerDisabled)
        } else if let error {
            Text(error.title).font(Typography.editorialHeadline)
            Text(error.message).font(Typography.subheadline).foregroundStyle(Palette.inkSecondary)
            Button(L10n.t("Try Again"), action: retry)
        }
    }
}
