import SwiftUI

/// Semantic colours (spec §64: surfaces, foreground, background, accent).
///
/// All values resolve to Apple's system colours so the app inherits light and
/// dark appearance, Increase Contrast, and Liquid Glass vibrancy without any
/// custom handling. The only brand colour is `accent`, sourced from the app's
/// `AccentColor` asset so it is defined once.
public enum Palette {
    // MARK: Background

    /// Grouped screens (Explore, Library, settings-like lists).
    public static let groupedBackground = Color(.systemGroupedBackground)

    // MARK: Surfaces

    /// A card or row resting on `groupedBackground`.
    public static let surface = Color(.secondarySystemGroupedBackground)
    public static let fillSecondary = Color(.secondarySystemFill)

    // MARK: Foreground

    public static let foregroundSecondary = Color(.secondaryLabel)
    public static let foregroundTertiary = Color(.tertiaryLabel)

    // MARK: Accent

    /// The single brand colour. Interactive elements only — never decoration.
    public static let accent = Color.accentColor
}
