import Clients
import ComposableArchitecture
import Models
import Testing
@testable import Features

@MainActor struct VerseStudyFeatureTests {
    private let verse = ChapterReaderFeatureTests.verses("John", 3, count: 1)[0]
    private let target = PassageReference(bookId: "Rom", chapter: 8, verses: 1...1)
    private func context(_ entities: [BibleEntity] = [], references: [PassageReference] = []) -> PassageContext {
        .init(reference: .init(bookId: "John", chapter: 3), entities: entities,
              relatedPassages: references, sources: [], isFixture: true)
    }

    @Test func categoriesExposeAllResourcesWithoutDuplicatesOrSilentTruncation() async {
        let people = (1...29).map { BibleEntity(id: "p\($0)", type: .person, name: "Person \($0)", summary: nil) }
        let theme = BibleEntity(id: "theme", type: .theme, name: "Hope", summary: nil)
        let initial = VerseStudyFeature.State(passage: verse, annotation: nil, context: context(people + [people[0], theme]))
        let store = TestStore(initialState: initial) { VerseStudyFeature() }
        #expect(store.state.resourceEntities.count == 30)
        #expect(store.state.resourceTypes == [.person, .theme])
        #expect(store.state.visibleEntities.count == 6)
        for limit in [12, 18, 24, 29] {
            await store.send(.moreEntities) { $0.entityLimit = limit }
        }
        #expect(store.state.visibleEntities.last?.id == "p29")
        await store.send(.categoryChanged(.theme)) { $0.selectedType = .theme; $0.entityLimit = 6 }
        #expect(store.state.visibleEntities == [theme])
        await store.send(.categoryChanged(.book))
        #expect(store.state.activeType == .theme)
    }

    @Test func referencesAreDeduplicatedAndRevealedInSmallBatches() async {
        let refs = (1...9).map { PassageReference(bookId: "Rom", chapter: $0) }
        let store = TestStore(initialState: VerseStudyFeature.State(passage: verse, annotation: nil, context: context(references: refs + [refs[0]]))) { VerseStudyFeature() }
        #expect(store.state.studyReferences.count == 9)
        #expect(store.state.referenceLimit == 6)
        await store.send(.moreReferences) { $0.referenceLimit = 9 }
    }

    @Test func previewLoadsInPlaceAndBackPreservesUnfinishedNoteAndCategory() async {
        var initial = VerseStudyFeature.State(passage: verse, annotation: nil, context: context())
        initial.tab = .references; initial.annotation.note = "Unfinished note"; initial.selectedType = .theme
        let passage = ChapterReaderFeatureTests.verses("Rom", 8, count: 1)[0]
        let store = TestStore(initialState: initial) { VerseStudyFeature() } withDependencies: {
            $0.bibleClient.passage = { ref in #expect(ref == target); return passage }
        }
        await store.send(.previewPassage(target)) { $0.previewReference = target; $0.previewLoading = true }
        await store.receive(.previewResponse(target, .success(passage))) { $0.preview = passage; $0.previewLoading = false }
        #expect(store.state.reference == initial.reference)
        #expect(store.state.annotation == initial.annotation)
        await store.send(.closePreview) { $0.previewReference = nil; $0.preview = nil }
        #expect(store.state.tab == .references)
        #expect(store.state.selectedType == .theme)
        #expect(store.state.savedNote.isEmpty)
    }

    @Test func previewFailureCanRetryWithoutNavigating() async {
        let fail = LockIsolated(true)
        let passage = ChapterReaderFeatureTests.verses("Rom", 8, count: 1)[0]
        let store = TestStore(initialState: VerseStudyFeature.State(passage: verse, annotation: nil, context: nil)) { VerseStudyFeature() } withDependencies: {
            $0.bibleClient.passage = { _ in
                if fail.value { throw BibleClientError.networkUnavailable }
                return passage
            }
        }
        await store.send(.previewPassage(target)) { $0.previewReference = target; $0.previewLoading = true }
        await store.receive(.previewResponse(target, .failure(.offline))) { $0.previewLoading = false; $0.previewError = .offline }
        fail.setValue(false)
        await store.send(.previewPassage(target)) { $0.previewLoading = true; $0.previewError = nil }
        await store.receive(.previewResponse(target, .success(passage))) { $0.previewLoading = false; $0.preview = passage }
    }

    @Test func closedOrReplacedPreviewIgnoresLateResponses() async {
        var initial = VerseStudyFeature.State(passage: verse, annotation: nil, context: nil)
        initial.previewReference = target; initial.previewLoading = true
        let store = TestStore(initialState: initial) { VerseStudyFeature() }
        let old = PassageReference(bookId: "Gen", chapter: 1)
        await store.send(.previewResponse(old, .failure(.offline)))
        #expect(store.state.previewLoading)
        await store.send(.closePreview) { $0.previewReference = nil; $0.previewLoading = false }
        await store.send(.previewResponse(target, .success(verse)))
        #expect(store.state.preview == nil)
    }

    @Test func openingReaderStillRequiresSavingAnUnfinishedNote() async {
        var initial = VerseStudyFeature.State(passage: verse, annotation: nil, context: nil)
        initial.annotation.note = "My reflection"
        let saved = initial.annotation
        let store = TestStore(initialState: initial) { VerseStudyFeature() } withDependencies: {
            $0.readerAnnotations.save = { #expect($0 == saved) }
        }
        await store.send(.passageTapped(target)) { $0.passageAfterSave = target }
        await store.receive(.save) { $0.saving = true }
        await store.receive(.saved(saved)) { $0.saving = false; $0.saved = true; $0.savedNote = saved.note; $0.passageAfterSave = nil }
        await store.receive(.delegate(.annotationSaved(saved)))
        await store.receive(.delegate(.openPassage(target)))
    }

    @Test func chapterGuideUsesChapterScopeAndLoadsContextWithoutAnnotationActions() async {
        let value = context()
        let store = TestStore(initialState: VerseStudyFeature.State(passage: verse, annotation: nil, context: nil, chapterGuide: true)) { VerseStudyFeature() } withDependencies: {
            $0.contextClient.chapter = { ref in #expect(ref.verses == nil); return value }
        }
        #expect(store.state.tabs == [.context, .references])
        #expect(store.state.text.isEmpty)
        await store.send(.bookmarkToggled)
        await store.send(.tabChanged(.note))
        await store.send(.task)
        await store.receive(.tabChanged(.context)) { $0.contextLoading = true }
        await store.receive(.contextResponse(value)) { $0.contextLoading = false; $0.referencesLoaded = true; $0.context = value }
    }

    @Test func lateEntityResponseDoesNotReopenClosedDetail() async {
        var initial = VerseStudyFeature.State(passage: verse, annotation: nil, context: context())
        let entity = BibleEntity(id: "p", type: .person, name: "Person", summary: nil)
        initial.selectedEntity = entity; initial.entityLoading = true; initial.tab = .context
        let store = TestStore(initialState: initial) { VerseStudyFeature() }
        await store.send(.closeEntity) { $0.selectedEntity = nil; $0.entityLoading = false }
        await store.receive(.tabChanged(.context))
        await store.send(.entityResponse(.init(entity: entity)))
        await store.send(.entityFailed(entity.id))
    }

    @Test func chapterGuideDoesNotChangeReaderPositionOrHistory() async {
        let ref = PassageReference(bookId: "John", chapter: 3)
        var state = ChapterReaderFeature.State(reference: ref)
        state.chapters["John.3"] = [verse]; state.scrollOffsets["John.3"] = 420
        let store = TestStore(initialState: state) { ChapterReaderFeature() }
        await store.send(.studyChapter(ref)) {
            $0.study = VerseStudyFeature.State(passage: verse, annotation: nil, context: nil, chapterGuide: true)
        }
        #expect(store.state.reference == ref)
        #expect(store.state.scrollOffsets == state.scrollOffsets)
        #expect(store.state.history.isEmpty)
    }
}
