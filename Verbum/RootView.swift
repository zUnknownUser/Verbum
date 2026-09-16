import Features
import SwiftUI

/// The app shell (spec §6). The store lives here so it survives view updates.
struct RootView: View {
    @State private var store = Store(initialState: AppFeature.State()) {
        AppFeature()
    }

    var body: some View {
        AccountContainer(onOpenPassage: { store.send(.profilePassageOpened($0)) }) { AppView(store: store) }
    }
}

#Preview {
    RootView()
}
