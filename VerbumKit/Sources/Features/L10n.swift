import Foundation
import Models

/// Interface strings, resolved from `Localizable.xcstrings` in the device
/// language (English base, Brazilian Portuguese). Scripture text is never
/// localized here — it is content, chosen by translation.
enum L10n {
    static var bundle: Bundle {
        let language = BookLanguage.current == .portuguese ? "pt-BR" : "en"
        guard let path = Bundle.module.path(forResource: language, ofType: "lproj"),
              let bundle = Bundle(path: path) else { return .module }
        return bundle
    }
    static func t(_ key: String.LocalizationValue) -> String {
        String(localized: key, bundle: bundle)
    }
}
