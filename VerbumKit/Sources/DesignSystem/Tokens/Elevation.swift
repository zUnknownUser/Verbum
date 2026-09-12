import SwiftUI

/// Depth (spec §40 "subtle depth", §64). System apps communicate hierarchy with
/// grouped backgrounds, not shadows, so this scale is deliberately short.
public enum Elevation {
    case none
    /// A card lifted just enough to separate from a grouped background.
    case card
    /// A floating element above scrolling content (e.g. a graph node in focus).
    case floating

    var radius: CGFloat {
        switch self {
        case .none: 0
        case .card: 6
        case .floating: 16
        }
    }

    var y: CGFloat {
        switch self {
        case .none: 0
        case .card: 2
        case .floating: 8
        }
    }

    var opacity: Double {
        switch self {
        case .none: 0
        case .card: 0.06
        case .floating: 0.12
        }
    }
}

extension View {
    public func elevation(_ level: Elevation) -> some View {
        shadow(color: .black.opacity(level.opacity), radius: level.radius, y: level.y)
    }
}
