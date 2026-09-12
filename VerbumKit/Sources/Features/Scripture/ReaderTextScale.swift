import ComposableArchitecture
import Foundation

/// User-chosen Scripture size, multiplied on top of Dynamic Type (spec §42).
public enum ReaderTextScale: String, CaseIterable, Equatable, Sendable {
    case small
    case standard
    case large
    case extraLarge

    /// Multiplier applied to `Typography.scriptureBasePointSize`.
    public var factor: Double {
        switch self {
        case .small: 0.85
        case .standard: 1.0
        case .large: 1.15
        case .extraLarge: 1.3
        }
    }

    public var title: String {
        switch self {
        case .small: L10n.t("Small")
        case .standard: L10n.t("Default")
        case .large: L10n.t("Large")
        case .extraLarge: L10n.t("Extra Large")
        }
    }
}

extension SharedKey where Self == AppStorageKey<ReaderTextScale>.Default {
    /// Persisted across launches; shared by the reader and its settings sheet.
    public static var readerTextScale: Self {
        Self[.appStorage("readerTextScale"), default: .standard]
    }
}
