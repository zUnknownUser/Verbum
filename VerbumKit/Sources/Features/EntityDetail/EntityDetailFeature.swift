import Clients
import ComposableArchitecture
import Foundation
import Models

/// One entity's page (spec §9): the facts, the passages that matter, and the
/// one-hop neighbourhood grouped by kind. Passages go to the reader; related
/// entities push another page. Sources are always listed (§33).
@Reducer
public struct EntityDetailFeature {
    @ObservableState
    public struct State: Equatable, Identifiable {
        public let entityID: EntityID
        public var content: Content = .idle

        public init(entityID: EntityID) {
            self.entityID = entityID
        }

        public var id: EntityID { entityID }
    }

    public enum Content: Equatable, Sendable {
        case idle
        case loading
        case loaded(Page)
        case failed
    }

    /// Everything the view needs, assembled once from the two client calls.
    public struct Page: Equatable, Sendable {
        public let detail: EntityDetail
        public let neighborhood: GraphSnapshot
        /// Whether the timeline has this entity (spec §8.2 "Open in timeline").
        public let isOnTimeline: Bool

        public init(detail: EntityDetail, neighborhood: GraphSnapshot, isOnTimeline: Bool = false) {
            self.detail = detail
            self.neighborhood = neighborhood
            self.isOnTimeline = isOnTimeline
        }

        public var entity: BibleEntity { detail.entity }
        public func related(_ type: BibleEntityType) -> [BibleEntity] { neighborhood.nodes(of: type) }

        /// Detail's key passages, or the passage nodes of the neighbourhood when the detail lists none.
        public var passages: [PassageReference] {
            if !detail.keyPassages.isEmpty { return detail.keyPassages }
            return neighborhood.nodes(of: .passage).compactMap { EntityFixturePassages.reference(for: $0) }
        }

        /// Sources cited by the detail and by every visible edge, without duplicates.
        public var sources: [SourceReference] {
            var seen = Set<String>()
            let fromEdges = neighborhood.edges.flatMap(\.sourceReferenceIds)
            return (detail.sources + fromEdges.compactMap { id in detail.sources.first { $0.id == id } })
                .filter { seen.insert($0.id).inserted }
        }
    }

    public enum Action: Equatable {
        case task
        case retryTapped
        case pageResponse(Result<Page, Failure>)
        case passageTapped(PassageReference)
        case entityTapped(BibleEntity)
        case graphTapped
        case timelineTapped
        case talkTapped
        case delegate(Delegate)

        @CasePathable
        public enum Delegate: Equatable {
            case openPassage(PassageReference)
            case openEntity(BibleEntity)
            /// Spec §8: the graph starts from the selected entity.
            case openGraph(EntityID)
            /// The timeline, scrolled to this entity's events.
            case openTimeline(EntityID)
            /// A spoken conversation about this page.
            case talk(EntityDetail)
        }
    }

    public struct Failure: Error, Equatable, Sendable {
        public init() {}
    }

    @Dependency(\.graphClient) var graphClient
    @Dependency(\.timelineClient) var timelineClient

    public init() {}

    /// Spec §8.1: default depth one, at most ~8–12 visible nodes.
    static let neighborLimit = 12

    public var body: some ReducerOf<Self> {
        Reduce { state, action in
            switch action {
            case .task, .retryTapped:
                state.content = .loading
                let id = state.entityID
                return .run { [graphClient, timelineClient] send in
                    do {
                        async let detail = graphClient.detail(id: id)
                        async let neighborhood = graphClient.neighbors(id: id, limit: Self.neighborLimit)
                        // The timeline is optional context: if it fails, the page still loads.
                        async let onTimeline = (try? timelineClient.eventsFor(entityID: id))?.isEmpty == false
                        await send(.pageResponse(.success(Page(detail: try detail, neighborhood: try neighborhood, isOnTimeline: await onTimeline))))
                    } catch {
                        await send(.pageResponse(.failure(Failure())))
                    }
                }
                .cancellable(id: CancelID.load, cancelInFlight: true)

            case .pageResponse(.success(let page)):
                state.content = .loaded(page)
                return .none

            case .pageResponse(.failure):
                state.content = .failed
                return .none

            case .passageTapped(let reference):
                return .send(.delegate(.openPassage(reference)))

            case .entityTapped(let entity):
                if let reference = EntityFixturePassages.reference(for: entity) {
                    return .send(.delegate(.openPassage(reference)))
                }
                return .send(.delegate(.openEntity(entity)))

            case .graphTapped:
                return .send(.delegate(.openGraph(state.entityID)))

            case .timelineTapped:
                return .send(.delegate(.openTimeline(state.entityID)))

            case .talkTapped:
                guard case .loaded(let page) = state.content else { return .none }
                return .send(.delegate(.talk(page.detail)))

            case .delegate:
                return .none
            }
        }
    }

    private enum CancelID { case load }
}

/// Passage nodes carry their reference in the id (`passage.1Sam.17`). Kept
/// next to the feature so the view never parses ids.
enum EntityFixturePassages {
    static func reference(for node: BibleEntity) -> PassageReference? {
        guard node.type == .passage else { return nil }
        let parts = node.id.split(separator: ".")
        guard parts.count == 3, parts[0] == "passage", let chapter = Int(parts[2]) else { return nil }
        return PassageReference(bookId: String(parts[1]), chapter: chapter)
    }
}
