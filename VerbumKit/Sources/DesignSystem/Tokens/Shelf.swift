import SwiftUI
import UIKit

/// Book spines on the canon shelf. Each division gets a barely different warm
/// tint so the shelf reads as a map of kinds, not a row of identical blocks.
/// Ink stays the same on all of them; the current book is the accent.
extension Palette {
    public static let spineTints: [Color] = [
        Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(hex: 0x262220) : UIColor(hex: 0xF3EDE3) }),
        Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(hex: 0x2A2521) : UIColor(hex: 0xEDE4D6) }),
        Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(hex: 0x282420) : UIColor(hex: 0xF0E9DD) }),
        Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(hex: 0x2C2722) : UIColor(hex: 0xE9E0D1) }),
    ]

    public static func spineTint(_ index: Int) -> Color {
        spineTints[((index % spineTints.count) + spineTints.count) % spineTints.count]
    }
}

extension Typography {
    /// Book name on a shelf block.
    public static let spine: Font = .system(.subheadline, design: .serif).weight(.semibold)
}
