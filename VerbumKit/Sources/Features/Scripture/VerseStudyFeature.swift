import Clients
import ComposableArchitecture
import Foundation
import Models

/// A progressive study sheet. Existing clients supply Scripture, graph and Ask;
/// opening it never generates an AI answer or transmits a personal note.
@Reducer
public struct VerseStudyFeature {
    public enum Tab: String, CaseIterable, Equatable, Sendable { case highlight, note, compare, context, references, ask }
    @ObservableState
    public struct State: Equatable {
        public let reference: PassageReference
        public let text: String
        public let translationID: String
        public var annotation: ReaderAnnotation
        public var tab: Tab = .highlight
        public var context: PassageContext?
        public var contextLoading = false
        public var referencesLoaded = false
        public var contextFailed = false
        public var candidates: [BibleEntity] = []
        public var entity: EntityDetail?
        public var entityLoading = false
        public var entityFailed = false
        public var comparison: BiblePassage?
        public var comparisonLoading = false
        public var comparisonFailed = false
        public var savedNote = ""
        public var closeAfterSave = false
        public var passageAfterSave: PassageReference?
        public var saving = false
        public var saveFailed = false
        public var saved = false
        public var question = ""
        public var ask: AskFeature.State?
        public init(passage: BiblePassage, annotation: ReaderAnnotation?, context: PassageContext?, candidates: [BibleEntity] = []) {
            reference = PassageReference(bookId: passage.bookId, chapter: passage.chapter, verses: passage.verseStart...passage.verseEnd)
            text = passage.text; translationID = passage.translationId
            self.annotation = annotation ?? ReaderAnnotation(reference: reference)
            self.savedNote = annotation?.note ?? ""
            self.context = context; self.candidates = candidates
            if !candidates.isEmpty { tab = .context }
        }
    }
    public enum Action: Equatable {
        case task, save, closeEntity, done
        case tabChanged(Tab), highlightChanged(HighlightColor?), noteChanged(String), questionChanged(String)
        case contextResponse(PassageContext?), contextFailed
        case entityTapped(BibleEntity), entityResponse(EntityDetail), entityFailed
        case comparisonResponse(BiblePassage), comparisonFailed
        case saved(ReaderAnnotation), saveFailed
        case submitQuestion
        case ask(AskFeature.Action), closeAnswer
        case passageTapped(PassageReference)
        case delegate(Delegate)
        @CasePathable public enum Delegate: Equatable {
            case annotationSaved(ReaderAnnotation), openPassage(PassageReference), close
        }
    }
    @Dependency(\.contextClient) var contextClient
    @Dependency(\.graphClient) var graphClient
    @Dependency(\.bibleComparison) var bibleComparison
    @Dependency(\.readerAnnotations) var annotations
    public init() {}
    public var body: some ReducerOf<Self> {
        Reduce { state, action in
            switch action {
            case .done:
                if state.annotation.note != state.savedNote { state.closeAfterSave = true; return .send(.save) }
                return .send(.delegate(.close))
            case .task:
                if state.candidates.count == 1 { return .send(.entityTapped(state.candidates[0])) }
                return .none
            case .tabChanged(let tab):
                state.tab = tab; state.entity = nil; state.candidates = []
                if ((tab == .context && state.context == nil) || (tab == .references && !state.referencesLoaded)), !state.contextLoading {
                    state.contextLoading = true; state.contextFailed = false
                    return .run { [contextClient, reference = state.reference] send in
                        do { await send(.contextResponse(try await contextClient.chapter(reference: reference))) }
                        catch is CancellationError {} catch { await send(.contextFailed) }
                    }.cancellable(id: CancelID.context, cancelInFlight: true)
                }
                if tab == .compare, state.comparison == nil, !state.comparisonLoading {
                    state.comparisonLoading = true; state.comparisonFailed = false
                    return .run { [bibleComparison, reference = state.reference] send in
                        do { await send(.comparisonResponse(try await bibleComparison.passage(reference: reference))) }
                        catch is CancellationError {} catch { await send(.comparisonFailed) }
                    }.cancellable(id: CancelID.comparison, cancelInFlight: true)
                }
                return .none
            case .highlightChanged(let color):
                state.annotation.highlight = color; state.saved = false
                return .send(.save)
            case .noteChanged(let note):
                state.annotation.note = String(note.prefix(10_000)); state.saved = false
                return .none
            case .save:
                state.saving = true; state.saveFailed = false
                return .run { [annotations, annotation = state.annotation] send in
                    do { try await annotations.save(annotation: annotation); await send(.saved(annotation)) }
                    catch { await send(.saveFailed) }
                }.cancellable(id: CancelID.save, cancelInFlight: true)
            case .saved(let annotation):
                state.saving = false; state.saved = annotation == state.annotation
                state.savedNote = annotation.note
                if let reference = state.passageAfterSave {
                    state.passageAfterSave = nil
                    return .concatenate(.send(.delegate(.annotationSaved(annotation))), .send(.delegate(.openPassage(reference))))
                }
                if state.closeAfterSave { return .concatenate(.send(.delegate(.annotationSaved(annotation))), .send(.delegate(.close))) }
                return .send(.delegate(.annotationSaved(annotation)))
            case .saveFailed: state.saving = false; state.saveFailed = true; state.closeAfterSave = false; state.passageAfterSave = nil; return .none
            case .contextResponse(let context): state.contextLoading = false; state.referencesLoaded = true; state.context = context; return .none
            case .contextFailed: state.contextLoading = false; state.contextFailed = true; return .none
            case .entityTapped(let entity):
                state.entity = nil; state.entityLoading = true; state.entityFailed = false
                return .run { [graphClient] send in
                    do { await send(.entityResponse(try await graphClient.detail(id: entity.id))) }
                    catch is CancellationError {} catch { await send(.entityFailed) }
                }.cancellable(id: CancelID.entity, cancelInFlight: true)
            case .entityResponse(let entity): state.entityLoading = false; state.entity = entity; return .none
            case .entityFailed: state.entityLoading = false; state.entityFailed = true; return .none
            case .closeEntity: state.entity = nil; state.candidates = []; return .none
            case .comparisonResponse(let passage): state.comparisonLoading = false; state.comparison = passage; return .none
            case .comparisonFailed: state.comparisonLoading = false; state.comparisonFailed = true; return .none
            case .questionChanged(let text): state.question = String(text.prefix(400)); return .none
            case .submitQuestion:
                guard !state.question.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return .none }
                state.ask = AskFeature.State(question: state.question, reference: state.reference)
                return .none
            case .closeAnswer: state.ask = nil; return .none
            case .ask(.delegate(.openPassage(let reference))), .passageTapped(let reference):
                if state.annotation.note != state.savedNote {
                    state.passageAfterSave = reference
                    return .send(.save)
                }
                return .send(.delegate(.openPassage(reference)))
            case .ask(.delegate(.openEntity(let entity))): state.ask = nil; state.tab = .context; return .send(.entityTapped(entity))
            case .ask(.delegate(.searchInstead)), .ask(.delegate(.talk)):
                state.ask = nil; return .none
            case .ask, .delegate: return .none
            }
        }.ifLet(\.ask, action: \.ask) { AskFeature() }
    }
    private enum CancelID { case context, comparison, entity, save }
}
