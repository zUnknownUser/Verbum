import Foundation
import Models

/// Decodes `GET /api/{translation}/{book}/{chapter}.json` into verse-level
/// passages. Keeps what the reader can show today: verse text, poetry line
/// breaks. Section headings, footnotes and word-of-Jesus marks are parsed but
/// not yet surfaced (the model has no field for them; tracked in ROADMAP).
enum HelloAOChapter {
    struct Response: Decodable {
        let translation: Translation
        let book: Book
        let chapter: Chapter

        struct Translation: Decodable { let id: String }
        struct Book: Decodable { let id: String }
        struct Chapter: Decodable {
            let number: Int
            let content: [Block]
        }
    }

    /// A block of chapter content. Unknown block types decode as `.other`.
    enum Block: Decodable {
        case heading(String)
        case verse(number: Int, text: String)
        case lineBreak
        case other

        private enum CodingKeys: String, CodingKey { case type, number, content }

        init(from decoder: any Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            switch try container.decode(String.self, forKey: .type) {
            case "heading":
                let parts = try container.decodeIfPresent([Inline].self, forKey: .content) ?? []
                self = .heading(Self.join(parts))
            case "verse":
                let number = try container.decode(Int.self, forKey: .number)
                let parts = try container.decodeIfPresent([Inline].self, forKey: .content) ?? []
                self = .verse(number: number, text: Self.join(parts))
            case "line_break":
                self = .lineBreak
            default:
                self = .other
            }
        }

        /// Inline runs join with spaces; poetry lines and explicit breaks join with newlines.
        static func join(_ parts: [Inline]) -> String {
            var out = ""
            var lastPoemLine: Int?
            for part in parts {
                switch part {
                case .text(let s, let poem):
                    if let poem, lastPoemLine != nil, poem != lastPoemLine {
                        out += "\n"
                    } else if !out.isEmpty, !out.hasSuffix("\n"), !out.hasSuffix(" "), !s.hasPrefix(" ") {
                        // Runs are usually already spaced; only add when both sides lack one.
                        if s.first?.isPunctuation == false { out += " " } else { }
                    }
                    out += s
                    if let poem { lastPoemLine = poem }
                case .lineBreak:
                    out += "\n"
                case .note:
                    break
                }
            }
            return out.split(separator: "\n").map { $0.trimmingCharacters(in: .whitespaces) }.joined(separator: "\n")
        }
    }

    /// One inline run inside a verse or heading.
    enum Inline: Decodable {
        case text(String, poem: Int?)
        case lineBreak
        case note

        private enum CodingKeys: String, CodingKey { case text, poem, noteId, lineBreak }

        init(from decoder: any Decoder) throws {
            if let single = try? decoder.singleValueContainer(), let s = try? single.decode(String.self) {
                self = .text(s, poem: nil)
                return
            }
            let container = try decoder.container(keyedBy: CodingKeys.self)
            if container.contains(.noteId) {
                self = .note
            } else if let text = try container.decodeIfPresent(String.self, forKey: .text) {
                self = .text(text, poem: try container.decodeIfPresent(Int.self, forKey: .poem))
            } else if try container.decodeIfPresent(Bool.self, forKey: .lineBreak) == true {
                self = .lineBreak
            } else {
                self = .note
            }
        }
    }

    /// Verse-level passages, in order. `bookId` is the app's OSIS id.
    static func passages(from data: Data, bookId: BookID) throws -> [BiblePassage] {
        let response = try JSONDecoder().decode(Response.self, from: data)
        let chapter = response.chapter.number
        return response.chapter.content.compactMap { block in
            guard case .verse(let number, let text) = block else { return nil }
            return BiblePassage(
                id: "\(response.translation.id):\(bookId).\(chapter).\(number)",
                translationId: response.translation.id,
                bookId: bookId,
                chapter: chapter,
                verseStart: number,
                verseEnd: number,
                text: text
            )
        }
    }
}
