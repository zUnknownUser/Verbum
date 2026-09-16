import Clients
import Features
import SwiftUI

struct RootView: View {
    @State private var owner = LocalAccountData.owner
    @State private var ready = false
    @State private var failed = false
    @State private var retry = 0
    var body: some View {
        Group {
            if ready { AccountRootView().id(owner) }
            else if failed {
                VStack {
                    Text(Locale.current.language.languageCode?.identifier == "pt" ? "Não foi possível abrir seus dados salvos." : "Your saved data could not be opened.")
                    Button(Locale.current.language.languageCode?.identifier == "pt" ? "Tentar novamente" : "Try again") { failed = false; retry += 1 }
                }
            } else { ProgressView() }
        }
            .task(id: retry) {
                for await _ in AccountClient.liveValue.sessions() {
                    if !ready {
                        do { try LocalAccountData.prepare() }
                        catch { failed = true; return }
                    }
                    let next = LocalAccountData.owner
                    if owner != next {
                        owner = next
                        await AudioPlayerClient.liveValue.stop()
                        await VoiceClient.liveValue.stop()
                    }
                    ready = true
                }
            }
    }
}
private struct AccountRootView: View {
    @State private var store = Store(initialState: AppFeature.State()) { AppFeature() } withDependencies: {
        $0.readerAnnotations = .forCurrentAccount()
    }
    var body: some View {
        AccountContainer(onOpenPassage: { store.send(.profilePassageOpened($0)) }) { AppView(store: store) }
    }
}
#Preview { RootView() }
