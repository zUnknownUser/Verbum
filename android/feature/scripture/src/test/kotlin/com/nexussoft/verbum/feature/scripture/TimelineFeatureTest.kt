package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.clients.TimelineClient
import com.nexussoft.verbum.common.arch.TestStore
import com.nexussoft.verbum.feature.scripture.TimelineFeature.Action
import com.nexussoft.verbum.feature.scripture.TimelineFeature.Content
import com.nexussoft.verbum.feature.scripture.TimelineFeature.DelegateAction
import com.nexussoft.verbum.feature.scripture.TimelineFeature.State
import com.nexussoft.verbum.feature.scripture.TimelineFeature.reducer
import com.nexussoft.verbum.models.BibleEntity
import com.nexussoft.verbum.models.BibleEntityType
import com.nexussoft.verbum.models.EntityId
import com.nexussoft.verbum.models.TimelineDatePrecision
import com.nexussoft.verbum.models.TimelineEvent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TimelineFeatureTest {
    private val david = TimelineEvent("t.david", "Reign of David", -1010, -970, TimelineDatePrecision.APPROXIMATE, "s", listOf("fixture.person.david"))
    private val exodus = TimelineEvent("t.exodus", "The Exodus", -1446, -1250, TimelineDatePrecision.DEBATED, "s", listOf("fixture.person.moses"))
    private val crucifixion = TimelineEvent("t.cross", "Crucifixion", 30, 33, TimelineDatePrecision.DEBATED, "s", listOf("fixture.person.jesus"))
    private val temple = TimelineEvent("t.temple", "Second Temple", -516, 70, TimelineDatePrecision.APPROXIMATE, null, emptyList())
    private val samaria = TimelineEvent("t.samaria", "Fall of Samaria", -722, null, TimelineDatePrecision.APPROXIMATE, null, emptyList())
    private val unknown = TimelineEvent("t.unknown", "Job", null, null, TimelineDatePrecision.UNKNOWN, null, emptyList())

    private class FakeTimeline(val events: List<TimelineEvent>? = null) : TimelineClient {
        override suspend fun events(): List<TimelineEvent> = events ?: error("offline")
        override suspend fun eventsFor(entityId: EntityId): List<TimelineEvent> = events().filter { entityId in it.entityIds }
    }

    private fun namedGraph() = StubGraphClient().let { stub ->
        object : com.nexussoft.verbum.clients.GraphClient by stub {
            override suspend fun entity(id: EntityId) = BibleEntity(id, BibleEntityType.PERSON, id.substringAfterLast('.').replaceFirstChar { it.uppercase() }, null)
        }
    }

    @Test fun loadsResolvesNamesAndOpensRowsInPlace() = runTest {
        val store = TestStore(State(), reducer(FakeTimeline(listOf(exodus, david)), namedGraph()))
        store.send(Action.Started) { it.copy(content = Content.Loading) }
        store.receive(Action.EventsLoaded(listOf(exodus, david))) { it.copy(content = Content.Loaded(listOf(exodus, david))) }
        store.receive(Action.NamesLoaded(mapOf("fixture.person.david" to "David", "fixture.person.moses" to "Moses"))) {
            it.copy(entityNames = mapOf("fixture.person.david" to "David", "fixture.person.moses" to "Moses"))
        }
        store.send(Action.EventTapped(david.id)) { it.copy(selectedId = david.id) }
        store.send(Action.EventTapped(exodus.id)) { it.copy(selectedId = exodus.id) }
        store.send(Action.EventTapped(exodus.id)) { it.copy(selectedId = null) }
        store.send(Action.EntityTapped("fixture.person.david"))
        store.receive(Action.Delegate(DelegateAction.OpenEntity("fixture.person.david")))
        store.send(Action.Started)
        store.finish()
    }

    @Test fun openedFromAnEntityItsFirstEventStartsOpen() = runTest {
        val graph = StubGraphClient()
        val store = TestStore(State(highlight = "fixture.person.david"), reducer(FakeTimeline(listOf(exodus, david)), graph))
        store.send(Action.Started) { it.copy(content = Content.Loading) }
        store.receive(Action.EventsLoaded(listOf(exodus, david))) { it.copy(content = Content.Loaded(listOf(exodus, david)), selectedId = david.id) }
        store.receive(Action.NamesLoaded(emptyMap()))
        assertEquals(david.id, store.state.highlightedEventId)
        store.finish()
    }

    @Test fun failureCanRetry() = runTest {
        val store = TestStore(State(), reducer(FakeTimeline(), StubGraphClient()))
        store.send(Action.Started) { it.copy(content = Content.Loading) }
        store.receive(Action.EventsFailed) { it.copy(content = Content.Failed) }
        store.send(Action.RetryTapped) { it.copy(content = Content.Loading) }
        store.receive(Action.EventsFailed) { it.copy(content = Content.Failed) }
        store.finish()
    }

    @Test fun datesReadWithTheirUncertainty() {
        assertEquals("c. 1010–970 BC", TimelineDates.text(david))
        assertEquals("1446–1250 BC · debated", TimelineDates.text(exodus))
        assertEquals("AD 30–33 · debated", TimelineDates.text(crucifixion))
        assertEquals("c. 516 BC – AD 70", TimelineDates.text(temple))
        assertEquals("c. 722 BC", TimelineDates.text(samaria))
        assertEquals("date unknown", TimelineDates.text(unknown))
    }
}
