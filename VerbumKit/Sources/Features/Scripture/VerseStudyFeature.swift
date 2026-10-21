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
        public var chapterGuide = false
        public var selectedType: BibleEntityType?
        public var entityLimit = 6
        public var referenceLimit = 6
        public var selectedEntity: BibleEntity?
        public var previewReference: PassageReference?
        public var preview: BiblePassage?
        public var previewLoading = false
        public var previewError: ReaderError?
        @Presents public var guidePreview: PassagePreviewFeature.State?
        public var entityHistory: [EntityDetail] = []
        public var expandedThemeSections: [EntityID: Set<String>] = [:]
        public var entityScrollOffsets: [EntityID: Double] = [:]

        public var tabs: [Tab] { chapterGuide ? [.context, .references] : Tab.allCases }
        public var resourceEntities: [BibleEntity] {
            var seen = Set<String>()
            return (candidates.isEmpty ? context?.entities ?? [] : candidates).filter { seen.insert($0.id).inserted }
        }
        public var resourceTypes: [BibleEntityType] {
            BibleEntityType.allCases.filter { type in resourceEntities.contains { $0.type == type } }
        }
        public var activeType: BibleEntityType? {
            if let selectedType, resourceTypes.contains(selectedType) { return selectedType }
            return resourceTypes.first
        }
        public var filteredEntities: [BibleEntity] { resourceEntities.filter { $0.type == activeType } }
        public var visibleEntities: [BibleEntity] { Array(filteredEntities.prefix(entityLimit)) }
        public var studyReferences: [PassageReference] {
            var seen = Set<PassageReference>()
            let references = entity?.keyPassages ?? ((context?.study?.connections.map(\.reference) ?? []) + (context?.relatedPassages ?? []))
            let guideReferences = Set(entity?.themeStudy?.readings.map(\.reference) ?? [])
            return references.filter { !guideReferences.contains($0) && seen.insert($0).inserted }
        }
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
        public init(passage: BiblePassage, annotation: ReaderAnnotation?, context: PassageContext?, candidates: [BibleEntity] = [], chapterGuide: Bool = false) {
            reference = PassageReference(bookId: passage.bookId, chapter: passage.chapter, verses: chapterGuide ? nil : passage.verseStart...passage.verseEnd)
            text = chapterGuide ? "" : passage.text; translationID = passage.translationId
            self.chapterGuide = chapterGuide
            self.annotation = annotation ?? ReaderAnnotation(reference: reference)
            self.savedNote = annotation?.note ?? ""
            self.context = context; self.candidates = candidates
            if chapterGuide || !candidates.isEmpty { tab = .context }
        }
    }
    public enum Action: Equatable {
        case task, save, closeEntity, done, bookmarkToggled
        case categoryChanged(BibleEntityType), moreEntities, moreReferences, retryEntity
        case previewPassage(PassageReference), closePreview
        case previewThemePassage(PassageReference)
        case guidePreview(PresentationAction<PassagePreviewFeature.Action>)
        case themeSectionChanged(String, Bool)
        case entityScrollOffsetChanged(EntityID, Double)
        case previewResponse(PassageReference, Result<BiblePassage, ReaderError>)
        case highlightStyleChanged(HighlightStyle)
        case tabChanged(Tab), highlightChanged(HighlightColor?), noteChanged(String), questionChanged(String)
        case contextResponse(PassageContext?), contextFailed
        case entityTapped(BibleEntity), entityResponse(EntityDetail), entityFailed(String)
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
    @Dependency(\.bibleClient) var bibleClient
    @Dependency(\.contextClient) var contextClient
    @Dependency(\.graphClient) var graphClient
    @Dependency(\.bibleComparison) var bibleComparison
    @Dependency(\.readerAnnotations) var annotations
    public init() {}
    public var body: some ReducerOf<Self> {
        Reduce { state, action in
            switch action {
            case .categoryChanged(let type):
                guard state.resourceTypes.contains(type) else { return .none }
                state.selectedType = type; state.entityLimit = 6
                return .none
            case .moreEntities:
                state.entityLimit = min(state.entityLimit + 6, state.filteredEntities.count)
                return .none
            case .moreReferences:
                state.referenceLimit = min(state.referenceLimit + 6, state.studyReferences.count)
                return .none
            case .previewThemePassage(let reference):
                state.guidePreview = PassagePreviewFeature.State(reference: reference)
                return .none
            case .guidePreview(.presented(.delegate(.openReader(let reference)))):
                state.guidePreview = nil
                return .send(.passageTapped(reference))
            case .guidePreview:
                return .none
            case .themeSectionChanged(let section, let expanded):
                guard let id = state.entity?.entity.id else { return .none }
                if expanded { state.expandedThemeSections[id, default: []].insert(section) }
                else { state.expandedThemeSections[id, default: []].remove(section) }
                return .none
            case .entityScrollOffsetChanged(let id, let offset):
                state.entityScrollOffsets[id] = max(0, offset)
                return .none
            case .previewPassage(let reference):
                state.previewReference = reference; state.preview = nil
                state.previewLoading = true; state.previewError = nil
                return .run { [bibleClient] send in
                    do { await send(.previewResponse(reference, .success(try await bibleClient.passage(reference: reference)))) }
                    catch is CancellationError {} catch { await send(.previewResponse(reference, .failure(ReaderError(error)))) }
                }.cancellable(id: CancelID.preview, cancelInFlight: true)
            case .previewResponse(let reference, let result):
                guard state.previewReference == reference else { return .none }
                state.previewLoading = false
                switch result {
                case .success(let passage): state.preview = passage; state.previewError = nil
                case .failure(let error): state.previewError = error
                }
                return .none
            case .closePreview:
                state.previewReference = nil; state.preview = nil; state.previewLoading = false; state.previewError = nil
                return .cancel(id: CancelID.preview)
            case .retryEntity:
                guard let entity = state.selectedEntity else { return .none }
                return .send(.entityTapped(entity))
            case .bookmarkToggled:
                guard !state.chapterGuide, !state.saving else { return .none }
                state.annotation.bookmarked = !(state.annotation.bookmarked ?? false); state.saved = false
                return .send(.save)
            case .done:
                if state.annotation.note != state.savedNote { state.closeAfterSave = true; return .send(.save) }
                return .send(.delegate(.close))
            case .task:
                if state.selectedEntity != nil { return .none }
                if state.chapterGuide { return .send(.tabChanged(.context)) }
                if state.candidates.count == 1 { return .send(.entityTapped(state.candidates[0])) }
                return .none
            case .tabChanged(let tab):
                guard state.tabs.contains(tab) else { return .none }
                state.tab = tab; state.entity = nil; state.candidates = []
                state.entityHistory = []
                state.selectedEntity = nil; state.entityLoading = false; state.entityFailed = false
                state.referenceLimit = 6
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
            case .highlightStyleChanged(let style):
                state.annotation.highlightStyle = style
                if state.annotation.highlight == nil { state.annotation.highlight = .gold }
                state.saved = false
                return .send(.save)
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
                if let current = state.entity, current.entity.id != entity.id {
                    state.entityHistory.append(current)
                }
                state.selectedEntity = entity; state.referenceLimit = 6
                state.entity = nil; state.entityLoading = true; state.entityFailed = false
                return .run { [graphClient] send in
                    do { await send(.entityResponse(try await graphClient.detail(id: entity.id))) }
                    catch is CancellationError {} catch { await send(.entityFailed(entity.id)) }
                }.cancellable(id: CancelID.entity, cancelInFlight: true)
            case .entityResponse(let entity):
                guard state.selectedEntity?.id == entity.entity.id else { return .none }
                state.entityLoading = false; state.entity = entity; return .none
            case .entityFailed(let id):
                guard state.selectedEntity?.id == id else { return .none }
                state.entityLoading = false; state.entityFailed = true; return .none
            case .closeEntity:
                if let previous = state.entityHistory.popLast() {
                    state.entity = previous; state.selectedEntity = previous.entity
                    state.entityLoading = false; state.entityFailed = false; state.referenceLimit = 6
                    return .cancel(id: CancelID.entity)
                }
                state.entity = nil; state.candidates = []; state.selectedEntity = nil
                state.entityLoading = false; state.entityFailed = false; state.referenceLimit = 6
                return .merge(.cancel(id: CancelID.entity), .send(.tabChanged(.context)))
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
        }
        .ifLet(\.ask, action: \.ask) { AskFeature() }
        .ifLet(\.$guidePreview, action: \.guidePreview) { PassagePreviewFeature() }
    }
    private enum CancelID { case context, comparison, entity, save, preview }
}
