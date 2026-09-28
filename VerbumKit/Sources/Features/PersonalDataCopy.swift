import Foundation
import Models

enum PersonalDataCopy {
    static var annotationsFailed: String {
        BookLanguage.current == .portuguese
            ? "Não foi possível carregar suas notas e marcações. Seu texto continua disponível."
            : "Your notes and markings could not be loaded. You can keep reading."
    }
}
