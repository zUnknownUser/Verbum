import SwiftUI

/// Type roles (spec §42, §64). Every font is a Dynamic Type text style, never a
/// fixed point size.
///
/// Two families, both system-provided:
/// - **SF Pro** (`.default`) for interface chrome.
/// - **New York** (`.serif`) for Scripture and editorial headings — the same
///   pairing Apple Books uses.
public enum Typography {
    // MARK: Interface (SF Pro)

    public static let headline: Font = .headline
    public static let body: Font = .body
    public static let subheadline: Font = .subheadline
    public static let footnote: Font = .footnote
    public static let caption: Font = .caption
    public static let caption2: Font = .caption2

    // MARK: Editorial (New York)

    /// Entity names and screen titles that should read like a book, not a form.
    public static let editorialTitle: Font = .system(.largeTitle, design: .serif).weight(.semibold)
    public static let editorialHeadline: Font = .system(.title2, design: .serif).weight(.semibold)

    // MARK: Scripture (New York)

    /// Body text of a passage at the default reader size.
    public static let scripture: Font = .system(.body, design: .serif)

    /// Point size `scripture` renders at before Dynamic Type. Readers combine it
    /// with `@ScaledMetric` and the user's text-scale preference (spec §42:
    /// user-adjustable size *and* Dynamic Type).
    public static let scriptureBasePointSize: CGFloat = 17

    /// Body text of a passage at an explicit point size (already scaled).
    public static func scripture(pointSize: CGFloat) -> Font {
        .system(size: pointSize, design: .serif)
    }

    /// Chapter opener numeral, e.g. the "17" above 1 Samuel 17.
    public static let chapterNumeral: Font = .system(size: 76, weight: .light, design: .serif)

    /// Letter-spaced small caps used for section labels and the book name over
    /// a chapter numeral. Apply with `Text.overline()`.
    public static let overline: Font = .caption.weight(.semibold)
    public static let overlineTracking: CGFloat = 2.2

    /// Verse numeral in the margin, sized relative to the Scripture point size.
    public static func verseNumeral(for scripturePointSize: CGFloat) -> Font {
        .system(size: scripturePointSize * 0.62, weight: .medium, design: .serif)
    }

    /// Title in a navigation bar that should still feel like a book.
    public static let navigationSerif: Font = .system(.headline, design: .serif).weight(.semibold)
    /// Verse numbers sit in the margin of the reading column; small and quiet.
    public static let verseNumber: Font = .caption2.weight(.medium).monospacedDigit()
    /// Book / chapter heading above a passage.
    public static let scriptureHeading: Font = .system(.title, design: .serif).weight(.semibold)

    /// Extra leading applied to Scripture, as a fraction of point size. New York
    /// needs more air than SF; at 17pt this is ~6pt.
    public static let scriptureLineSpacingRatio: CGFloat = 0.35
    /// Convenience at the default size.
    public static let scriptureLineSpacing: CGFloat = 6
}
