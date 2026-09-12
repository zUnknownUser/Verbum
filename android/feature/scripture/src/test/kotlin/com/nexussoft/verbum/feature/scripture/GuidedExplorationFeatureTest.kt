package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.clients.EditorialExplorationClient
import com.nexussoft.verbum.common.arch.TestStore
import com.nexussoft.verbum.models.*
import com.nexussoft.verbum.feature.scripture.GuidedExplorationFeature.Action
import com.nexussoft.verbum.feature.scripture.GuidedExplorationFeature.State
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class GuidedExplorationFeatureTest {
    @Test fun selectionCanBeClearedAndStaleResultsAreIgnored() = runTest {
        val plan = EditorialExplorationClient.explore(ExplorationRequest(ArrivalFeeling.ANXIOUS, BookLanguage.current))
        val store = TestStore(State(), GuidedExplorationFeature.reducer(EditorialExplorationClient))
        store.send(Action.Select(ArrivalFeeling.ANXIOUS)) { State(feeling = ArrivalFeeling.ANXIOUS, isLoading = true) }
        store.receive(Action.Response(ArrivalFeeling.ANXIOUS, plan)) { it.copy(plan = plan, isLoading = false) }
        store.send(Action.ChangeFeeling) { State() }
        store.send(Action.Response(ArrivalFeeling.ANXIOUS, plan))
        store.finish()
    }
}
