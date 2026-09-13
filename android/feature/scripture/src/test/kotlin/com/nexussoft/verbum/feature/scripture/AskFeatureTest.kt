package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.clients.AskScriptureClient
import com.nexussoft.verbum.clients.AskScriptureException
import com.nexussoft.verbum.clients.GraphClient
import com.nexussoft.verbum.clients.GraphClientException
import com.nexussoft.verbum.clients.PreviewAskScriptureClient
import com.nexussoft.verbum.common.arch.TestStore
import com.nexussoft.verbum.feature.scripture.AskFeature.Action
import com.nexussoft.verbum.feature.scripture.AskFeature.Content
import com.nexussoft.verbum.feature.scripture.AskFeature.DelegateAction
import com.nexussoft.verbum.feature.scripture.AskFeature.Page
import com.nexussoft.verbum.feature.scripture.AskFeature.State
import com.nexussoft.verbum.models.BibleEntity
import com.nexussoft.verbum.models.BibleEntityType
import com.nexussoft.verbum.models.EntityDetail
import com.nexussoft.verbum.models.EntityId
import com.nexussoft.verbum.models.GraphSnapshot
import com.nexussoft.verbum.models.PassageReference
import com.nexussoft.verbum.models.ScriptureAnswer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AskFeatureTest {
    private val david = BibleEntity("fixture.person.david", BibleEntityType.PERSON, "David", null)
    private val goliath = BibleEntity("fixture.person.goliath", BibleEntityType.PERSON, "Goliath", null)
    private val preview = PreviewAskScriptureClient.answer

    private fun graph(vararg known: BibleEntity) = object : GraphClient {
        override suspend fun entity(id: EntityId): BibleEntity = known.firstOrNull { it.id == id } ?: throw GraphClientException.UnknownEntity(id)
        override suspend fun neighbors(id: EntityId, limit: Int): GraphSnapshot = throw AssertionError("not stubbed")
        override suspend fun detail(id: EntityId): EntityDetail = throw AssertionError("not stubbed")
        override suspend fun entities(type: BibleEntityType): List<BibleEntity> = throw AssertionError("not stubbed")
    }

    @Test
    fun appearingAsksOnceAndResolvesTheEntitiesItCites() = runTest {
        val asked = mutableListOf<String>()
        val store = TestStore(State("  How did David defeat Goliath? "), AskFeature.reducer(AskScriptureClient { asked += it; preview }, graph(david, goliath)))
        assertEquals("How did David defeat Goliath?", store.state.question)
        store.send(Action.Started) { it.copy(content = Content.Asking) }
        store.receive(Action.Response(preview)) { it.copy(content = Content.Answered(Page(preview))) }
        store.receive(Action.EntitiesResolved(listOf(david, goliath))) { it.copy(content = Content.Answered(Page(preview, listOf(david, goliath)))) }
        assertEquals(listOf("How did David defeat Goliath?"), asked)
        // A second appearance (Back, rotation) does not ask again (§47: minimal retention, no repeats).
        store.send(Action.Started)
        store.finish()
    }

    @Test
    fun anIdTheGraphDoesNotKnowIsSimplyNotShown() = runTest {
        val store = TestStore(State("why did Job suffer"), AskFeature.reducer(AskScriptureClient { preview }, graph(david)))
        store.send(Action.Started) { it.copy(content = Content.Asking) }
        store.receive(Action.Response(preview)) { it.copy(content = Content.Answered(Page(preview))) }
        store.receive(Action.EntitiesResolved(listOf(david))) { it.copy(content = Content.Answered(Page(preview, listOf(david)))) }
        store.finish()
    }

    @Test
    fun nothingToStandBehindIsAnAnsweredPageNotAFailure() = runTest {
        val empty = ScriptureAnswer("", "No sufficiently relevant Scripture passages were found for this question.", emptyList(), emptyList(), emptyList(), ScriptureAnswer.Confidence.LOW, false)
        val store = TestStore(State("best programming language"), AskFeature.reducer(AskScriptureClient { empty }, StubGraphClient()))
        store.send(Action.Started) { it.copy(content = Content.Asking) }
        store.receive(Action.Response(empty)) { it.copy(content = Content.Answered(Page(empty))) }
        assertTrue(empty.isEmpty)
        // §21.3: the fallback is the search results, with the same question.
        store.send(Action.SearchInsteadTapped)
        store.receive(Action.Delegate(DelegateAction.SearchInstead("best programming language")))
        store.finish()
    }

    @Test
    fun failuresAreNamedAndRetryable() = runTest {
        var attempts = 0
        val client = AskScriptureClient {
            attempts += 1
            when (attempts) {
                1 -> throw AskScriptureException.Unavailable
                2 -> throw AskScriptureException.NetworkUnavailable
                else -> throw IllegalStateException("boom")
            }
        }
        val store = TestStore(State("what is grace"), AskFeature.reducer(client, StubGraphClient()))
        store.send(Action.Started) { it.copy(content = Content.Asking) }
        store.receive(Action.Failed(AskScriptureException.Unavailable)) { it.copy(content = Content.Failed(AskScriptureException.Unavailable)) }
        store.send(Action.RetryTapped) { it.copy(content = Content.Asking) }
        store.receive(Action.Failed(AskScriptureException.NetworkUnavailable)) { it.copy(content = Content.Failed(AskScriptureException.NetworkUnavailable)) }
        store.send(Action.RetryTapped) { it.copy(content = Content.Asking) }
        store.receive(Action.Failed(AskScriptureException.Failed)) { it.copy(content = Content.Failed(AskScriptureException.Failed)) }
        store.finish()
    }

    @Test
    fun tapsBecomeDelegates() = runTest {
        val store = TestStore(State("q"), AskFeature.reducer(AskScriptureClient { preview }, StubGraphClient()))
        val reference = PassageReference("1Sam", 17, 45..47)
        store.send(Action.PassageTapped(reference))
        store.receive(Action.Delegate(DelegateAction.OpenPassage(reference)))
        store.send(Action.EntityTapped(david))
        store.receive(Action.Delegate(DelegateAction.OpenEntity(david)))
        store.finish()
    }

    @Test
    fun questionsAreToldFromLookups() {
        assertTrue(AskFeature.looksLikeQuestion("why did Job suffer"))
        assertTrue(AskFeature.looksLikeQuestion("Did Jesus abolish the Law?"))
        assertTrue(AskFeature.looksLikeQuestion("what does the Bible say about wealth"))
        assertTrue(AskFeature.looksLikeQuestion("por que Jó sofreu"))
        assertTrue(AskFeature.looksLikeQuestion("the parable of the prodigal son"))
        assertFalse(AskFeature.looksLikeQuestion("David"))
        assertFalse(AskFeature.looksLikeQuestion("1 Samuel 17"))
        assertFalse(AskFeature.looksLikeQuestion("Jn 3:16"))
        assertFalse(AskFeature.looksLikeQuestion("Valley of Elah"))
        assertFalse(AskFeature.looksLikeQuestion("why?"))
    }
}
