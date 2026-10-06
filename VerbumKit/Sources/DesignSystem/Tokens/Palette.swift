import SwiftUI

/// Shared semantic roles keep reading, discovery and account surfaces consistent.
public enum Palette {
    public static let groupedBackground = paper
    public static let surface = paperElevated
    public static let fillSecondary = accentWash
    public static let foregroundSecondary = inkSecondary
    public static let foregroundTertiary = inkTertiary
    public static let onAccent = adaptive(light: 0xFFFDF8, dark: 0x242B26)
    public static let accent = adaptive(light: 0x315A46, dark: 0xA4C9AD, contrastLight: 0x254632, contrastDark: 0xC7E8CF)
}
