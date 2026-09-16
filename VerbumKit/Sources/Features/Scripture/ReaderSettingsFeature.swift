import ComposableArchitecture
import Models

/// Reader preferences. Writes go straight to shared state so the chapter
/// behind the sheet re-renders as the user picks.
@Reducer
public struct ReaderSettingsFeature {
    @ObservableState
    public struct State: Equatable {
        @Shared(.readerTextScale) public var textScale
        @Shared(.readingMode) public var readingMode
        @Shared(.readerFocusMode) public var focusMode
        public init() {}
    }

    public enum Action: Equatable {
        case textScaleChanged(ReaderTextScale)
        case readingModeChanged(ReadingMode)
        case focusModeChanged(Bool)
    }

    public init() {}

    public var body: some ReducerOf<Self> {
        Reduce { state, action in
            switch action {
            case .readingModeChanged(let mode): state.$readingMode.withLock { $0 = mode }; return .none
            case .focusModeChanged(let enabled): state.$focusMode.withLock { $0 = enabled }; return .none
            case .textScaleChanged(let scale):
                state.$textScale.withLock { $0 = scale }
                return .none
            }
        }
    }
}
