import Clients
import ComposableArchitecture
import Foundation
import Models
import Testing
@testable import Features

@MainActor
@Suite struct VoiceFeatureTests {
    nonisolated static let sam17 = PassageReference(bookId: "1Sam", chapter: 17)
    nonisolated static let session = RealtimeSession(clientSecret: "ek_test", expiresAt: 0, model: "gpt-realtime")

    /// A scripted conversation: the feature hands the events to state, one line at a time.
    @Test func aConversationBecomesTranscriptAndStatus() async {
        let (events, continuation) = AsyncStream.makeStream(of: VoiceEvent.self)
        let configuration = LockIsolated<VoiceConfiguration?>(nil)
        let stopped = LockIsolated(false)
        let store = TestStore(initialState: VoiceFeature.State(context: .chapter(Self.sam17))) {
            VoiceFeature()
        } withDependencies: {
            $0.locale = Locale(identifier: "pt_BR")
            $0.bibleClient.chapter = { _, _ in [BiblePassage(id: "1", translationId: "WEB", bookId: "1Sam", chapter: 17, verseStart: 1, verseEnd: 1, text: "Now the Philistines gathered.")] }
            $0.realtimeSessionClient.create = { Self.session }
            $0.voiceClient.start = { session, config, _ in
                #expect(session == Self.session)
                configuration.setValue(config)
                return events
            }
            $0.voiceClient.setMuted = { _ in }
            $0.voiceClient.stop = { stopped.setValue(true) }
            $0.askScriptureClient.ask = { _ in .preview }
            $0.searchClient.search = { .empty($0) }
        }

        await store.send(.task) { $0.phase = .connecting }
        await store.receive(\.started)
        let config = configuration.value
        #expect(config?.language == "pt")
        #expect(config?.instructions.contains("1 Samuel 17") == true)
        #expect(config?.instructions.contains("1 Now the Philistines gathered.") == true)
        #expect(config?.instructions.contains("Never claim revelation") == true)
        #expect(config?.tools.map(\.name) == ["ask_scripture", "search_scripture", "open_passage"])
        #expect(config?.opening?.contains("1 Samuel 17") == true)

        continuation.yield(.listening)
        await store.receive(\.event) { $0.phase = .listening }
        continuation.yield(.userSpeaking(true))
        await store.receive(\.event) { $0.isUserSpeaking = true }
        continuation.yield(.userSaid("Who is Goliath?"))
        await store.receive(\.event) { $0.lines = [.init(id: 0, role: .user, text: "Who is Goliath?")] }
        continuation.yield(.assistantSpeaking(true))
        await store.receive(\.event) { $0.phase = .speaking }
        continuation.yield(.assistantDelta("The Phil"))
        await store.receive(\.event) { $0.partial = "The Phil" }
        continuation.yield(.assistantSaid("The Philistine champion."))
        await store.receive(\.event) {
            $0.partial = ""
            $0.lines.append(.init(id: 1, role: .companion, text: "The Philistine champion."))
        }
        continuation.yield(.toolCalled("ask_scripture"))
        await store.receive(\.event) { $0.phase = .thinking }
        continuation.yield(.assistantSpeaking(false))
        await store.receive(\.event) { $0.phase = .listening }

        await store.send(.muteToggled) { $0.isMuted = true }
        await store.send(.endTapped) { $0.phase = .ended }
        #expect(stopped.value)
    }

    @Test func toolsMentionAndOpenPassages() async {
        let (events, _) = AsyncStream.makeStream(of: VoiceEvent.self)
        let handler = LockIsolated<VoiceToolHandler?>(nil)
        let store = TestStore(initialState: VoiceFeature.State(context: .entity(EntityDetail(entity: BibleEntity(id: "fixture.person.david", type: .person, name: "David", summary: "King."), role: "King of Israel", keyPassages: [Self.sam17])))) {
            VoiceFeature()
        } withDependencies: {
            $0.locale = Locale(identifier: "en_US")
            $0.realtimeSessionClient.create = { Self.session }
            $0.voiceClient.start = { _, config, tools in
                #expect(config.instructions.contains("Role: King of Israel."))
                #expect(config.instructions.contains("Key passages: 1 Samuel 17."))
                handler.setValue(tools)
                return events
            }
            $0.voiceClient.stop = {}
            $0.bibleClient.chapter = { _, _ in [] }
            $0.askScriptureClient.ask = { _ in .preview }
            $0.searchClient.search = { query in SearchResponse(query: query, passages: [PassageReference(bookId: "Ps", chapter: 23, verses: 1...1)], books: [], entities: []) }
        }
        await store.send(.task) { $0.phase = .connecting }
        await store.receive(\.started)

        let tools = handler.value!
        let asked = await tools("ask_scripture", #"{"question":"how did David win"}"#)
        #expect(asked.contains(#""passages":["1 Samuel 17:45-47","1 Samuel 17:49-50"]"#))
        #expect(asked.contains(#""confidence":"high""#))
        await store.receive(\.passagesMentioned) { $0.passages = ScriptureAnswer.preview.passageReferences }

        let searched = await tools("search_scripture", #"{"query":"shepherd"}"#)
        #expect(searched.contains(#""passages":["Psalms 23:1"]"#))
        await store.receive(\.passagesMentioned) { $0.passages.append(PassageReference(bookId: "Ps", chapter: 23, verses: 1...1)) }

        let opened = await tools("open_passage", #"{"reference":"John 3:16"}"#)
        #expect(opened == #"{"opened":"John 3:16"}"#)
        await store.receive(\.openRequested) { $0.passages.append(PassageReference(bookId: "John", chapter: 3, verses: 16...16)) }
        await store.receive(\.delegate.openPassage)

        #expect(await tools("open_passage", #"{"reference":"nonsense"}"#) == #"{"error":"not a reference I can open"}"#)
        #expect(await tools("nope", "{}") == #"{"error":"unknown tool nope"}"#)

        await store.send(.endTapped) { $0.phase = .ended }
    }

    @Test func anAskFailureIsToldToTheModelNotInvented() async {
        let (events, _) = AsyncStream.makeStream(of: VoiceEvent.self)
        let handler = LockIsolated<VoiceToolHandler?>(nil)
        let store = TestStore(initialState: VoiceFeature.State(context: .answer(question: "why", .preview))) {
            VoiceFeature()
        } withDependencies: {
            $0.locale = Locale(identifier: "en_US")
            $0.realtimeSessionClient.create = { Self.session }
            $0.voiceClient.start = { _, _, tools in handler.setValue(tools); return events }
            $0.voiceClient.stop = {}
            $0.bibleClient.chapter = { _, _ in [] }
            $0.askScriptureClient.ask = { _ in throw AskScriptureError.unavailable }
            $0.searchClient.search = { .empty($0) }
        }
        await store.send(.task) { $0.phase = .connecting }
        await store.receive(\.started)
        let result = await handler.value!("ask_scripture", #"{"question":"q"}"#)
        #expect(result.contains("do not answer from memory"))
        await store.send(.endTapped) { $0.phase = .ended }
    }

    @Test func failuresAreNamedAndRetryable() async {
        let attempts = LockIsolated(0)
        let store = TestStore(initialState: VoiceFeature.State(context: .chapter(Self.sam17))) {
            VoiceFeature()
        } withDependencies: {
            $0.locale = Locale(identifier: "en_US")
            $0.bibleClient.chapter = { _, _ in throw BibleClientError.networkUnavailable }
            $0.realtimeSessionClient.create = {
                attempts.withValue { $0 += 1 }
                if attempts.value == 1 { throw VoiceError.unavailable }
                return Self.session
            }
            $0.voiceClient.start = { _, _, _ in throw VoiceError.microphoneDenied }
            $0.voiceClient.stop = {}
            $0.askScriptureClient.ask = { _ in .preview }
            $0.searchClient.search = { .empty($0) }
        }
        await store.send(.task) { $0.phase = .connecting }
        await store.receive(\.failed) { $0.phase = .failed(.unavailable) }
        await store.send(.retryTapped) { $0.phase = .connecting }
        await store.receive(\.failed) { $0.phase = .failed(.microphoneDenied) }
    }

    @Test func aDroppedSocketEndsTheConversation() async {
        let (events, continuation) = AsyncStream.makeStream(of: VoiceEvent.self)
        let store = TestStore(initialState: VoiceFeature.State(context: .chapter(Self.sam17))) {
            VoiceFeature()
        } withDependencies: {
            $0.locale = Locale(identifier: "en_US")
            $0.bibleClient.chapter = { _, _ in [] }
            $0.realtimeSessionClient.create = { Self.session }
            $0.voiceClient.start = { _, _, _ in events }
            $0.voiceClient.stop = {}
            $0.askScriptureClient.ask = { _ in .preview }
            $0.searchClient.search = { .empty($0) }
        }
        await store.send(.task) { $0.phase = .connecting }
        await store.receive(\.started)
        continuation.yield(.listening)
        await store.receive(\.event) { $0.phase = .listening }
        continuation.yield(.failed(.networkUnavailable))
        await store.receive(\.event) { $0.phase = .failed(.networkUnavailable) }
        continuation.finish()
        await store.finish()
    }
}
