import Features
import SwiftUI

@main
struct VerbumApp: App {
    init() {
        AppLaunch.prepare()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
        }
    }
}
