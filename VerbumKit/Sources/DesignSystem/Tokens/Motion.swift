import SwiftUI

/// Animation durations and curves (spec §41, §64).
///
/// Motion exists to communicate navigation through knowledge — a node opening,
/// focus moving into an entity — never as ambience. Reduce Motion is honoured
/// through `resolved(_:reduceMotion:)`.
public enum Motion {
    public enum Duration {
        public static let quick: TimeInterval = 0.15
        public static let standard: TimeInterval = 0.25
        public static let spatial: TimeInterval = 0.45
    }

    /// State toggles, selection, hover-like feedback.
    public static let quick: Animation = .easeOut(duration: Duration.quick)
    /// Most transitions.
    public static let standard: Animation = .smooth(duration: Duration.standard)
    /// Graph expansion, focus transitions, timeline movement.
    public static let spatial: Animation = .snappy(duration: Duration.spatial, extraBounce: 0)

    /// Returns `nil` (no animation) when the user has Reduce Motion enabled.
    public static func resolved(_ animation: Animation, reduceMotion: Bool) -> Animation? {
        reduceMotion ? nil : animation
    }
}
