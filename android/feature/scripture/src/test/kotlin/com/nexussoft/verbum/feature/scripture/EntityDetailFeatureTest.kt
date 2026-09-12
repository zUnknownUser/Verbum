package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.common.arch.TestStore
import com.nexussoft.verbum.feature.scripture.EntityDetailFeature.Action
import com.nexussoft.verbum.feature.scripture.EntityDetailFeature.Content
import com.nexussoft.verbum.feature.scripture.EntityDetailFeature.DelegateAction
import com.nexussoft.verbum.feature.scripture.EntityDetailFeature.Page
import com.nexussoft.verbum.feature.scripture.EntityDetailFeature.State
import com.nexussoft.verbum.models.BibleEntity
import com.nexussoft.verbum.models.BibleEntityType
import com.nexussoft.verbum.models.BibleRelationship
import com.nexussoft.verbum.models.EntityDetail
import com.nexussoft.verbum.models.GraphSnapshot
import com.nexussoft.verbum.models.PassageReference
import com.nexussoft.verbum.models.RelationshipType
import com.nexussoft.verbum.models.SourceReference
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

internal object EntityFixtures {
    val david = BibleEntity("fixture.person.david", BibleEntityType.PERSON, "David", "King.")
    val goliath = BibleEntity("fixture.person.goliath", BibleEntityType.PERSON, "Goliath", null)
    val sam17 = BibleEntity("passage.1Sam.17", BibleEntityType.PASSAGE, "1 Samuel 17", null)
    val source = SourceReference("fixture.source.web", "WEB", null)
    val page = Page(
        detail = EntityDetail(david, role = "King", keyPassages = listOf(PassageReference("1Sam", 17)), sources = listOf(source)),
        neighborhood = GraphSnapshot(
            david, listOf(goliath, sam17),
            listOf(
                BibleRelationship("e1", david.id, goliath.id, RelationshipType.RELATED_TO, 1.0, listOf(source.id)),
                BibleRelationship("e2", david.id, sam17.id, RelationshipType.APPEARS_IN, 1.0, listOf(source.id)),
            ),
        ),
    )
}

class EntityDetailFeatureTest {
    @Test
    fun startedLoadsDetailAndNeighbourhood() = runTest {
        val store = TestStore(State(EntityFixtures.david.id), EntityDetailFeature.reducer(StubGraphClient(
            detailStub = { EntityFixtures.page.detail },
            neighborsStub = { id, limit -> assertEquals(EntityFixtures.david.id, id); assertEquals(12, limit); EntityFixtures.page.neighborhood },
        )))
        store.send(Action.Started) { it.copy(content = Content.Loading) }
        store.receive(Action.PageLoaded(EntityFixtures.page)) { it.copy(content = Content.Loaded(EntityFixtures.page)) }
        val page = (store.state.content as Content.Loaded).page
        assertEquals(listOf(EntityFixtures.goliath), page.related(BibleEntityType.PERSON))
        assertEquals(listOf(PassageReference("1Sam", 17)), page.passages)
        assertEquals(listOf(EntityFixtures.source), page.sources)
        store.finish()
    }

    @Test
    fun failureIsAStateNotAnErrorString() = runTest {
        val store = TestStore(State("x"), EntityDetailFeature.reducer(StubGraphClient(
            detailStub = { throw IllegalStateException("boom") },
            neighborsStub = { _, _ -> throw IllegalStateException("boom") },
        )))
        store.send(Action.Started) { it.copy(content = Content.Loading) }
        store.receive(Action.PageFailed) { it.copy(content = Content.Failed) }
        store.send(Action.RetryTapped) { it.copy(content = Content.Loading) }
        store.receive(Action.PageFailed) { it.copy(content = Content.Failed) }
        store.finish()
    }

    @Test
    fun passagesAndEntitiesBecomeDelegates() = runTest {
        val store = TestStore(State(EntityFixtures.david.id), EntityDetailFeature.reducer(StubGraphClient()))
        store.send(Action.PassageTapped(PassageReference("1Sam", 17)))
        store.receive(Action.Delegate(DelegateAction.OpenPassage(PassageReference("1Sam", 17))))
        store.send(Action.EntityTapped(EntityFixtures.goliath))
        store.receive(Action.Delegate(DelegateAction.OpenEntity(EntityFixtures.goliath)))
        store.send(Action.EntityTapped(EntityFixtures.sam17))
        store.receive(Action.Delegate(DelegateAction.OpenPassage(PassageReference("1Sam", 17))))
        store.finish()
    }

    @Test
    fun passagesFallBackToNeighbourhoodWhenDetailListsNone() {
        val page = Page(EntityDetail(EntityFixtures.goliath), GraphSnapshot(EntityFixtures.goliath, listOf(EntityFixtures.sam17), emptyList()))
        assertEquals(listOf(PassageReference("1Sam", 17)), page.passages)
    }
}

