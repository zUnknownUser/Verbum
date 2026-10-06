import SwiftUI

/// Solid content surfaces; translucent materials remain in system navigation.
public extension View {
    func editorialSurface() -> some View {
        background(Palette.paperElevated, in: RoundedRectangle(cornerRadius: Radius.lg, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: Radius.lg, style: .continuous)
                    .strokeBorder(Palette.rule, lineWidth: 1)
                    .allowsHitTesting(false)
            }
    }
}

public struct EditorialIcon: View {
    private let symbol: String
    public init(_ symbol: String) { self.symbol = symbol }
    public var body: some View {
        Image(systemName: symbol)
            .font(.system(size: 21, weight: .medium))
            .foregroundStyle(Palette.accent)
            .frame(width: 48, height: 48)
            .background(Palette.accentWash, in: RoundedRectangle(cornerRadius: Radius.md))
            .accessibilityHidden(true)
    }
}

/// A restrained pressed state which also respects Reduce Motion.
public struct EditorialButtonStyle: ButtonStyle {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    public init() {}
    public func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .opacity(configuration.isPressed ? 0.8 : 1)
            .scaleEffect(configuration.isPressed && !reduceMotion ? 0.985 : 1)
            .animation(reduceMotion ? nil : .easeOut(duration: 0.16), value: configuration.isPressed)
    }
}

public struct EditorialTextFieldStyle: TextFieldStyle {
    public init() {}
    public func _body(configuration: TextField<Self._Label>) -> some View {
        configuration
            .font(.body)
            .foregroundStyle(Palette.ink)
            .padding(16)
            .frame(minHeight: 54)
            .background(Palette.paperElevated, in: RoundedRectangle(cornerRadius: Radius.md))
            .overlay {
                RoundedRectangle(cornerRadius: Radius.md).strokeBorder(Palette.rule, lineWidth: 1)
                    .allowsHitTesting(false)
            }
    }
}
