import Clients
import ComposableArchitecture
import Models
import Testing
@testable import Features

@MainActor
@Suite struct GuidedExplorationFeatureTests {
    @Test func selectionLoadsAPreviewAndCanBeCleared() async throws {
        let plan = try await GuidedExplorationClient.editorialPreview.explore(.init(feeling: .anxious, language: .current))
        let store = TestStore(initialState: GuidedExplorationFeature.State()) { GuidedExplorationFeature() } withDependencies: {
            $0.guidedExploration.explore = { _ in plan }
        }
        await store.send(.select(.anxious)) { $0.feeling = .anxious; $0.isLoading = true }
        await store.receive(.response(.anxious, plan)) { $0.plan = plan; $0.isLoading = false }
        await store.send(.changeFeeling) { $0 = GuidedExplorationFeature.State() }
        await store.send(.response(.anxious, plan)) // stale result must not restore the choice
    }
}
