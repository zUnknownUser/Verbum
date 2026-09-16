import Clients
import ComposableArchitecture
import Foundation
import Models

enum ProfileAppearance: String, CaseIterable { case automatic, light, dark }

extension SharedKey where Self == AppStorageKey<ProfileAppearance>.Default {
    static var profileAppearance: Self { Self[.appStorage("profileAppearance"), default: .automatic] }
}
extension SharedKey where Self == FileStorageKey<ReadingActivity>.Default {
    static var readingActivity: Self {
        Self[.fileStorage(.documentsDirectory.appending(component: "reading-activity.json")), default: ReadingActivity()]
    }
}

@Reducer
struct ProfileFeature {
    @ObservableState
    struct State: Equatable {
        @Shared(.readingActivity) var activity
        @Shared(.profileAppearance) var appearance
        @Shared(.lastRead) var lastRead
        var usage: UsageStatus?
        var usageFailed = false
        var annotations: [ReaderAnnotation] = []
        var loading = false
        var loadFailed = false
        var readingSettings = ReaderSettingsFeature.State()
        var notifications = DailyVerseFeature.State()
        var bookmarks: [ReaderAnnotation] { annotations.filter { $0.bookmarked == true } }
        var highlights: [ReaderAnnotation] { annotations.filter { $0.highlight != nil } }
        var notes: [ReaderAnnotation] { annotations.filter { !$0.note.isEmpty } }
    }
    enum Action: Equatable {
        case task, loaded([ReaderAnnotation]), loadFailed
        case usageLoaded(UsageStatus?), usageFailed
        case appearanceChanged(ProfileAppearance)
        case readingSettings(ReaderSettingsFeature.Action)
        case notifications(DailyVerseFeature.Action)
    }
    @Dependency(\.usageClient) var usageClient
    @Dependency(\.readerAnnotations) var annotations
    var body: some ReducerOf<Self> {
        Scope(state: \.readingSettings, action: \.readingSettings) { ReaderSettingsFeature() }
        Scope(state: \.notifications, action: \.notifications) { DailyVerseFeature() }
        Reduce { state, action in
            switch action {
            case .task:
                state.loading = true; state.loadFailed = false
                return .merge(.run { [usageClient] send in
                    do { await send(.usageLoaded(try await usageClient.status())) } catch { await send(.usageFailed) }
                }, .run { [annotations] send in
                    do { await send(.loaded(try await annotations.load())) }
                    catch is CancellationError {} catch { await send(.loadFailed) }
                }.cancellable(id: "profile-annotations", cancelInFlight: true))
            case .usageLoaded(let value): state.usage = value; state.usageFailed = false; return .none
            case .usageFailed: state.usageFailed = true; return .none
            case .loaded(let values): state.annotations = values; state.loading = false; return .none
            case .loadFailed: state.loading = false; state.loadFailed = true; return .none
            case .appearanceChanged(let appearance): state.$appearance.withLock { $0 = appearance }; return .none
            case .readingSettings, .notifications: return .none
            }
        }
    }
}
