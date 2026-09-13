package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.clients.AskScriptureClient
import com.nexussoft.verbum.clients.AskScriptureException
import com.nexussoft.verbum.clients.PreviewAskScriptureClient
import com.nexussoft.verbum.clients.RealtimeSession
import com.nexussoft.verbum.clients.RealtimeSessionClient
import com.nexussoft.verbum.clients.SearchClient
import com.nexussoft.verbum.clients.VoiceClient
import com.nexussoft.verbum.clients.VoiceConfiguration
import com.nexussoft.verbum.clients.VoiceEvent
import com.nexussoft.verbum.clients.VoiceException
import com.nexussoft.verbum.clients.VoiceToolHandler
import com.nexussoft.verbum.common.arch.TestStore
import com.nexussoft.verbum.feature.scripture.VoiceFeature.Action
import com.nexussoft.verbum.feature.scripture.VoiceFeature.DelegateAction
import com.nexussoft.verbum.feature.scripture.VoiceFeature.Line
import com.nexussoft.verbum.feature.scripture.VoiceFeature.Phase
import com.nexussoft.verbum.feature.scripture.VoiceFeature.State
import com.nexussoft.verbum.models.BibleEntity
import com.nexussoft.verbum.models.BibleEntityType
import com.nexussoft.verbum.models.BiblePassage
import com.nexussoft.verbum.models.BookLanguage
import com.nexussoft.verbum.models.EntityDetail
import com.nexussoft.verbum.models.PassageReference
import com.nexussoft.verbum.models.SearchResponse
import com.nexussoft.verbum.models.VoiceContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VoiceFeatureTest {
    private val sam17 = PassageReference("1Sam", 17)
    private val session = RealtimeSession("ek_test", 0, "gpt-realtime")
    private val preview = PreviewAskScriptureClient.answer

    /**
     * A scripted [VoiceClient]: records the configuration and tool handler, replays [events].
     * The TestStore runs effects to completion inside `send`, so the script is finite and given up front.
     */
    private class FakeVoice(vararg scripted: VoiceEvent) : VoiceClient {
        val events = Channel<VoiceEvent>(Channel.UNLIMITED).apply { scripted.forEach { trySend(it) }; close() }
        var configuration: VoiceConfiguration? = null
        var tools: VoiceToolHandler? = null
        var stopped = 0
        var muted: Boolean? = null
        var failWith: VoiceException? = null
        override suspend fun start(session: RealtimeSession, configuration: VoiceConfiguration, tools: VoiceToolHandler): Flow<VoiceEvent> {
            failWith?.let { throw it }
            this.configuration = configuration; this.tools = tools
            return events.receiveAsFlow()
        }
        override suspend fun setMuted(muted: Boolean) { this.muted = muted }
        override suspend fun stop() { stopped++ }
    }

    private fun deps(
        voice: FakeVoice,
        sessions: RealtimeSessionClient = RealtimeSessionClient { session },
        ask: AskScriptureClient = AskScriptureClient { preview },
        search: SearchClient = SearchClient { SearchResponse.empty(it) },
        chapter: List<BiblePassage> = emptyList(),
        language: BookLanguage = BookLanguage.ENGLISH,
    ) = VoiceFeature.Dependencies(sessions, voice, ask, search, StubBibleClient(chapterStub = { _, _ -> chapter }), { language })

    @Test
    fun aConversationBecomesTranscriptAndStatus() = runTest {
        val voice = FakeVoice(
            VoiceEvent.Listening, VoiceEvent.UserSpeaking(true), VoiceEvent.UserSaid("Who is Goliath?"), VoiceEvent.AssistantSpeaking(true),
            VoiceEvent.AssistantDelta("The Phil"), VoiceEvent.AssistantSaid("The Philistine champion."), VoiceEvent.ToolCalled("ask_scripture"), VoiceEvent.AssistantSpeaking(false),
        )
        val verse = BiblePassage("1", "WEB", "1Sam", 17, 1, 1, "Now the Philistines gathered.")
        val store = TestStore(State(VoiceContext.Chapter(sam17)), VoiceFeature.reducer(deps(voice, chapter = listOf(verse), language = BookLanguage.PORTUGUESE)))

        store.send(Action.Started) { it.copy(phase = Phase.Connecting) }
        store.receive(Action.Connected)
        val config = assertNotNull(voice.configuration)
        assertEquals("pt", config.language)
        assertTrue(config.instructions.contains("1 Samuel 17"))
        assertTrue(config.instructions.contains("1 Now the Philistines gathered."))
        assertTrue(config.instructions.contains("Never claim revelation"))
        assertEquals(listOf("ask_scripture", "search_scripture", "open_passage"), config.tools.map { it.name })
        assertTrue(config.opening!!.contains("1 Samuel 17"))

        store.receive(Action.Event(VoiceEvent.Listening)) { it.copy(phase = Phase.Listening) }
        store.receive(Action.Event(VoiceEvent.UserSpeaking(true))) { it.copy(isUserSpeaking = true) }
        store.receive(Action.Event(VoiceEvent.UserSaid("Who is Goliath?"))) { it.copy(lines = listOf(Line(0, Line.Role.USER, "Who is Goliath?"))) }
        store.receive(Action.Event(VoiceEvent.AssistantSpeaking(true))) { it.copy(phase = Phase.Speaking) }
        store.receive(Action.Event(VoiceEvent.AssistantDelta("The Phil"))) { it.copy(partial = "The Phil") }
        store.receive(Action.Event(VoiceEvent.AssistantSaid("The Philistine champion."))) {
            it.copy(partial = "", lines = it.lines + Line(1, Line.Role.COMPANION, "The Philistine champion."))
        }
        store.receive(Action.Event(VoiceEvent.ToolCalled("ask_scripture"))) { it.copy(phase = Phase.Thinking) }
        store.receive(Action.Event(VoiceEvent.AssistantSpeaking(false))) { it.copy(phase = Phase.Listening) }

        store.send(Action.MuteToggled) { it.copy(isMuted = true) }
        assertEquals(true, voice.muted)
        store.send(Action.EndTapped) { it.copy(phase = Phase.Ended) }
        assertTrue(voice.stopped >= 1)
        store.finish()
    }

    @Test
    fun toolsMentionAndOpenPassages() = runTest {
        val voice = FakeVoice()
        val detail = EntityDetail(BibleEntity("fixture.person.david", BibleEntityType.PERSON, "David", "King."), role = "King of Israel", keyPassages = listOf(sam17))
        val search = SearchClient { SearchResponse(it, listOf(PassageReference("Ps", 23, 1..1)), emptyList(), emptyList()) }
        val store = TestStore(State(VoiceContext.Entity(detail)), VoiceFeature.reducer(deps(voice, search = search)))
        store.send(Action.Started) { it.copy(phase = Phase.Connecting) }
        store.receive(Action.Connected)
        val config = assertNotNull(voice.configuration)
        assertTrue(config.instructions.contains("Role: King of Israel."))
        assertTrue(config.instructions.contains("Key passages: 1 Samuel 17."))

        val tools = assertNotNull(voice.tools)
        val asked = tools.run("ask_scripture", """{"question":"how did David win"}""")
        assertTrue(asked.contains(""""passages":["1 Samuel 17:45-47","1 Samuel 17:49-50"]"""), asked)
        assertTrue(asked.contains(""""confidence":"high""""))
        store.receive(Action.PassagesMentioned(preview.passageReferences)) { it.copy(passages = preview.passageReferences) }

        val searched = tools.run("search_scripture", """{"query":"shepherd"}""")
        assertTrue(searched.contains(""""passages":["Psalms 23:1"]"""), searched)
        store.receive(Action.PassagesMentioned(listOf(PassageReference("Ps", 23, 1..1)))) { it.copy(passages = it.passages + PassageReference("Ps", 23, 1..1)) }

        assertEquals("""{"opened":"John 3:16"}""", tools.run("open_passage", """{"reference":"John 3:16"}"""))
        val john = PassageReference("John", 3, 16..16)
        store.receive(Action.OpenRequested(john)) { it.copy(passages = it.passages + john) }
        store.receive(Action.Delegate(DelegateAction.OpenPassage(john)))

        assertEquals("""{"error":"not a reference I can open"}""", tools.run("open_passage", """{"reference":"nonsense"}"""))
        assertEquals("""{"error":"unknown tool nope"}""", tools.run("nope", "{}"))

        store.send(Action.EndTapped) { it.copy(phase = Phase.Ended) }
        store.finish()
    }

    @Test
    fun anAskFailureIsToldToTheModelNotInvented() = runTest {
        val voice = FakeVoice()
        val store = TestStore(State(VoiceContext.Answer("why", preview)), VoiceFeature.reducer(deps(voice, ask = AskScriptureClient { throw AskScriptureException.Unavailable })))
        store.send(Action.Started) { it.copy(phase = Phase.Connecting) }
        store.receive(Action.Connected)
        assertTrue(assertNotNull(voice.tools).run("ask_scripture", """{"question":"q"}""").contains("do not answer from memory"))
        store.send(Action.EndTapped) { it.copy(phase = Phase.Ended) }
        store.finish()
    }

    @Test
    fun failuresAreNamedAndRetryable() = runTest {
        val voice = FakeVoice().apply { failWith = VoiceException.MicrophoneDenied }
        var attempts = 0
        val sessions = RealtimeSessionClient { attempts += 1; if (attempts == 1) throw VoiceException.Unavailable else session }
        val store = TestStore(State(VoiceContext.Chapter(sam17)), VoiceFeature.reducer(deps(voice, sessions = sessions)))
        store.send(Action.Started) { it.copy(phase = Phase.Connecting) }
        store.receive(Action.Failed(VoiceException.Unavailable)) { it.copy(phase = Phase.Failed(VoiceException.Unavailable)) }
        store.send(Action.RetryTapped) { it.copy(phase = Phase.Connecting) }
        store.receive(Action.Failed(VoiceException.MicrophoneDenied)) { it.copy(phase = Phase.Failed(VoiceException.MicrophoneDenied)) }
        store.finish()
    }

    @Test
    fun aDroppedSocketEndsTheConversation() = runTest {
        val voice = FakeVoice(VoiceEvent.Listening, VoiceEvent.Failed(VoiceException.NetworkUnavailable))
        val store = TestStore(State(VoiceContext.Chapter(sam17)), VoiceFeature.reducer(deps(voice)))
        store.send(Action.Started) { it.copy(phase = Phase.Connecting) }
        store.receive(Action.Connected)
        store.receive(Action.Event(VoiceEvent.Listening)) { it.copy(phase = Phase.Listening) }
        store.receive(Action.Event(VoiceEvent.Failed(VoiceException.NetworkUnavailable))) { it.copy(phase = Phase.Failed(VoiceException.NetworkUnavailable)) }
        store.finish()
    }
}
