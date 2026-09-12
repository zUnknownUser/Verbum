import Clients
import ComposableArchitecture
import Foundation
import Models

/// The timeline (spec §4.2, §21.5): periods and events in order, each with its
/// dating and how sure that dating is. Tapping an event opens it in place —
/// summary, the people and places involved (which go to their pages), sources.
/// Opened from an entity page, it highlights that entity's events.
@Reducer
public struct TimelineFeature {
    @ObservableState
    public struct State: Equatable {
        /// The entity whose events are highlighted, when opened from its page.
        public let highlight: EntityID?
        public var content: Content = .idle
        public var selectedID: TimelineEvent.ID?
        /// Names for the entities the events mention, resolved after loading.
        public var entityNames: [EntityID: String] = [:]

        public init(highlight: EntityID? = nil) {
            self.highlight = highlight
        }

        /// The first highlighted event, to scroll to on arrival.
        public var highlightedEventID: TimelineEvent.ID? {
            guard let highlight, case .loaded(let events) = content else { return nil }
            return events.first { $0.entityIds.contains(highlight) }?.id
        }
    }

    public enum Content: Equatable, Sendable {
        case idle
        case loading
        case loaded([TimelineEvent])
        case failed
    }

    public enum Action: Equatable {
        case task
        case retryTapped
        case eventsResponse(Result<[TimelineEvent], Failure>)
        case namesResponse([EntityID: String])
        case eventTapped(TimelineEvent.ID)
        case entityTapped(EntityID)
        case delegate(Delegate)

        @CasePathable
        public enum Delegate: Equatable {
            case openEntity(EntityID)
        }
    }

    public struct Failure: Error, Equatable, Sendable {
        public init() {}
    }

    @Dependency(\.timelineClient) var timelineClient
    @Dependency(\.graphClient) var graphClient

    public init() {}

    public var body: some ReducerOf<Self> {
        Reduce { state, action in
            switch action {
            case .task:
                guard state.content == .idle else { return .none }
                return load(&state)

            case .retryTapped:
                return load(&state)

            case .eventsResponse(.success(let events)):
                state.content = .loaded(events)
                // Arriving from an entity page: its first event starts open.
                if state.selectedID == nil { state.selectedID = state.highlightedEventID }
                // Names for the chips. Unknown ids are simply not shown.
                let ids = Array(Set(events.flatMap(\.entityIds))).sorted()
                return .run { [graphClient] send in
                    var names: [EntityID: String] = [:]
                    for id in ids {
                        if let entity = try? await graphClient.entity(id: id) { names[id] = entity.name }
                    }
                    await send(.namesResponse(names))
                }
                .cancellable(id: CancelID.names, cancelInFlight: true)

            case .namesResponse(let names):
                state.entityNames = names
                return .none

            case .eventsResponse(.failure):
                state.content = .failed
                return .none

            case .eventTapped(let id):
                state.selectedID = state.selectedID == id ? nil : id
                return .none

            case .entityTapped(let id):
                return .send(.delegate(.openEntity(id)))

            case .delegate:
                return .none
            }
        }
    }

    private func load(_ state: inout State) -> Effect<Action> {
        state.content = .loading
        return .run { [timelineClient] send in
            do {
                await send(.eventsResponse(.success(try await timelineClient.events())))
            } catch {
                await send(.eventsResponse(.failure(Failure())))
            }
        }
        .cancellable(id: CancelID.load, cancelInFlight: true)
    }

    private enum CancelID { case load, names }
}

/// How a dating reads (§4.2: approximate dates and uncertainty, always shown).
/// `c. 1010–970 BC`, `1446–1250 BC · debated`, `AD 30–33 · debated`,
/// `c. 516 BC – AD 70`, `date unknown`. The era words come from the catalogue.
public enum TimelineDates {
    public static func text(for event: TimelineEvent) -> String {
        guard let start = event.startYear else { return L10n.t("date unknown") }
        let prefix = event.datePrecision == .approximate ? L10n.t("c.") + " " : ""
        let span: String
        if let end = event.endYear, end != start {
            switch (start < 0, end < 0) {
            case (true, true): span = L10n.t("\(String(-start))–\(String(-end)) BC")
            case (false, false): span = L10n.t("AD \(String(start))–\(String(end))")
            default: span = "\(year(start)) – \(year(end))"
            }
        } else {
            span = year(start)
        }
        let suffix = event.datePrecision == .debated ? " · " + L10n.t("debated") : ""
        return prefix + span + suffix
    }

    /// `1010 BC` / `AD 30`, in the device language.
    static func year(_ year: Int) -> String {
        year < 0 ? L10n.t("\(String(-year)) BC") : L10n.t("AD \(String(year))")
    }
}
