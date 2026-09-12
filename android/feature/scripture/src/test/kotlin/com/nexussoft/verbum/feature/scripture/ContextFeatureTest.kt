package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.clients.ContextClient
import com.nexussoft.verbum.common.arch.TestStore
import com.nexussoft.verbum.models.*
import com.nexussoft.verbum.feature.scripture.ContextFeature.State
import com.nexussoft.verbum.feature.scripture.ContextFeature.Content
import com.nexussoft.verbum.feature.scripture.ContextFeature.Action
import com.nexussoft.verbum.feature.scripture.ContextFeature.DelegateAction
import com.nexussoft.verbum.feature.scripture.ContextFeature.reducer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ContextFeatureTest {
    @Test fun missingCoverageIsNotFailure() = runTest {
        val store = TestStore(State(PassageReference("Neh", 9)), reducer(ContextClient { null }))
        store.send(Action.Started) { it.copy(content = Content.Loading) }
        store.receive(Action.Response(null)) { it.copy(content = Content.Unavailable) }
        store.send(Action.Started)
        store.finish()
    }

    @Test fun loadedContextIsRetainedAndPassagesNavigate() = runTest {
        val reference = PassageReference("1Sam", 17)
        val page = PassageContext(reference, emptyList(), emptyList(), emptyList(), true)
        val store = TestStore(State(reference), reducer(ContextClient { page }))
        store.send(Action.Started) { it.copy(content = Content.Loading) }
        store.receive(Action.Response(page)) { it.copy(content = Content.Loaded(page)) }
        store.send(Action.Started)
        store.send(Action.PassageTapped(reference))
        store.receive(Action.Delegate(DelegateAction.OpenPassage(reference)))
        store.finish()
    }

    @Test fun failedLoadCanRetry() = runTest {
        val store = TestStore(State(PassageReference("John", 3)), reducer(ContextClient { error("offline") }))
        store.send(Action.Started) { it.copy(content = Content.Loading) }
        store.receive(Action.Failed) { it.copy(content = Content.Failed) }
        store.send(Action.RetryTapped) { it.copy(content = Content.Loading) }
        store.receive(Action.Failed) { it.copy(content = Content.Failed) }
        store.finish()
    }
}
