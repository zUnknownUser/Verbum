import Clients
import ComposableArchitecture
import Foundation
import Models
import Testing
@testable import Features

@MainActor @Suite struct ThemeStudyFeatureTests {
    static func detail() throws -> EntityDetail {
        let file = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
            .appendingPathComponent("api/examples/entities/faith-study.pt.json")
        return try JSONDecoder().decode(EntityDetail.self, from: Data(contentsOf: file))
    }

    @Test func guideDoesNotDuplicateItsReadingsOrHideOtherEvidence() throws {
        let detail = try Self.detail()
        let study = try #require(detail.themeStudy)
        let connection = try #require(study.connections.first)
        let extra = PassageReference(bookId: "John", chapter: 3, verses: 16...16)
        let legacy = SourceReference(id: "legacy", citation: "Prior source", url: nil)
        let page = EntityDetailFeature.Page(
            detail: EntityDetail(entity: detail.entity,
                keyPassages: study.readings.map(\.reference) + [extra, extra],
                sources: detail.sources + [legacy, legacy], themeStudy: study),
            neighborhood: .init(root: detail.entity, nodes: [connection.entity], edges: [])
        )
        #expect(page.additionalPassages == [extra])
        #expect(Set(page.studySources.map(\.id)) == Set(study.sourceReferenceIds))
        #expect(page.otherSources.contains(legacy))
        #expect(page.sources.filter { $0.id == legacy.id }.count == 1)
        #expect(page.related(connection.entity.type).isEmpty)
        #expect(page.neighborhood.nodes == [connection.entity])
    }

    @Test func previewAndRelatedEntityKeepGuideAndExpansionWithoutReloading() async throws {
        let detail = try Self.detail()
        let study = try #require(detail.themeStudy)
        let reference = study.readings[0].reference
        let passage = ChapterReaderFeatureTests.verses(reference.bookId, reference.chapter, count: 1)[0]
        let page = EntityDetailFeature.Page(detail: detail, neighborhood: .init(root: detail.entity, nodes: [], edges: []))
        var initial = EntityDetailFeature.State(entityID: detail.entity.id)
        initial.content = .loaded(page)
        let store = TestStore(initialState: initial) { EntityDetailFeature() } withDependencies: {
            $0.bibleClient.passage = { #expect($0 == reference); return passage }
        }
        await store.send(.studySectionChanged("reading.\(study.readings[0].id)", true)) {
            $0.expandedStudySections = ["reading.\(study.readings[0].id)"]
        }
        await store.send(.previewPassage(reference)) { $0.passagePreview = .init(reference: reference) }
        await store.send(.passagePreview(.presented(.task))) { $0.passagePreview?.loading = true }
        await store.receive(.passagePreview(.presented(.response(reference, .success(passage))))) {
            $0.passagePreview?.loading = false; $0.passagePreview?.passage = passage
        }
        await store.send(.passagePreview(.dismiss)) { $0.passagePreview = nil }
        await store.send(.task)
        let connection = try #require(study.connections.first)
        await store.send(.entityTapped(connection.entity))
        await store.receive(.delegate(.openEntity(connection.entity)))
        #expect(store.state.content == initial.content)
        #expect(store.state.expandedStudySections == ["reading.\(study.readings[0].id)"])
    }

    @Test func previewReaderActionUsesExistingDelegateAndClosesPreview() async {
        let ref = PassageReference(bookId: "Exod", chapter: 14, verses: 10...31)
        var state = EntityDetailFeature.State(entityID: "fixture.theme.faith")
        state.passagePreview = .init(reference: ref)
        let store = TestStore(initialState: state) { EntityDetailFeature() }
        await store.send(.passagePreview(.presented(.openReader)))
        await store.receive(.passagePreview(.presented(.delegate(.openReader(ref))))) { $0.passagePreview = nil }
        await store.receive(.delegate(.openPassage(ref)))
    }

    @Test func inlineThemeRetainsStudyHistoryExpansionAndUnfinishedNote() async throws {
        let detail = try Self.detail()
        let study = try #require(detail.themeStudy)
        let connection = try #require(study.connections.first)
        let child = EntityDetail(entity: connection.entity)
        let verse = ChapterReaderFeatureTests.verses("Exod", 14, count: 1)[0]
        var state = VerseStudyFeature.State(passage: verse, annotation: nil, context: nil, chapterGuide: true)
        state.entity = detail; state.selectedEntity = detail.entity; state.annotation.note = "Minha reflexão"
        let store = TestStore(initialState: state) { VerseStudyFeature() } withDependencies: {
            $0.graphClient.detail = { #expect($0 == connection.entity.id); return child }
        }
        await store.send(.themeSectionChanged("connections", true)) {
            $0.expandedThemeSections[detail.entity.id] = ["connections"]
        }
        await store.send(.entityScrollOffsetChanged(detail.entity.id, 1320)) {
            $0.entityScrollOffsets[detail.entity.id] = 1320
        }
        await store.send(.task)
        await store.send(.entityTapped(connection.entity)) {
            $0.entityHistory = [detail]; $0.entity = nil; $0.selectedEntity = connection.entity; $0.entityLoading = true
        }
        await store.receive(.entityResponse(child)) { $0.entity = child; $0.entityLoading = false }
        await store.send(.closeEntity) {
            $0.entityHistory = []; $0.entity = detail; $0.selectedEntity = detail.entity
        }
        await store.send(.entityResponse(child))
        #expect(store.state.annotation.note == "Minha reflexão")
        #expect(store.state.expandedThemeSections[detail.entity.id] == ["connections"])
        #expect(store.state.entityScrollOffsets[detail.entity.id] == 1320)
        #expect(Set(store.state.studyReferences).isDisjoint(with: Set(study.readings.map(\.reference))))
        let reference = study.readings[0].reference
        await store.send(.previewThemePassage(reference)) { $0.guidePreview = .init(reference: reference) }
        await store.send(.guidePreview(.dismiss)) { $0.guidePreview = nil }
        #expect(store.state.entity == detail)
        #expect(store.state.annotation.note == "Minha reflexão")
    }

    @Test func openingReaderFromThemePreviewStillSavesUnfinishedAnnotation() async {
        let reference = PassageReference(bookId: "Exod", chapter: 14, verses: 10...31)
        let verse = ChapterReaderFeatureTests.verses("John", 3, count: 1)[0]
        var state = VerseStudyFeature.State(passage: verse, annotation: nil, context: nil)
        state.annotation.note = "Minha reflexão"
        state.guidePreview = .init(reference: reference)
        let annotation = state.annotation
        let store = TestStore(initialState: state) { VerseStudyFeature() } withDependencies: {
            $0.readerAnnotations.save = { #expect($0 == annotation) }
        }
        await store.send(.guidePreview(.presented(.openReader)))
        await store.receive(.guidePreview(.presented(.delegate(.openReader(reference))))) { $0.guidePreview = nil }
        await store.receive(.passageTapped(reference)) { $0.passageAfterSave = reference }
        await store.receive(.save) { $0.saving = true }
        await store.receive(.saved(annotation)) {
            $0.saving = false; $0.saved = true; $0.savedNote = annotation.note; $0.passageAfterSave = nil
        }
        await store.receive(.delegate(.annotationSaved(annotation)))
        await store.receive(.delegate(.openPassage(reference)))
    }

    @Test func previewFailureCanRetryAndIgnoresResponseForAnotherReference() async {
        let reference = PassageReference(bookId: "Exod", chapter: 14, verses: 10...31)
        let passage = ChapterReaderFeatureTests.verses("Exod", 14, count: 1)[0]
        let fail = LockIsolated(true)
        let store = TestStore(initialState: PassagePreviewFeature.State(reference: reference)) { PassagePreviewFeature() } withDependencies: {
            $0.bibleClient.passage = { _ in
                if fail.value { throw BibleClientError.networkUnavailable }
                return passage
            }
        }
        await store.send(.task) { $0.loading = true }
        await store.receive(.response(reference, .failure(.offline))) { $0.loading = false; $0.error = .offline }
        await store.send(.response(.init(bookId: "John", chapter: 3), .success(passage)))
        #expect(store.state.passage == nil)
        fail.setValue(false)
        await store.send(.retry) { $0.loading = true; $0.error = nil }
        await store.receive(.response(reference, .success(passage))) { $0.loading = false; $0.passage = passage }
        await store.send(.task)
    }
}
