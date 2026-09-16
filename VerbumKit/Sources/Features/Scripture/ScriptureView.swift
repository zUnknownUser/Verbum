import ComposableArchitecture
import DesignSystem
import SwiftUI

/// Reader, with the shelf as a sheet (compact) or a leading column (regular).
public struct ScriptureView: View {
    @Bindable var store: StoreOf<ScriptureFeature>
    @Environment(\.horizontalSizeClass) private var sizeClass

    public init(store: StoreOf<ScriptureFeature>) {
        self.store = store
    }

    public var body: some View {
        Group {
            if sizeClass == .regular && !store.reader.focusMode {
                HStack(spacing: 0) {
                    BookPickerView(store: store.scope(state: \.books, action: \.books))
                        .frame(width: 340)
                    Rectangle().fill(Palette.rule).frame(width: 1)
                    reader
                }
            } else {
                reader
                    .sheet(isPresented: shelfPresented) {
                        NavigationStack {
                            BookPickerView(store: store.scope(state: \.books, action: \.books))
                        }
                        .presentationBackground(Palette.paper)
                        .presentationDetents([.large])
                        .presentationDragIndicator(.visible)
                    }
            }
        }
        .sheet(item: $store.scope(state: \.settings, action: \.settings)) { settingsStore in
            ReaderSettingsView(store: settingsStore)
        }
    }

    /// Presenting is the title's job; only dismissal flows back from the sheet.
    private var shelfPresented: Binding<Bool> {
        Binding(get: { store.isShelfPresented }, set: { if !$0 { store.send(.shelfDismissed) } })
    }

    private var reader: some View {
        ChapterReaderView(
            store: store.scope(state: \.reader, action: \.reader),
            onTitleTapped: { store.send(.titleTapped) },
            onSettingsTapped: { store.send(.settingsButtonTapped) }
        )
    }
}
