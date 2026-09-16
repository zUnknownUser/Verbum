import Foundation
import Testing
@testable import Models

@Suite struct BibleBookLocalizedTests {
    @Test func everyBookHasAPortugueseName() {
        // Books whose Portuguese name is spelled the same as the English one.
        let sameInBothLanguages: Set<String> = ["Daniel", "Joel", "1 Samuel", "2 Samuel"]
        for book in BibleBook.canon {
            let translated = book.localizedName(for: .portuguese) != book.name || sameInBothLanguages.contains(book.name)
            #expect(translated, "\(book.id) has no Portuguese name")
            #expect(!book.localizedAbbreviations(for: .portuguese).isEmpty, "\(book.id) has no Portuguese abbreviations")
        }
        #expect(BibleBook.book(id: "John")?.localizedName(for: .portuguese) == "João")
        #expect(BibleBook.book(id: "Gen")?.localizedName(for: .portuguese) == "Gênesis")
        #expect(BibleBook.book(id: "Rev")?.localizedName(for: .portuguese) == "Apocalipse")
    }

    @Test func englishIsTheBase() {
        for book in BibleBook.canon {
            #expect(book.localizedName(for: .english) == book.name)
            #expect(book.localizedAbbreviations(for: .english).isEmpty)
        }
    }

    @Test func portugueseAbbreviationsNeverCollideWithEachOther() {
        var seen: [String: BookID] = [:]
        for book in BibleBook.canon {
            for key in ([book.localizedName(for: .portuguese)] + book.localizedAbbreviations(for: .portuguese)).map({ $0.lowercased().filter { !$0.isWhitespace } }) {
                if let other = seen[key], other != book.id {
                    Issue.record("\(key) is used by \(other) and \(book.id)")
                }
                seen[key] = book.id
            }
        }
    }

    @Test func languageFollowsTheLocale() {
        #expect(BookLanguage(locale: Locale(identifier: "en_BR")) == .portuguese)
        #expect(BookLanguage(locale: Locale(identifier: "es_BR")) == .portuguese)
        #expect(BookLanguage(locale: Locale(identifier: "pt_BR")) == .portuguese)
        #expect(BookLanguage(locale: Locale(identifier: "pt_PT")) == .portuguese)
        #expect(BookLanguage(locale: Locale(identifier: "en_US")) == .english)
        #expect(BookLanguage(locale: Locale(identifier: "es_ES")) == .english) // unsupported → default
    }

    @Test func formattedReferencesFollowTheLanguage() {
        let ref = PassageReference(bookId: "John", chapter: 3, verses: 16...18)
        #expect(ref.formatted(for: .english) == "John 3:16-18")
        #expect(ref.formatted(for: .portuguese) == "João 3:16-18")
        #expect(PassageReference(bookId: "Song", chapter: 2).formatted(for: .portuguese) == "Cântico dos Cânticos 2")
    }

    @Test func divisionTitles() {
        #expect(BibleBook.Division.poetry.localizedTitle(for: .portuguese) == "Poesia e Sabedoria")
        #expect(BibleBook.Division.poetry.localizedTitle(for: .english) == "Poetry & Wisdom")
    }
}
