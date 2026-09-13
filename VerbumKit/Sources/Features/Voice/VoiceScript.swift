import Clients
import Core
import Foundation
import Models

/// What the companion is told and what it may do. The instructions carry the
/// product's rules (§3.5, §13.3, §31) and the page's content; the tools are how
/// anything beyond that page is answered — through the same retrieval-first
/// `/v1/ask` and `/v1/search` as the rest of the app, never from memory (§73).
enum VoiceScript {
    static let askTool = VoiceTool(
        name: "ask_scripture",
        description: "Answer a Bible question from Scripture itself. Returns a short answer with the passages it rests on and a confidence. Use it for anything not already in the page you were given.",
        parametersJSON: #"{"type":"object","properties":{"question":{"type":"string","description":"The question, in English, in one sentence."}},"required":["question"]}"#
    )
    static let searchTool = VoiceTool(
        name: "search_scripture",
        description: "Find passages, people, places and themes for a word or phrase.",
        parametersJSON: #"{"type":"object","properties":{"query":{"type":"string"}},"required":["query"]}"#
    )
    static let openTool = VoiceTool(
        name: "open_passage",
        description: "Open a passage in the reader for the user. Only after they agreed to it.",
        parametersJSON: #"{"type":"object","properties":{"reference":{"type":"string","description":"An English reference such as \"1 Samuel 17:45-47\" or \"John 3\"."}},"required":["reference"]}"#
    )

    static func configuration(for context: VoiceContext, chapterText: String?, language: BookLanguage) -> VoiceConfiguration {
        let spoken = language == .portuguese ? "Brazilian Portuguese" : "English"
        var lines: [String] = [
            "You are the study companion inside Verbum, a Bible exploration app. You talk with the reader about the page they have open, in \(spoken).",
            "Turn-taking: answer only what was just asked, in one to three short sentences, then stop and wait. Never keep talking on your own, never add unasked-for material, never ask more than one question at a time. If what you heard was not a question about Scripture — a greeting, small talk, an unclear fragment — reply in one short sentence and wait. If you are not sure what was said, say so briefly and ask them to repeat.",
            "Stay with Scripture: its text, its people, places, history and how it has been read. When a question needs passages beyond this page, call ask_scripture and answer from what it returns; say which passages you are drawing on, by reference. Never quote or cite a verse you were not given by the page or by a tool.",
            "Distinguish what the text says from how traditions interpret it, and say when scholars or traditions disagree. Say plainly when you do not know.",
            "Never claim revelation from God, foretell the reader's future, promise healing or outcomes, or bless a personal decision as God's will. Prefer: \"this passage has traditionally been read as…\". For a question that is really about medical, legal, financial or mental-health help, say so kindly and point to a professional.",
            "If the reader wants to read something you mentioned, offer to open it and call open_passage only after they agree.",
        ]
        switch context {
        case .chapter(let reference):
            lines.append("The reader has \(reference.formatted(for: .english)) open. Its text (World English Bible):")
            if let chapterText { lines.append(String(chapterText.prefix(12_000))) }
        case .entity(let detail):
            var facts = ["The reader is on the page for \(detail.entity.name) (\(detail.entity.type.rawValue))."]
            if let summary = detail.entity.summary { facts.append(summary) }
            if !detail.aliases.isEmpty { facts.append("Also called: \(detail.aliases.joined(separator: ", ")).") }
            if let role = detail.role { facts.append("Role: \(role).") }
            if let dates = detail.approximateDates { facts.append("Dates: \(dates).") }
            if let geography = detail.modernGeography { facts.append("Today: \(geography).") }
            if !detail.keyPassages.isEmpty { facts.append("Key passages: \(detail.keyPassages.map { $0.formatted(for: .english) }.joined(separator: "; ")).") }
            lines.append(facts.joined(separator: " "))
        case .answer(let question, let answer):
            lines.append("The reader just asked Verbum: \"\(question)\". The written answer, grounded in \(answer.passageReferences.map { $0.formatted(for: .english) }.joined(separator: "; ")):")
            lines.append(answer.answer.isEmpty ? answer.summary : answer.answer)
            if answer.interpretiveVariance { lines.append("Traditions differ on this question; say so if it comes up.") }
        }
        let opening: String = switch (context, language) {
        case (.chapter(let reference), .portuguese): "Estou com \(reference.formatted(for: .portuguese)) aberto com você. O que quer entender melhor?"
        case (.chapter(let reference), .english): "I have \(reference.formatted(for: .english)) open with you. What would you like to understand better?"
        case (.entity(let detail), .portuguese): "Vamos falar de \(detail.entity.name). Pode perguntar."
        case (.entity(let detail), .english): "Let's talk about \(detail.entity.name). Ask away."
        case (.answer, .portuguese): "Li a resposta com você. Onde quer ir a partir daqui?"
        case (.answer, .english): "I've read the answer with you. Where would you like to go from here?"
        }
        return VoiceConfiguration(
            instructions: lines.joined(separator: "\n\n"),
            opening: opening,
            tools: [askTool, searchTool, openTool],
            voice: "marin",
            language: language.rawValue
        )
    }

    /// Runs one tool for the model. Results are JSON text; references are given
    /// in English so the model can say them and hand them back to `open_passage`.
    static func run(
        _ name: String,
        _ argumentsJSON: String,
        ask: AskScriptureClient,
        search: SearchClient,
        mentioned: @escaping @Sendable ([PassageReference]) async -> Void,
        open: @escaping @Sendable (PassageReference) async -> Void
    ) async -> String {
        let arguments = (try? JSONSerialization.jsonObject(with: Data(argumentsJSON.utf8))) as? [String: Any] ?? [:]
        switch name {
        case askTool.name:
            guard let question = arguments["question"] as? String, !question.isEmpty else { return json(["error": "question is required"]) }
            do {
                let answer = try await ask.ask(question: question)
                await mentioned(answer.passageReferences)
                return json([
                    "answer": answer.answer.isEmpty ? "No reliable, citable answer was found. Say so; do not answer from memory." : answer.answer,
                    "summary": answer.summary,
                    "passages": answer.passageReferences.map { $0.formatted(for: .english) },
                    "confidence": answer.confidence.rawValue,
                    "traditionsDisagree": answer.interpretiveVariance,
                ])
            } catch {
                return json(["error": "Ask is not available right now. Say so; do not answer from memory."])
            }
        case searchTool.name:
            guard let query = arguments["query"] as? String, !query.isEmpty else { return json(["error": "query is required"]) }
            do {
                let response = try await search.search(query: query)
                await mentioned(response.passages)
                return json([
                    "passages": response.passages.map { $0.formatted(for: .english) },
                    "entities": response.entities.prefix(8).map { ["name": $0.name, "kind": $0.type.rawValue, "summary": $0.summary ?? ""] },
                ])
            } catch {
                return json(["error": "Search is not available right now."])
            }
        case openTool.name:
            guard let text = arguments["reference"] as? String,
                  let reference = try? PassageReferenceParser.parse(text, language: .english) else {
                return json(["error": "not a reference I can open"])
            }
            await open(reference)
            return json(["opened": reference.formatted(for: .english)])
        default:
            return json(["error": "unknown tool \(name)"])
        }
    }

    private static func json(_ object: [String: Any]) -> String {
        guard let data = try? JSONSerialization.data(withJSONObject: object, options: [.sortedKeys]) else { return "{}" }
        return String(decoding: data, as: UTF8.self)
    }
}
