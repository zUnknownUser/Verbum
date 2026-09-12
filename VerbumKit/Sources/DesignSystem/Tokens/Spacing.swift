import Foundation

/// Spacing scale (spec §64). 4pt base, matching Apple's layout rhythm.
/// `screenMargin` is the standard iOS leading/trailing inset.
public enum Spacing {
    public static let xxs: CGFloat = 2
    public static let xs: CGFloat = 4
    public static let sm: CGFloat = 8
    public static let md: CGFloat = 12
    public static let lg: CGFloat = 16
    public static let xl: CGFloat = 24
    public static let xxl: CGFloat = 32
    public static let xxxl: CGFloat = 48

    /// Horizontal inset used by system apps for full-width content.
    public static let screenMargin: CGFloat = 16
    /// Extra breathing room around Scripture, so reading columns never feel cramped.
    public static let readingMargin: CGFloat = 24
    /// Widest a Scripture column may grow. Past this, lines get too long to read
    /// comfortably (regular-width layouts: iPad, unfolded phones).
    public static let readingMaxWidth: CGFloat = 680
}
