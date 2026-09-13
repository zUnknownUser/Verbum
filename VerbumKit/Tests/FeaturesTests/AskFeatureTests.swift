import Clients
import ComposableArchitecture
import Foundation
import Models
import Testing
@testable import Features

@MainActor
@Suite struct AskFeatureTests {
    nonisolated static let david = BibleEntity(id: "fixture.person.david", type: .person, name: "David", summary: nil)
    nonisolated static let goliath = BibleEntity(id: "fixture.person.goliath", type: .person, name: "Goliath", summary: nil)

    @Test func appearingAsksOnceAndResolvesTheEntitiesItCites() async {
        let asked = LockIsolated<[String]>([])
        let store = TestStore(initialState: AskFeature.State(question: "  How did David defeat Goliath? ")) {
            AskFeature()
        } withDependencies: {
            $0.askScriptureClient.ask = { question in
                asked.withValue { $0.append(question) }
                return .preview
            }
            $0.graphClient.entity = { id in
                switch id {
                case "fixture.person.david": Self.david
                case "fixture.person.goliath": Self.goliath
                default: throw GraphClientError.unknownEntity(id)
                }
            }
        }
        #expect(store.state.question == "How did David defeat Goliath?")

        await store.send(.task) { $0.content = .asking }
        await store.receive(\.response.success) { $0.content = .answered(.init(answer: .preview)) }
        await store.receive(\.entitiesResolved) { $0.content = .answered(.init(answer: .preview, entities: [Self.david, Self.goliath])) }
        #expect(asked.value == ["How did David defeat Goliath?"])

        // A second appearance (tab switch, Back) does not ask again (§47: minimal retention, no repeats).
        await store.send(.task)
    }

    @Test func anIdTheGraphDoesNotKnowIsSimplyNotShown() async {
        let store = TestStore(initialState: AskFeature.State(question: "why did Job suffer")) {
            AskFeature()
        } withDependencies: {
            $0.askScriptureClient.ask = { _ in .preview }
            $0.graphClient.entity = { id in
                guard id == "fixture.person.david" else { throw GraphClientError.unknownEntity(id) }
                return Self.david
            }
        }
        await store.send(.task) { $0.content = .asking }
        await store.receive(\.response.success) { $0.content = .answered(.init(answer: .preview)) }
        await store.receive(\.entitiesResolved) { $0.content = .answered(.init(answer: .preview, entities: [Self.david])) }
    }

    @Test func nothingToStandBehindIsAnAnsweredPageNotAFailure() async {
        let empty = ScriptureAnswer(answer: "", summary: "No sufficiently relevant Scripture passages were found for this question.", passageReferences: [], entityReferences: [], sourceReferences: [], confidence: .low, interpretiveVariance: false)
        let store = TestStore(initialState: AskFeature.State(question: "best programming language")) {
            AskFeature()
        } withDependencies: {
            $0.askScriptureClient.ask = { _ in empty }
        }
        await store.send(.task) { $0.content = .asking }
        await store.receive(\.response.success) { $0.content = .answered(.init(answer: empty)) }
        #expect(empty.isEmpty)
        // §21.3: the fallback is the search results, with the same question.
        await store.send(.searchInsteadTapped)
        await store.receive(\.delegate.searchInstead) 
    }

    @Test func failuresAreNamedAndRetryable() async {
        let attempts = LockIsolated(0)
        let store = TestStore(initialState: AskFeature.State(question: "what is grace")) {
            AskFeature()
        } withDependencies: {
            $0.askScriptureClient.ask = { _ in
                attempts.withValue { $0 += 1 }
                if attempts.value == 1 { throw AskScriptureError.unavailable }
                if attempts.value == 2 { throw AskScriptureError.networkUnavailable }
                throw URLError(.badServerResponse)
            }
        }
        await store.send(.task) { $0.content = .asking }
        await store.receive(\.response.failure) { $0.content = .failed(.unavailable) }
        await store.send(.retryTapped) { $0.content = .asking }
        await store.receive(\.response.failure) { $0.content = .failed(.networkUnavailable) }
        await store.send(.retryTapped) { $0.content = .asking }
        await store.receive(\.response.failure) { $0.content = .failed(.failed) }
    }

    @Test func tapsBecomeDelegates() async {
        let store = TestStore(initialState: AskFeature.State(question: "q")) { AskFeature() }
        let reference = PassageReference(bookId: "1Sam", chapter: 17, verses: 45...47)
        await store.send(.passageTapped(reference))
        await store.receive(\.delegate.openPassage)
        await store.send(.entityTapped(Self.david))
        await store.receive(\.delegate.openEntity)
    }

    @Test func questionsAreToldFromLookups() {
        #expect(AskFeature.State.looksLikeQuestion("why did Job suffer"))
        #expect(AskFeature.State.looksLikeQuestion("Did Jesus abolish the Law?"))
        #expect(AskFeature.State.looksLikeQuestion("what does the Bible say about wealth"))
        #expect(AskFeature.State.looksLikeQuestion("por que Jó sofreu"))
        #expect(AskFeature.State.looksLikeQuestion("the parable of the prodigal son"))
        #expect(!AskFeature.State.looksLikeQuestion("David"))
        #expect(!AskFeature.State.looksLikeQuestion("1 Samuel 17"))
        #expect(!AskFeature.State.looksLikeQuestion("Jn 3:16"))
        #expect(!AskFeature.State.looksLikeQuestion("Valley of Elah"))
        #expect(!AskFeature.State.looksLikeQuestion("why?"))
    }
}
