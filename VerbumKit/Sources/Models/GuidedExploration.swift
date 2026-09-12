/// A voluntary, ephemeral exploration intent, not a diagnosis or user profile.
public enum ArrivalFeeling: String, Codable, CaseIterable, Sendable {
    case anxious, lost, grateful, tired, afraid, alone, angry, hopeless, peaceful
}

/// Replace the client implementation with retrieval later, not the views.
/// No identifier, journal text or inferred emotion is sent by the mobile preview.
public struct ExplorationRequest: Equatable, Sendable {
    public let feeling: ArrivalFeeling
    public let language: BookLanguage
    public init(feeling: ArrivalFeeling, language: BookLanguage) {
        self.feeling = feeling
        self.language = language
    }
}

public struct ExplorationPlan: Equatable, Sendable {
    public let request: ExplorationRequest
    public let guidingQuestion: String
    /// These structured references are both destinations and primary sources.
    public let passages: [PassageReference]
    public let isEditorialPreview: Bool
    public init(request: ExplorationRequest, guidingQuestion: String, passages: [PassageReference], isEditorialPreview: Bool) {
        self.request = request
        self.guidingQuestion = guidingQuestion
        self.passages = passages
        self.isEditorialPreview = isEditorialPreview
    }
}
