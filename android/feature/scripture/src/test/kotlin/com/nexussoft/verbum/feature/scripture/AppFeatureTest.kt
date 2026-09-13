package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.clients.InMemoryPreferencesClient
import com.nexussoft.verbum.clients.helloao.ScriptureAudioClient
import com.nexussoft.verbum.common.arch.TestStore
import com.nexussoft.verbum.feature.scripture.AppFeature.Action
import com.nexussoft.verbum.feature.scripture.AppFeature.Destination
import com.nexussoft.verbum.feature.scripture.AppFeature.DestinationAction
import com.nexussoft.verbum.feature.scripture.AppFeature.Tab
import com.nexussoft.verbum.models.BibleBook
import com.nexussoft.verbum.models.BibleEntity
import com.nexussoft.verbum.models.BibleEntityType
import com.nexussoft.verbum.models.BookLanguage
import com.nexussoft.verbum.models.PassageReference
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import com.nexussoft.verbum.clients.NoopNotificationClient
import com.nexussoft.verbum.clients.NotificationClient
import kotlinx.coroutines.flow.flowOf
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AppFeatureTest {
    private val david = BibleEntity("fixture.person.david", BibleEntityType.PERSON, "David", null)
    private val sam17 = PassageReference("1Sam", 17)
    private val preferences = InMemoryPreferencesClient()

    private fun deps(bible: StubBibleClient = StubBibleClient(), graph: StubGraphClient = StubGraphClient()) = AppFeature.Dependencies(
        bibleClient = bible, clipboard = unimplementedClipboard, preferences = preferences, searchClient = unimplementedSearch, graphClient = graph,
        audioClient = ScriptureAudioClient { _, _ -> null }, player = FakePlayer(),
        clock = Clock.fixed(Instant.parse("2026-01-04T15:00:00Z"), ZoneId.of("UTC")), language = { BookLanguage.ENGLISH }, searchDebounceMs = 0,
    )

    private fun store(deps: AppFeature.Dependencies = deps()) = TestStore(AppFeature.State(), AppFeature.reducer(deps))

    private fun reader(reference: PassageReference) = Destination.Reader(ScriptureFeature.State.initial(reference))

    @Test
    fun tabsRememberTheLastContentTab() = runTest {
        val store = store()
        store.send(Action.TabChanged(Tab.EXPLORE)) { it.copy(tab = Tab.EXPLORE, contentTab = Tab.EXPLORE) }
        store.send(Action.TabChanged(Tab.SEARCH)) { it.copy(tab = Tab.SEARCH) }
        store.send(Action.TabChanged(Tab.LIBRARY)) { it.copy(tab = Tab.LIBRARY) }
        assertEquals(Tab.EXPLORE, store.state.contentTab)
        store.finish()
    }

    @Test
    fun searchResultsLandOnTheContentTabAndShowIt() = runTest {
        val store = store()
        store.send(Action.TabChanged(Tab.EXPLORE)) { it.copy(tab = Tab.EXPLORE, contentTab = Tab.EXPLORE) }
        store.send(Action.TabChanged(Tab.SEARCH)) { it.copy(tab = Tab.SEARCH) }
        store.send(Action.Search(SearchFeature.Action.PassageTapped(sam17)))
        store.receive(Action.Search(SearchFeature.Action.Delegate(SearchFeature.DelegateAction.OpenPassage(sam17)))) {
            it.copy(tab = Tab.EXPLORE, explorePath = listOf(reader(sam17)))
        }
        store.send(Action.TabChanged(Tab.SEARCH)) { it.copy(tab = Tab.SEARCH) }
        store.send(Action.Search(SearchFeature.Action.EntityTapped(david)))
        store.receive(Action.Search(SearchFeature.Action.Delegate(SearchFeature.DelegateAction.OpenEntity(david)))) {
            it.copy(tab = Tab.EXPLORE, explorePath = it.explorePath + Destination.Entity(EntityDetailFeature.State(david.id)))
        }
        store.finish()
    }

    /**
     * §13, §21.3: a question asked from Search opens Ask on the content tab; its passages chain on
     * the same stack; "See search results" returns to the field, which still holds the question.
     */
    @Test
    fun askOpensFromSearchAndFallsBackToIt() = runTest {
        val store = TestStore(AppFeature.State(tab = Tab.SEARCH, search = SearchFeature.State(query = "why did Job suffer")), AppFeature.reducer(deps()))
        store.send(Action.Search(SearchFeature.Action.AskTapped))
        store.receive(Action.Search(SearchFeature.Action.Delegate(SearchFeature.DelegateAction.Ask("why did Job suffer")))) {
            it.copy(tab = Tab.HOME, homePath = listOf(Destination.Ask(AskFeature.State("why did Job suffer"))))
        }
        store.send(Action.HomePath(0, DestinationAction.Ask(AskFeature.Action.PassageTapped(sam17))))
        store.receive(Action.HomePath(0, DestinationAction.Ask(AskFeature.Action.Delegate(AskFeature.DelegateAction.OpenPassage(sam17))))) {
            it.copy(homePath = it.homePath + reader(sam17))
        }
        store.send(Action.HomePath(0, DestinationAction.Ask(AskFeature.Action.SearchInsteadTapped)))
        store.receive(Action.HomePath(0, DestinationAction.Ask(AskFeature.Action.Delegate(AskFeature.DelegateAction.SearchInstead("why did Job suffer"))))) {
            it.copy(tab = Tab.SEARCH)
        }
        store.finish()
    }

    /** A conversation starts from the reader, an entity page or an Ask answer as a sheet; when the companion opens a passage the sheet goes and the reader comes. */
    @Test
    fun voiceStartsFromThreePlacesAndOpensPassages() = runTest {
        val detail = com.nexussoft.verbum.models.EntityDetail(david)
        val entity = EntityDetailFeature.State(david.id, EntityDetailFeature.Content.Loaded(EntityDetailFeature.Page(detail, com.nexussoft.verbum.models.GraphSnapshot(david, emptyList(), emptyList()))))
        val answer = com.nexussoft.verbum.clients.PreviewAskScriptureClient.answer
        val ask = AskFeature.State("why", AskFeature.Content.Answered(AskFeature.Page(answer)))
        val initial = AppFeature.State(
            tab = Tab.EXPLORE, contentTab = Tab.EXPLORE,
            explorePath = listOf(reader(sam17), Destination.Entity(entity), Destination.Ask(ask)),
        )
        val store = TestStore(initial, AppFeature.reducer(deps()))

        store.send(Action.ExplorePath(0, DestinationAction.Reader(ScriptureFeature.Action.Reader(ChapterReaderFeature.Action.TalkTapped))))
        store.receive(Action.ExplorePath(0, DestinationAction.Reader(ScriptureFeature.Action.Reader(ChapterReaderFeature.Action.Delegate(ChapterReaderFeature.DelegateAction.Talk(sam17))))))
        store.receive(Action.ExplorePath(0, DestinationAction.Reader(ScriptureFeature.Action.Delegate(ScriptureFeature.DelegateAction.Talk(sam17))))) {
            it.copy(voice = VoiceFeature.State(com.nexussoft.verbum.models.VoiceContext.Chapter(sam17)))
        }
        val ps23 = PassageReference("Ps", 23)
        store.send(Action.Voice(VoiceFeature.Action.PassageTapped(ps23)))
        store.receive(Action.Voice(VoiceFeature.Action.Delegate(VoiceFeature.DelegateAction.OpenPassage(ps23)))) {
            it.copy(voice = null, explorePath = it.explorePath + reader(ps23))
        }

        store.send(Action.ExplorePath(1, DestinationAction.Entity(EntityDetailFeature.Action.TalkTapped)))
        store.receive(Action.ExplorePath(1, DestinationAction.Entity(EntityDetailFeature.Action.Delegate(EntityDetailFeature.DelegateAction.Talk(detail))))) {
            it.copy(voice = VoiceFeature.State(com.nexussoft.verbum.models.VoiceContext.Entity(detail)))
        }
        store.send(Action.VoiceDismissed) { it.copy(voice = null) }

        store.send(Action.ExplorePath(2, DestinationAction.Ask(AskFeature.Action.TalkTapped)))
        store.receive(Action.ExplorePath(2, DestinationAction.Ask(AskFeature.Action.Delegate(AskFeature.DelegateAction.Talk("why", answer))))) {
            it.copy(voice = VoiceFeature.State(com.nexussoft.verbum.models.VoiceContext.Answer("why", answer)))
        }
        store.finish()
    }

    @Test
    fun homeGreetsByTheClockAndOpensPassagesOnItsOwnStack() = runTest {
        val store = store()
        store.send(Action.Home(HomeFeature.Action.Started)) {
            it.copy(home = it.home.copy(greeting = HomeFeature.Greeting.AFTERNOON))
        }
        val verse = store.state.home.dailyVerse.reference
        store.send(Action.Home(HomeFeature.Action.DailyVerse(DailyVerseFeature.Action.OpenTapped)))
        store.receive(Action.Home(HomeFeature.Action.DailyVerse(DailyVerseFeature.Action.Delegate(DailyVerseFeature.DelegateAction.OpenPassage(verse)))))
        store.receive(Action.Home(HomeFeature.Action.Delegate(HomeFeature.DelegateAction.OpenPassage(verse)))) {
            it.copy(homePath = listOf(reader(verse)))
        }
        store.send(Action.Home(HomeFeature.Action.SearchTapped))
        store.receive(Action.Home(HomeFeature.Action.Delegate(HomeFeature.DelegateAction.OpenSearch))) { it.copy(tab = Tab.SEARCH) }
        store.finish()
    }

    @Test
    fun continueReadingComesFromWhatTheReaderRecorded() = runTest {
        preferences.setString(ChapterReaderFeature.LAST_READ_KEY, "Rom 8")
        val store = store()
        store.send(Action.Home(HomeFeature.Action.Started)) {
            it.copy(home = it.home.copy(lastRead = PassageReference("Rom", 8), greeting = HomeFeature.Greeting.AFTERNOON))
        }
        store.send(Action.Home(HomeFeature.Action.ContinueReadingTapped))
        store.receive(Action.Home(HomeFeature.Action.Delegate(HomeFeature.DelegateAction.OpenPassage(PassageReference("Rom", 8))))) {
            it.copy(homePath = listOf(reader(PassageReference("Rom", 8))))
        }
        store.finish()
    }

    @Test
    fun destinationsChainOnTheSameStack() = runTest {
        // Explore → People → David → Goliath → 1 Samuel 17: the golden path (§75), one stack.
        val goliath = BibleEntity("fixture.person.goliath", BibleEntityType.PERSON, "Goliath", null)
        val store = store()
        store.send(Action.TabChanged(Tab.EXPLORE)) { it.copy(tab = Tab.EXPLORE, contentTab = Tab.EXPLORE) }
        store.send(Action.Explore(ExploreFeature.Action.EntryTapped(ExploreFeature.Entry.PEOPLE)))
        store.receive(Action.Explore(ExploreFeature.Action.Delegate(ExploreFeature.DelegateAction.Open(ExploreFeature.Entry.PEOPLE)))) {
            it.copy(explorePath = listOf(Destination.Entities(EntityListFeature.State(BibleEntityType.PERSON))))
        }
        store.send(Action.ExplorePath(0, DestinationAction.Entities(EntityListFeature.Action.EntityTapped(david))))
        store.receive(Action.ExplorePath(0, DestinationAction.Entities(EntityListFeature.Action.Delegate(EntityListFeature.DelegateAction.OpenEntity(david))))) {
            it.copy(explorePath = it.explorePath + Destination.Entity(EntityDetailFeature.State(david.id)))
        }
        store.send(Action.ExplorePath(1, DestinationAction.Entity(EntityDetailFeature.Action.EntityTapped(goliath))))
        store.receive(Action.ExplorePath(1, DestinationAction.Entity(EntityDetailFeature.Action.Delegate(EntityDetailFeature.DelegateAction.OpenEntity(goliath))))) {
            it.copy(explorePath = it.explorePath + Destination.Entity(EntityDetailFeature.State(goliath.id)))
        }
        store.send(Action.ExplorePath(2, DestinationAction.Entity(EntityDetailFeature.Action.PassageTapped(sam17))))
        store.receive(Action.ExplorePath(2, DestinationAction.Entity(EntityDetailFeature.Action.Delegate(EntityDetailFeature.DelegateAction.OpenPassage(sam17))))) {
            it.copy(explorePath = it.explorePath + reader(sam17))
        }
        assertEquals(4, store.state.explorePath.size)
        store.send(Action.Pop(Tab.EXPLORE)) { it.copy(explorePath = it.explorePath.dropLast(1)) }
        store.finish()
    }

    @Test
    fun shelfChapterOpensTheReader() = runTest {
        val samuel = assertNotNull(BibleBook.book("1Sam"))
        val store = store()
        store.send(Action.Explore(ExploreFeature.Action.EntryTapped(ExploreFeature.Entry.BOOKS)))
        store.receive(Action.Explore(ExploreFeature.Action.Delegate(ExploreFeature.DelegateAction.Open(ExploreFeature.Entry.BOOKS)))) {
            it.copy(explorePath = listOf(Destination.Books(BookPickerFeature.State(current = PassageReference("John", 3)))))
        }
        store.send(Action.ExplorePath(0, DestinationAction.Books(BookPickerFeature.Action.BookTapped(samuel)))) {
            it.copy(explorePath = listOf(Destination.Books(BookPickerFeature.State(current = PassageReference("John", 3), selectedBook = samuel))))
        }
        store.send(Action.ExplorePath(0, DestinationAction.Books(BookPickerFeature.Action.ChapterTapped(17))))
        store.receive(Action.ExplorePath(0, DestinationAction.Books(BookPickerFeature.Action.Delegate(BookPickerFeature.DelegateAction.ChapterSelected(sam17))))) {
            it.copy(explorePath = it.explorePath + reader(sam17))
        }
        store.finish()
    }

    @Test
    fun theGraphOpensFromAnEntityAndRefocusesOnTheSameStack() = runTest {
        val goliath = BibleEntity("fixture.person.goliath", BibleEntityType.PERSON, "Goliath", null)
        val store = store()
        store.send(Action.TabChanged(Tab.EXPLORE)) { it.copy(tab = Tab.EXPLORE, contentTab = Tab.EXPLORE) }
        store.send(Action.Explore(ExploreFeature.Action.EntryTapped(ExploreFeature.Entry.PEOPLE)))
        store.receive(Action.Explore(ExploreFeature.Action.Delegate(ExploreFeature.DelegateAction.Open(ExploreFeature.Entry.PEOPLE)))) {
            it.copy(explorePath = listOf(Destination.Entities(EntityListFeature.State(BibleEntityType.PERSON))))
        }
        store.send(Action.ExplorePath(0, DestinationAction.Entities(EntityListFeature.Action.EntityTapped(david))))
        store.receive(Action.ExplorePath(0, DestinationAction.Entities(EntityListFeature.Action.Delegate(EntityListFeature.DelegateAction.OpenEntity(david))))) {
            it.copy(explorePath = it.explorePath + Destination.Entity(EntityDetailFeature.State(david.id)))
        }
        store.send(Action.ExplorePath(1, DestinationAction.Entity(EntityDetailFeature.Action.GraphTapped)))
        store.receive(Action.ExplorePath(1, DestinationAction.Entity(EntityDetailFeature.Action.Delegate(EntityDetailFeature.DelegateAction.OpenGraph(david.id))))) {
            it.copy(explorePath = it.explorePath + Destination.Graph(GraphFeature.State(david.id)))
        }
        store.send(Action.ExplorePath(2, DestinationAction.Graph(GraphFeature.Action.NodeTapped(goliath))))
        store.receive(Action.ExplorePath(2, DestinationAction.Graph(GraphFeature.Action.Delegate(GraphFeature.DelegateAction.OpenEntity(goliath))))) {
            it.copy(explorePath = it.explorePath + Destination.Entity(EntityDetailFeature.State(goliath.id)))
        }
        store.send(Action.ExplorePath(2, DestinationAction.Graph(GraphFeature.Action.Delegate(GraphFeature.DelegateAction.Focus(goliath))))) {
            it.copy(explorePath = it.explorePath + Destination.Graph(GraphFeature.State(goliath.id)))
        }
        store.finish()
    }

    @Test
    fun theTimelineOpensFromExploreAndFromAnEntity() = runTest {
        val store = store()
        store.send(Action.Explore(ExploreFeature.Action.EntryTapped(ExploreFeature.Entry.TIMELINE)))
        store.receive(Action.Explore(ExploreFeature.Action.Delegate(ExploreFeature.DelegateAction.Open(ExploreFeature.Entry.TIMELINE)))) {
            it.copy(explorePath = listOf(Destination.Timeline(TimelineFeature.State())))
        }
        store.send(Action.ExplorePath(0, DestinationAction.Timeline(TimelineFeature.Action.EntityTapped(david.id))))
        store.receive(Action.ExplorePath(0, DestinationAction.Timeline(TimelineFeature.Action.Delegate(TimelineFeature.DelegateAction.OpenEntity(david.id))))) {
            it.copy(explorePath = it.explorePath + Destination.Entity(EntityDetailFeature.State(david.id)))
        }
        store.send(Action.ExplorePath(1, DestinationAction.Entity(EntityDetailFeature.Action.TimelineTapped)))
        store.receive(Action.ExplorePath(1, DestinationAction.Entity(EntityDetailFeature.Action.Delegate(EntityDetailFeature.DelegateAction.OpenTimeline(david.id))))) {
            it.copy(explorePath = it.explorePath + Destination.Timeline(TimelineFeature.State(highlight = david.id)))
        }
        store.finish()
    }

    @Test
    fun aTappedNotificationLandsOnHome() = runTest {
        // One tap, then the stream ends: TestStore runs effects to completion.
        val verse = PassageReference("John", 3, 16..16)
        val notifications = object : NotificationClient by NoopNotificationClient {
            override fun openedVerses() = flowOf(verse)
        }
        val store = store(deps().let { d ->
            AppFeature.Dependencies(
                d.bibleClient, d.clipboard, d.preferences, d.searchClient, d.graphClient, d.audioClient, d.player, d.clock, d.language,
                d.searchDebounceMs, d.initialTextScale, notifications = notifications,
            )
        })
        store.send(Action.TabChanged(Tab.EXPLORE)) { it.copy(tab = Tab.EXPLORE, contentTab = Tab.EXPLORE) }
        store.send(Action.Started)
        store.receive(Action.OpenedVerse(verse)) {
            it.copy(tab = Tab.HOME, contentTab = Tab.HOME, homePath = listOf(reader(verse)))
        }
        store.finish()
    }

    @Test
    fun greetingsFollowTheHour() {
        assertEquals(HomeFeature.Greeting.MORNING, HomeFeature.Greeting.at(8))
        assertEquals(HomeFeature.Greeting.AFTERNOON, HomeFeature.Greeting.at(13))
        assertEquals(HomeFeature.Greeting.EVENING, HomeFeature.Greeting.at(22))
        assertEquals(HomeFeature.Greeting.EVENING, HomeFeature.Greeting.at(3))
        assertEquals(PassageReference("Rom", 8), LastRead.decode(LastRead.encode(PassageReference("Rom", 8, 28..30))))
    }
}
