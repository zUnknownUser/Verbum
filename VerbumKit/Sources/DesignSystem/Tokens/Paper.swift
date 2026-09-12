import SwiftUI
import UIKit

/// The reading surface (spec §40 "warm-light"). System semantic colours are
/// right for chrome, but a book is not read on `#FFFFFF`: the reader gets a
/// warm paper and a warm ink, in both appearances, so text feels printed.
extension Palette {
    /// Reading canvas.
    public static let paper = Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(hex: 0x151311) : UIColor(hex: 0xFAF7F1) })
    /// A surface resting on paper (chapter cells, sheets).
    public static let paperElevated = Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(hex: 0x1F1C19) : UIColor(hex: 0xFFFFFF) })
    /// Body text on paper.
    public static let ink = Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(hex: 0xEBE6DE) : UIColor(hex: 0x1E1A15) })
    public static let inkSecondary = Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(hex: 0xA39B90) : UIColor(hex: 0x6E655B) })
    public static let inkTertiary = Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(hex: 0x6E665C) : UIColor(hex: 0xA39B90) })
    /// Hairlines on paper.
    public static let rule = Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(hex: 0x2C2823) : UIColor(hex: 0xE7E0D5) })
    /// Highlight wash behind a selected verse. Bronze, barely there.
    public static let selectionWash = Color.accentColor.opacity(0.16)
}

extension UIColor {
    convenience init(hex: UInt32) {
        self.init(
            red: CGFloat((hex >> 16) & 0xFF) / 255,
            green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255,
            alpha: 1
        )
    }
}
