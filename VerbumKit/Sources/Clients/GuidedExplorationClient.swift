import ComposableArchitecture
import Models

@DependencyClient
public struct GuidedExplorationClient: Sendable {
    public var explore: @Sendable (_ request: ExplorationRequest) async throws -> ExplorationPlan
}

extension GuidedExplorationClient: DependencyKey {
    public static let liveValue = Self.editorialPreview
    public static let previewValue = Self.editorialPreview
}

extension DependencyValues {
    public var guidedExploration: GuidedExplorationClient {
        get { self[GuidedExplorationClient.self] }
        set { self[GuidedExplorationClient.self] = newValue }
    }
}

extension GuidedExplorationClient {
    public static let editorialPreview = Self(explore: { request in
        let passages: [PassageReference]
        let question: String
        let pt = request.language == .portuguese
        switch request.feeling {
        case .anxious:
            passages = [.init(bookId: "Matt", chapter: 6, verses: 25...34), .init(bookId: "Phil", chapter: 4, verses: 4...9)]
            question = pt ? "Observe como a passagem aborda preocupações e o dia de hoje." : "Notice how the passage addresses worry and the present day."
        case .lost:
            passages = [.init(bookId: "Ps", chapter: 23, verses: 1...6), .init(bookId: "Jas", chapter: 1, verses: 5...8)]
            question = pt ? "Explore as imagens de orientação e os pedidos de sabedoria." : "Explore images of guidance and requests for wisdom."
        case .grateful:
            passages = [.init(bookId: "Ps", chapter: 103, verses: 1...5), .init(bookId: "1Thess", chapter: 5, verses: 16...18)]
            question = pt ? "Leia os motivos de gratidão e o contexto em que ela aparece." : "Read the reasons for gratitude and the context in which it appears."
        case .tired:
            passages = [.init(bookId: "Matt", chapter: 11, verses: 28...30), .init(bookId: "Ps", chapter: 23, verses: 1...6)]
            question = pt ? "Observe como descanso e cuidado são apresentados nestes textos." : "Notice how rest and care are presented in these texts."
        case .afraid:
            passages = [.init(bookId: "Ps", chapter: 56, verses: 1...4), .init(bookId: "1Sam", chapter: 17, verses: 41...50)]
            question = pt ? "Compare uma oração diante do medo com uma narrativa de enfrentamento." : "Compare a prayer in the face of fear with a narrative of confrontation."
        case .alone:
            passages = [.init(bookId: "Ps", chapter: 139, verses: 1...12), .init(bookId: "Rom", chapter: 8, verses: 31...39)]
            question = pt ? "Explore as imagens de presença e vínculo, lendo além de um verso isolado." : "Explore images of presence and connection beyond an isolated verse."
        case .angry:
            passages = [.init(bookId: "Jas", chapter: 1, verses: 19...20), .init(bookId: "Eph", chapter: 4, verses: 26...32)]
            question = pt ? "Observe as relações entre escuta, palavras e maneiras de agir." : "Notice the connections between listening, words and ways of acting."
        case .hopeless:
            passages = [.init(bookId: "Lam", chapter: 3, verses: 21...26), .init(bookId: "Rom", chapter: 8, verses: 18...25)]
            question = pt ? "Leia esperança junto do sofrimento ao redor, sem apagar a dificuldade." : "Read hope alongside the surrounding suffering, without erasing the difficulty."
        case .peaceful:
            passages = [.init(bookId: "Phil", chapter: 4, verses: 4...9), .init(bookId: "Ps", chapter: 23, verses: 1...6)]
            question = pt ? "Explore como paz e confiança aparecem no conjunto da passagem." : "Explore how peace and trust appear within the whole passage."
        }
        return ExplorationPlan(request: request, guidingQuestion: question, passages: passages, isEditorialPreview: true)
    })
}
