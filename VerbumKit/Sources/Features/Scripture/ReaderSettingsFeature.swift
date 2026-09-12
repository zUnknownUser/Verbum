import ComposableArchitecture

/// Reader preferences. Writes go straight to shared state so the chapter
/// behind the sheet re-renders as the user picks.
@Reducer
public struct ReaderSettingsFeature {
    @ObservableState
    public struct State: Equatable {
        @Shared(.readerTextScale) public var textScale
        public init() {}
    }

    public enum Action: Equatable {
        case textScaleChanged(ReaderTextScale)
    }

    public init() {}

    public var body: some ReducerOf<Self> {
        Reduce { state, action in
            switch action {
            case .textScaleChanged(let scale):
                state.$textScale.withLock { $0 = scale }
                return .none
            }
        }
    }
}
