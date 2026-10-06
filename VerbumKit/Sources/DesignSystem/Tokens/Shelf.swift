import SwiftUI
import UIKit

/// Book spines on the canon shelf. Each division gets a barely different warm
/// tint so the shelf reads as a map of kinds, not a row of identical blocks.
/// Ink stays the same on all of them; the current book is the accent.
extension Palette {
    public static let spineTints: [Color] = [
        Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(hex: 0x26332B) : UIColor(hex: 0xE8EEE5) }),
        Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(hex: 0x30372D) : UIColor(hex: 0xE6E9DA) }),
        Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(hex: 0x2A3432) : UIColor(hex: 0xE5ECE7) }),
        Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(hex: 0x35382C) : UIColor(hex: 0xECE9DB) }),
    ]

    public static func spineTint(_ index: Int) -> Color {
        spineTints[((index % spineTints.count) + spineTints.count) % spineTints.count]
    }
}

extension Typography {
    /// Book name on a shelf block.
    public static let spine: Font = .system(.subheadline, design: .serif).weight(.semibold)
}
