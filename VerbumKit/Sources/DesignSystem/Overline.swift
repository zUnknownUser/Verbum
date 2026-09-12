import SwiftUI

extension Text {
    /// Letter-spaced small caps: `OLD TESTAMENT`, `1 SAMUEL`.
    public func overline(color: Color = Palette.inkSecondary) -> some View {
        self
            .font(Typography.overline)
            .kerning(Typography.overlineTracking)
            .textCase(.uppercase)
            .foregroundStyle(color)
    }
}
