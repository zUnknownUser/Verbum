import Foundation

/// What Ask Scripture returns (spec §30, the AI response data contract, verbatim;
/// the wire shape is `AskResponse` in api/openapi.yaml). References are structured
/// fields, never parsed out of prose (§30): the client renders and navigates from
/// `passageReferences`, and the server guarantees each one was retrieved and
/// verified before the model could cite it (§31).
public struct ScriptureAnswer: Codable, Equatable, Sendable {
    /// How sure the server is that the answer is grounded in what it retrieved (§31).
    public enum Confidence: String, Codable, Equatable, Sendable {
        case low, medium, high
    }

    /// Empty when no reliable, citable answer was found (§51: trust over always answering).
    public let fallback: UsageRestriction?
    public let answer: String
    public let summary: String
    /// Only passages the server itself retrieved and the model actually cited.
    public let passageReferences: [PassageReference]
    /// Ids of graph entities whose key passages cover a cited passage; may be empty.
    public let entityReferences: [String]
    public let sourceReferences: [SourceReference]
    public let confidence: Confidence
    /// True when traditions or scholars meaningfully disagree on the question (§31).
    public let interpretiveVariance: Bool

    public init(
        answer: String,
        summary: String,
        passageReferences: [PassageReference],
        entityReferences: [String],
        sourceReferences: [SourceReference],
        confidence: Confidence,
        interpretiveVariance: Bool,
        fallback: UsageRestriction? = nil
    ) {
        self.fallback = fallback
        self.answer = answer
        self.summary = summary
        self.passageReferences = passageReferences
        self.entityReferences = entityReferences
        self.sourceReferences = sourceReferences
        self.confidence = confidence
        self.interpretiveVariance = interpretiveVariance
    }

    /// The server found nothing it could stand behind: show the §51 fallback,
    /// and the closest passages if it named any.
    public var isEmpty: Bool { answer.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
}
