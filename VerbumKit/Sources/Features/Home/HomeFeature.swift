import ComposableArchitecture
import Foundation
import Models

/// Home (spec §5): a question, a way back into what you were reading, and the
/// verse of the day. Deliberately small (§73.8).
@Reducer
public struct HomeFeature {
    @ObservableState
    public struct State: Equatable {
        @Shared(.lastRead) public var lastRead
        public var greeting: Greeting = .morning
        public var dailyVerse = DailyVerseFeature.State()

        public init() {}
    }

    public enum Greeting: Equatable, Sendable {
        case morning, afternoon, evening

        static func at(hour: Int) -> Greeting {
            switch hour {
            case 5..<12: .morning
            case 12..<18: .afternoon
            default: .evening
            }
        }
    }

    public enum Action: Equatable {
        case task
        case continueReadingTapped
        case searchTapped
        case arrivalTapped
        case dailyVerse(DailyVerseFeature.Action)
        case delegate(Delegate)

        @CasePathable
        public enum Delegate: Equatable {
            case openPassage(PassageReference)
            case openSearch
            case openArrival
        }
    }

    @Dependency(\.date.now) var now
    @Dependency(\.calendar) var calendar

    public init() {}

    public var body: some ReducerOf<Self> {
        Scope(state: \.dailyVerse, action: \.dailyVerse) { DailyVerseFeature() }
        Reduce { state, action in
            switch action {
            case .task:
                state.greeting = Greeting.at(hour: calendar.component(.hour, from: now))
                return .none

            case .continueReadingTapped:
                guard let reference = state.lastRead else { return .none }
                return .send(.delegate(.openPassage(reference)))

            case .dailyVerse(.delegate(.openPassage(let reference))):
                return .send(.delegate(.openPassage(reference)))

            case .dailyVerse:
                return .none

            case .searchTapped:
                return .send(.delegate(.openSearch))
            case .arrivalTapped:
                return .send(.delegate(.openArrival))

            case .delegate:
                return .none
            }
        }
    }
}
