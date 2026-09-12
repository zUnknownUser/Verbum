import Foundation

/// Interface strings, resolved from `Localizable.xcstrings` in the device
/// language (English base, Brazilian Portuguese). Scripture text is never
/// localized here — it is content, chosen by translation.
enum L10n {
    static func t(_ key: String.LocalizationValue) -> String {
        String(localized: key, bundle: .module)
    }
}
