import ComposableArchitecture
import Models

/// The reading destination: a chapter reader with the bookshelf beside it.
///
/// Where the shelf lives is size-class driven, never orientation driven: in
/// compact width it is a sheet over the page (as Books shows its table of
/// contents); in regular width — iPad, an unfolded phone — it sits in a column
/// to the left. Search and entity pages are the app shell's business now.
@Reducer
public struct ScriptureFeature {
    @ObservableState
    public struct State: Equatable {
        public var books: BookPickerFeature.State
        public var reader: ChapterReaderFeature.State
        @Presents public var settings: ReaderSettingsFeature.State?
        /// Compact width only: the shelf presented over the page.
        public var isShelfPresented = false

        public init(reference: PassageReference = PassageReference(bookId: "John", chapter: 3)) {
            let reader = ChapterReaderFeature.State(reference: reference)
            self.reader = reader
            self.books = BookPickerFeature.State(current: reader.reference)
        }
    }

    public enum Action: Equatable {
        case books(BookPickerFeature.Action)
        case reader(ChapterReaderFeature.Action)
        case settings(PresentationAction<ReaderSettingsFeature.Action>)
        case titleTapped
        case shelfDismissed
        case settingsButtonTapped
        case delegate(Delegate)

        @CasePathable
        public enum Delegate: Equatable {
            case listen(PassageReference)
            case talk(PassageReference)
            case openContext(PassageReference)
        }
    }

    public init() {}

    public var body: some ReducerOf<Self> {
        Scope(state: \.books, action: \.books) {
            BookPickerFeature()
        }
        Scope(state: \.reader, action: \.reader) {
            ChapterReaderFeature()
        }
        Reduce { state, action in
            switch action {
            case .titleTapped:
                state.isShelfPresented = true
                return .none

            case .shelfDismissed:
                state.isShelfPresented = false
                return .none

            case .settingsButtonTapped:
                state.settings = ReaderSettingsFeature.State()
                return .none

            case .books(.delegate(.chapterSelected(let reference))):
                state.isShelfPresented = false
                return .send(.reader(.go(to: reference)))

            case .reader(.delegate(.listen(let reference))):
                return .send(.delegate(.listen(reference)))

            case .reader(.delegate(.talk(let reference))):
                return .send(.delegate(.talk(reference)))

            case .reader(.delegate(.openContext(let reference))):
                return .send(.delegate(.openContext(reference)))

            case .reader:
                // Keep the shelf's "you are here" in step with the reader.
                state.books.current = state.reader.reference
                return .none

            case .books, .settings, .delegate:
                return .none
            }
        }
        .ifLet(\.$settings, action: \.settings) {
            ReaderSettingsFeature()
        }
    }
}
