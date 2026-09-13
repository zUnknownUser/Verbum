/// What a spoken conversation is about: the page the user started it from.
/// The companion is told this and nothing else about the user (spec §47).
public enum VoiceContext: Equatable, Sendable {
    /// A chapter open in the reader; its text is read through `BibleClient` when the conversation starts.
    case chapter(PassageReference)
    /// An entity page (§9): the facts and key passages shown there.
    case entity(EntityDetail)
    /// An Ask Scripture answer (§30), to go on from.
    case answer(question: String, ScriptureAnswer)

    /// A short title for the sheet: `1 Samuel 17`, `David`, the question.
    public var title: String {
        switch self {
        case .chapter(let reference): reference.formatted
        case .entity(let detail): detail.entity.name
        case .answer(let question, _): question
        }
    }
}
