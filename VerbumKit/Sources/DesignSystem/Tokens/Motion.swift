import SwiftUI

/// Animation durations and curves (spec §41, §64).
///
/// Motion exists to communicate navigation through knowledge — a node opening,
/// focus moving into an entity — never as ambience. Reduce Motion is honoured
/// through `resolved(_:reduceMotion:)`.
///
/// `Drift` is the single, deliberate exception (DESIGN_SYSTEM.md "Drifting
/// rows"): the arrival chooser, where words move slowly enough to read against.
public enum Motion {
    public enum Duration {
        public static let standard: TimeInterval = 0.25
        public static let spatial: TimeInterval = 0.45
    }

    /// Most transitions.
    public static let standard: Animation = .smooth(duration: Duration.standard)
    /// Graph expansion, focus transitions, timeline movement.
    public static let spatial: Animation = .snappy(duration: Duration.spatial, extraBounce: 0)

    /// Ambient sideways drift, in points per second. Slow enough that a word
    /// stays readable while it moves, and that a finger always overtakes it.
    public enum Drift {
        public static let slow: CGFloat = 7
        public static let medium: CGFloat = 9
        public static let fast: CGFloat = 12

        /// The short glide after a finger lets go of a drifting row.
        public static let settle: Animation = .easeOut(duration: 0.55)
    }

    /// Returns `nil` (no animation) when the user has Reduce Motion enabled.
    public static func resolved(_ animation: Animation, reduceMotion: Bool) -> Animation? {
        reduceMotion ? nil : animation
    }
}
