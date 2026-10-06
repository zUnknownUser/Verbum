import SwiftUI
import UIKit

/// Editorial surfaces with separate light, dark and increased-contrast values.
extension Palette {
    public static let paper = adaptive(light: 0xF7F5F0, dark: 0x171C19)
    public static let paperElevated = adaptive(light: 0xFFFDF8, dark: 0x222A25)
    public static let ink = adaptive(light: 0x242B26, dark: 0xF0EEE7)
    public static let inkSecondary = adaptive(light: 0x626A63, dark: 0xAAB4AB, contrastLight: 0x424B44, contrastDark: 0xD4DBD5)
    public static let inkTertiary = adaptive(light: 0x687068, dark: 0x9AA69C, contrastLight: 0x424B44, contrastDark: 0xD4DBD5)
    public static let rule = adaptive(light: 0xE2E5DC, dark: 0x354039, contrastLight: 0x788278, contrastDark: 0x87968A)
    public static let accentWash = adaptive(light: 0xE8EEE5, dark: 0x2C3A30)
    /// A stable, dark surface for the primary reading invitation.
    public static let forest = Color(red: 0x28 / 255.0, green: 0x47 / 255.0, blue: 0x37 / 255.0)
    public static let onForest = Color(red: 0xF7 / 255.0, green: 0xF5 / 255.0, blue: 0xED / 255.0)
    public static let selectionWash = accentWash

    static func adaptive(light: UInt32, dark: UInt32, contrastLight: UInt32? = nil, contrastDark: UInt32? = nil) -> Color {
        Color(UIColor { traits in
            let high = traits.accessibilityContrast == .high
            return UIColor(hex: traits.userInterfaceStyle == .dark
                ? (high ? contrastDark ?? dark : dark)
                : (high ? contrastLight ?? light : light))
        })
    }
}

extension UIColor {
    convenience init(hex: UInt32) {
        self.init(red: CGFloat((hex >> 16) & 0xFF) / 255,
                  green: CGFloat((hex >> 8) & 0xFF) / 255,
                  blue: CGFloat(hex & 0xFF) / 255, alpha: 1)
    }
}
