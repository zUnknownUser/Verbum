import ComposableArchitecture
import DesignSystem
import SwiftUI

/// Reader adapts to the available window width, including changes in foldable poses.
public struct ScriptureView: View {
    @Bindable var store: StoreOf<ScriptureFeature>
    @Environment(\.horizontalSizeClass) private var sizeClass

    public init(store: StoreOf<ScriptureFeature>) {
        self.store = store
    }

    public var body: some View {
        GeometryReader { geometry in
            // A regular size class alone does not guarantee room for two columns.
            let showsShelf = sizeClass == .regular && geometry.size.width >= 820 && !store.reader.focusMode
            HStack(spacing: 0) {
                if showsShelf {
                    BookPickerView(store: store.scope(state: \.books, action: \.books))
                        .frame(width: min(340, max(280, geometry.size.width * 0.3)))
                    Rectangle().fill(Palette.rule).frame(width: 1)
                }
                reader
            }
            .sheet(isPresented: shelfPresented(inline: showsShelf)) {
                NavigationStack {
                    BookPickerView(store: store.scope(state: \.books, action: \.books))
                }
                .presentationBackground(Palette.paper)
                .presentationDetents([.large])
                .presentationDragIndicator(.visible)
            }
            .onChange(of: showsShelf) { _, isInline in
                if isInline && store.isShelfPresented { store.send(.shelfDismissed) }
            }
        }
        .sheet(item: $store.scope(state: \.settings, action: \.settings)) { settingsStore in
            ReaderSettingsView(store: settingsStore)
        }
    }

    /// Presenting is the title's job; only dismissal flows back from the sheet.
    private func shelfPresented(inline: Bool) -> Binding<Bool> {
        Binding(get: { !inline && store.isShelfPresented }, set: { if !$0 { store.send(.shelfDismissed) } })
    }

    private var reader: some View {
        ChapterReaderView(
            store: store.scope(state: \.reader, action: \.reader),
            onTitleTapped: { store.send(.titleTapped) },
            onSettingsTapped: { store.send(.settingsButtonTapped) }
        )
    }
}
