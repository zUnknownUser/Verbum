import Clients
import ComposableArchitecture
import Foundation
import Models

/// One question, one answer (spec §13, §21.3). The page is a destination pushed
/// from Search; it asks once when it appears, renders the §30 contract from its
/// structured fields, and sends every passage to the reader and every entity to
/// its page. When the server has nothing it can stand behind, the page says so
/// and offers the search results instead (§51: trust over always answering).
@Reducer
public struct AskFeature {
    @ObservableState
    public struct State: Equatable {
        public let question: String
        public let reference: PassageReference?
        public var content: Content = .idle

        public init(question: String, reference: PassageReference? = nil) {
            self.reference = reference
            self.question = question.trimmingCharacters(in: .whitespacesAndNewlines)
        }
    }

    public enum Content: Equatable, Sendable {
        case idle
        case asking
        case answered(Page)
        case failed(AskScriptureError)
    }

    /// The answer plus the entities it points at, resolved to names for the
    /// "Explore further" links (§13.2). Resolution is best-effort: an id the
    /// graph does not know is simply not shown.
    public struct Page: Equatable, Sendable {
        public let answer: ScriptureAnswer
        public var entities: [BibleEntity]

        public init(answer: ScriptureAnswer, entities: [BibleEntity] = []) {
            self.answer = answer
            self.entities = entities
        }
    }

    public enum Action: Equatable {
        case task
        case retryTapped
        case response(Result<ScriptureAnswer, AskScriptureError>)
        case entitiesResolved([BibleEntity])
        case passageTapped(PassageReference)
        case entityTapped(BibleEntity)
        case searchInsteadTapped
        case talkTapped
        case delegate(Delegate)

        @CasePathable
        public enum Delegate: Equatable {
            case openPassage(PassageReference)
            case openEntity(BibleEntity)
            /// §21.3: "failure gracefully falls back to search results".
            case searchInstead(String)
            /// Go on from this answer out loud.
            case talk(question: String, ScriptureAnswer)
        }
    }

    @Dependency(\.askScriptureClient) var askScriptureClient
    @Dependency(\.graphClient) var graphClient

    public init() {}

    public var body: some ReducerOf<Self> {
        Reduce { state, action in
            switch action {
            case .task:
                guard state.content == .idle else { return .none }
                return ask(&state)

            case .retryTapped:
                return ask(&state)

            case .response(.success(let answer)):
                state.content = .answered(Page(answer: answer))
                let ids = answer.entityReferences
                guard !ids.isEmpty else { return .none }
                return .run { [graphClient] send in
                    var entities: [BibleEntity] = []
                    for id in ids.prefix(8) {
                        if let entity = try? await graphClient.entity(id: id) { entities.append(entity) }
                    }
                    await send(.entitiesResolved(entities))
                }
                .cancellable(id: CancelID.entities, cancelInFlight: true)

            case .response(.failure(let error)):
                state.content = .failed(error)
                return .none

            case .entitiesResolved(let entities):
                guard case .answered(var page) = state.content else { return .none }
                page.entities = entities
                state.content = .answered(page)
                return .none

            case .passageTapped(let reference):
                return .send(.delegate(.openPassage(reference)))

            case .entityTapped(let entity):
                return .send(.delegate(.openEntity(entity)))

            case .searchInsteadTapped:
                return .send(.delegate(.searchInstead(state.question)))

            case .talkTapped:
                guard case .answered(let page) = state.content, !page.answer.isEmpty else { return .none }
                return .send(.delegate(.talk(question: state.question, page.answer)))

            case .delegate:
                return .none
            }
        }
    }

    private func ask(_ state: inout State) -> Effect<Action> {
        state.content = .asking
        return .run { [askScriptureClient, question = state.question, reference = state.reference] send in
            do {
                let answer: ScriptureAnswer
                if let reference { answer = try await askScriptureClient.askAbout(question: question, reference: reference) }
                else { answer = try await askScriptureClient.ask(question: question) }
                await send(.response(.success(answer)))
            } catch let error as AskScriptureError {
                await send(.response(.failure(error)))
            } catch is CancellationError {
                return
            } catch {
                await send(.response(.failure(.failed)))
            }
        }
        .cancellable(id: CancelID.ask, cancelInFlight: true)
    }

    private enum CancelID { case ask, entities }
}

extension AskFeature.State {
    /// Whether a query reads as a question for Ask rather than a lookup for
    /// Search (§6: both are reachable from the same field): a question mark, a
    /// question word, or several words that are not a reference or a name.
    public static func looksLikeQuestion(_ query: String) -> Bool {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count >= 8 else { return false }
        if trimmed.hasSuffix("?") { return true }
        let words = trimmed.lowercased().split(whereSeparator: { $0.isWhitespace })
        guard let first = words.first else { return false }
        let questionWords: Set<String> = [
            "why", "what", "how", "who", "where", "when", "which", "does", "did", "is", "are", "can", "should",
            "por", "porque", "o", "que", "como", "quem", "onde", "quando", "qual", "quais",
        ]
        return questionWords.contains(String(first)) || words.count >= 4
    }
}
