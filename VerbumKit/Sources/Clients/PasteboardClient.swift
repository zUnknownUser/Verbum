import ComposableArchitecture
import UIKit

/// Writes to the system pasteboard. A dependency so features never touch
/// UIKit directly and tests can assert what was copied (spec §35, §61).
@DependencyClient
public struct PasteboardClient: Sendable {
    public var copy: @Sendable (_ text: String) -> Void
}

extension PasteboardClient: DependencyKey {
    public static let liveValue = PasteboardClient(
        copy: { text in
            Task { @MainActor in UIPasteboard.general.string = text }
        }
    )
    public static let previewValue = PasteboardClient(copy: { _ in })
}

extension DependencyValues {
    public var pasteboard: PasteboardClient {
        get { self[PasteboardClient.self] }
        set { self[PasteboardClient.self] = newValue }
    }
}
