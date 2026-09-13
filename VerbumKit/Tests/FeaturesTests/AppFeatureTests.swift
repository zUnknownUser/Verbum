import ComposableArchitecture
import Foundation
import Models
import Testing
@testable import Features

@MainActor
@Suite struct AppFeatureTests {
    nonisolated static let david = BibleEntity(id: "fixture.person.david", type: .person, name: "David", summary: nil)
    nonisolated static let sam17 = PassageReference(bookId: "1Sam", chapter: 17)

    @Test func tabsRememberTheLastContentTab() async {
        let store = TestStore(initialState: AppFeature.State()) { AppFeature() }
        await store.send(.tabChanged(.explore)) { $0.tab = .explore; $0.contentTab = .explore }
        await store.send(.tabChanged(.search)) { $0.tab = .search }
        await store.send(.tabChanged(.library)) { $0.tab = .library }
        #expect(store.state.contentTab == .explore)
    }

    @Test func searchResultsLandOnTheContentTabAndShowIt() async {
        let store = TestStore(initialState: AppFeature.State()) { AppFeature() }
        await store.send(.tabChanged(.explore)) { $0.tab = .explore; $0.contentTab = .explore }
        await store.send(.tabChanged(.search)) { $0.tab = .search }
        await store.send(.search(.passageTapped(Self.sam17)))
        await store.receive(\.search.delegate.openPassage) {
            $0.tab = .explore
            $0.explorePath[id: 0] = .reader(ScriptureFeature.State(reference: Self.sam17))
        }
        await store.send(.tabChanged(.search)) { $0.tab = .search }
        await store.send(.search(.entityTapped(Self.david)))
        await store.receive(\.search.delegate.openEntity) {
            $0.tab = .explore
            $0.explorePath[id: 1] = .entity(EntityDetailFeature.State(entityID: Self.david.id))
        }
    }

    /// §13, §21.3: a question asked from Search opens Ask on the content tab; its
    /// passages and entities chain on the same stack; "See search results" returns
    /// to the field, which still holds the question.
    @Test func askOpensFromSearchAndFallsBackToIt() async {
        var initial = AppFeature.State()
        initial.tab = .search
        initial.search.query = "why did Job suffer"
        let store = TestStore(initialState: initial) { AppFeature() }
        await store.send(.search(.askTapped))
        await store.receive(\.search.delegate.ask) {
            $0.tab = .home
            $0.homePath[id: 0] = .ask(AskFeature.State(question: "why did Job suffer"))
        }
        await store.send(.homePath(.element(id: 0, action: .ask(.passageTapped(Self.sam17)))))
        await store.receive(\.homePath[id: 0].ask.delegate.openPassage) {
            $0.homePath[id: 1] = .reader(ScriptureFeature.State(reference: Self.sam17))
        }
        await store.send(.homePath(.element(id: 0, action: .ask(.searchInsteadTapped))))
        await store.receive(\.homePath[id: 0].ask.delegate.searchInstead) { $0.tab = .search }
    }

    /// A conversation starts from the reader, an entity page or an Ask answer as
    /// a sheet; when the companion opens a passage the sheet goes and the reader comes.
    @Test func voiceStartsFromThreePlacesAndOpensPassages() async {
        var initial = AppFeature.State()
        initial.tab = .explore
        initial.contentTab = .explore
        initial.explorePath.append(.reader(ScriptureFeature.State(reference: Self.sam17)))
        let detail = EntityDetail(entity: Self.david)
        var entity = EntityDetailFeature.State(entityID: Self.david.id)
        entity.content = .loaded(.init(detail: detail, neighborhood: GraphSnapshot(root: Self.david, nodes: [], edges: [])))
        initial.explorePath.append(.entity(entity))
        var ask = AskFeature.State(question: "why")
        ask.content = .answered(.init(answer: .preview))
        initial.explorePath.append(.ask(ask))
        let store = TestStore(initialState: initial) { AppFeature() }
        store.exhaustivity = .off(showSkippedAssertions: false)

        await store.send(.explorePath(.element(id: 0, action: .reader(.reader(.talkTapped)))))
        await store.receive(\.explorePath[id: 0].reader.delegate.talk) {
            $0.voice = VoiceFeature.State(context: .chapter(Self.sam17))
        }
        let ps23 = PassageReference(bookId: "Ps", chapter: 23)
        await store.send(.voice(.presented(.passageTapped(ps23))))
        await store.receive(\.voice.presented.delegate.openPassage) {
            $0.voice = nil
            $0.explorePath[id: 3] = .reader(ScriptureFeature.State(reference: ps23))
        }

        await store.send(.explorePath(.element(id: 1, action: .entity(.talkTapped))))
        await store.receive(\.explorePath[id: 1].entity.delegate.talk) {
            $0.voice = VoiceFeature.State(context: .entity(detail))
        }
        await store.send(.voice(.dismiss)) { $0.voice = nil }

        await store.send(.explorePath(.element(id: 2, action: .ask(.talkTapped))))
        await store.receive(\.explorePath[id: 2].ask.delegate.talk) {
            $0.voice = VoiceFeature.State(context: .answer(question: "why", .preview))
        }
    }

    @Test func homeOpensPassagesOnItsOwnStack() async throws {
        let store = TestStore(initialState: AppFeature.State()) { AppFeature() }
        let verse = store.state.home.dailyVerse.reference
        await store.send(.home(.dailyVerse(.openTapped)))
        await store.receive(\.home.dailyVerse.delegate.openPassage)
        await store.receive(\.home.delegate.openPassage) {
            $0.homePath[id: 0] = .reader(ScriptureFeature.State(reference: verse))
        }
        await store.send(.home(.searchTapped))
        await store.receive(\.home.delegate.openSearch) { $0.tab = .search }
    }

    @Test func exploreEntriesPushListsOrTheShelf() async {
        let store = TestStore(initialState: AppFeature.State()) { AppFeature() }
        await store.send(.explore(.entryTapped(.people)))
        await store.receive(\.explore.delegate.open) {
            $0.explorePath[id: 0] = .entities(EntityListFeature.State(type: .person))
        }
        await store.send(.explore(.entryTapped(.books)))
        await store.receive(\.explore.delegate.open) {
            $0.explorePath[id: 1] = .books(BookPickerFeature.State(current: PassageReference(bookId: "John", chapter: 3)))
        }
    }

    @Test func destinationsChainOnTheSameStack() async throws {
        // Explore → People → David → Goliath → 1 Samuel 17: the golden path (§75), one stack.
        let goliath = BibleEntity(id: "fixture.person.goliath", type: .person, name: "Goliath", summary: nil)
        let store = TestStore(initialState: AppFeature.State()) { AppFeature() }
        await store.send(.tabChanged(.explore)) { $0.tab = .explore; $0.contentTab = .explore }
        await store.send(.explore(.entryTapped(.people)))
        await store.receive(\.explore.delegate.open) { $0.explorePath[id: 0] = .entities(EntityListFeature.State(type: .person)) }
        await store.send(.explorePath(.element(id: 0, action: .entities(.entityTapped(Self.david)))))
        await store.receive(\.explorePath[id: 0].entities.delegate.openEntity) {
            $0.explorePath[id: 1] = .entity(EntityDetailFeature.State(entityID: Self.david.id))
        }
        await store.send(.explorePath(.element(id: 1, action: .entity(.entityTapped(goliath)))))
        await store.receive(\.explorePath[id: 1].entity.delegate.openEntity) {
            $0.explorePath[id: 2] = .entity(EntityDetailFeature.State(entityID: goliath.id))
        }
        await store.send(.explorePath(.element(id: 2, action: .entity(.passageTapped(Self.sam17)))))
        await store.receive(\.explorePath[id: 2].entity.delegate.openPassage) {
            $0.explorePath[id: 3] = .reader(ScriptureFeature.State(reference: Self.sam17))
        }
        #expect(store.state.explorePath.count == 4)
        await store.send(.explorePath(.popFrom(id: 1))) { $0.explorePath.removeLast(3) }
    }

    @Test func shelfChapterOpensTheReader() async {
        let store = TestStore(initialState: AppFeature.State()) { AppFeature() }
        await store.send(.explore(.entryTapped(.books)))
        await store.receive(\.explore.delegate.open) {
            $0.explorePath[id: 0] = .books(BookPickerFeature.State(current: PassageReference(bookId: "John", chapter: 3)))
        }
        await store.send(.explorePath(.element(id: 0, action: .books(.bookTapped(BibleBook.book(id: "1Sam")!))))) {
            $0.explorePath[id: 0, case: \.books]?.selectedBook = BibleBook.book(id: "1Sam")
        }
        await store.send(.explorePath(.element(id: 0, action: .books(.chapterTapped(17)))))
        await store.receive(\.explorePath[id: 0].books.delegate.chapterSelected) {
            $0.explorePath[id: 1] = .reader(ScriptureFeature.State(reference: Self.sam17))
        }
    }

    @Test func theGraphOpensFromAnEntityAndRefocusesOnTheSameStack() async {
        let goliath = BibleEntity(id: "fixture.person.goliath", type: .person, name: "Goliath", summary: nil)
        let store = TestStore(initialState: AppFeature.State()) { AppFeature() }
        await store.send(.tabChanged(.explore)) { $0.tab = .explore; $0.contentTab = .explore }
        await store.send(.explore(.entryTapped(.people)))
        await store.receive(\.explore.delegate.open) { $0.explorePath[id: 0] = .entities(EntityListFeature.State(type: .person)) }
        await store.send(.explorePath(.element(id: 0, action: .entities(.entityTapped(Self.david)))))
        await store.receive(\.explorePath[id: 0].entities.delegate.openEntity) {
            $0.explorePath[id: 1] = .entity(EntityDetailFeature.State(entityID: Self.david.id))
        }
        await store.send(.explorePath(.element(id: 1, action: .entity(.graphTapped))))
        await store.receive(\.explorePath[id: 1].entity.delegate.openGraph) {
            $0.explorePath[id: 2] = .graph(GraphFeature.State(rootID: Self.david.id))
        }
        await store.send(.explorePath(.element(id: 2, action: .graph(.nodeTapped(goliath)))))
        await store.receive(\.explorePath[id: 2].graph.delegate.openEntity) {
            $0.explorePath[id: 3] = .entity(EntityDetailFeature.State(entityID: goliath.id))
        }
        await store.send(.explorePath(.element(id: 2, action: .graph(.delegate(.focus(goliath)))))) {
            $0.explorePath[id: 4] = .graph(GraphFeature.State(rootID: goliath.id))
        }
    }

    @Test func theTimelineOpensFromExploreAndFromAnEntity() async {
        let store = TestStore(initialState: AppFeature.State()) { AppFeature() }
        await store.send(.explore(.entryTapped(.timeline)))
        await store.receive(\.explore.delegate.open) { $0.explorePath[id: 0] = .timeline(TimelineFeature.State()) }
        await store.send(.explorePath(.element(id: 0, action: .timeline(.entityTapped(Self.david.id)))))
        await store.receive(\.explorePath[id: 0].timeline.delegate.openEntity) {
            $0.explorePath[id: 1] = .entity(EntityDetailFeature.State(entityID: Self.david.id))
        }
        await store.send(.explorePath(.element(id: 1, action: .entity(.timelineTapped))))
        await store.receive(\.explorePath[id: 1].entity.delegate.openTimeline) {
            $0.explorePath[id: 2] = .timeline(TimelineFeature.State(highlight: Self.david.id))
        }
    }

    @Test func aTappedNotificationLandsOnHome() async {
        let (verses, continuation) = AsyncStream.makeStream(of: PassageReference.self)
        let store = TestStore(initialState: AppFeature.State()) {
            AppFeature()
        } withDependencies: {
            $0.notificationClient.openedVerses = { verses }
        }
        await store.send(.tabChanged(.explore)) { $0.tab = .explore; $0.contentTab = .explore }
        await store.send(.task)
        let verse = PassageReference(bookId: "John", chapter: 3, verses: 16...16)
        continuation.yield(verse)
        await store.receive(\.openedVerse) {
            $0.tab = .home
            $0.contentTab = .home
            $0.homePath[id: 0] = .reader(ScriptureFeature.State(reference: verse))
        }
        continuation.finish()
        await store.finish()
    }
}

@MainActor
@Suite struct HomeFeatureTests {
    @Test func greetingFollowsTheClock() async {
        let calendar = Calendar(identifier: .gregorian)
        let afternoon = calendar.date(from: DateComponents(year: 2026, month: 1, day: 4, hour: 15))!
        let store = TestStore(initialState: HomeFeature.State()) {
            HomeFeature()
        } withDependencies: {
            $0.date.now = afternoon
            $0.calendar = calendar
        }
        await store.send(.task) {
            $0.greeting = .afternoon
        }
    }

    @Test func greetingBoundaries() {
        #expect(HomeFeature.Greeting.at(hour: 13) == .afternoon)
        #expect(HomeFeature.Greeting.at(hour: 22) == .evening)
        #expect(HomeFeature.Greeting.at(hour: 3) == .evening)
    }

    @Test func continueReadingNeedsSomethingRead() async {
        let store = TestStore(initialState: HomeFeature.State()) { HomeFeature() }
        await store.send(.continueReadingTapped) // nothing read yet: no delegate
        store.state.$lastRead.withLock { $0 = PassageReference(bookId: "Rom", chapter: 8) }
        await store.send(.continueReadingTapped)
        await store.receive(\.delegate.openPassage, PassageReference(bookId: "Rom", chapter: 8))
    }

    @Test func readerRecordsWhereYouAre() async {
        let store = TestStore(initialState: ChapterReaderFeature.State(reference: PassageReference(bookId: "Rom", chapter: 8))) {
            ChapterReaderFeature()
        } withDependencies: {
            $0.bibleClient.chapter = { _, _ in [] }
        }
        await store.send(.task) { $0.content = .loading }
        await store.receive(\.chapterResponse.success) {
            $0.content = .loaded([])
            $0.$lastRead.withLock { $0 = PassageReference(bookId: "Rom", chapter: 8) }
        }
    }
}
