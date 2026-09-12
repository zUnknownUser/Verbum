/// A narrator's recording of one chapter.
public struct AudioNarrator: Identifiable, Codable, Equatable, Hashable, Sendable {
    public let id: String
    public let name: String
    public let url: String
    /// Where per-verse timings can be fetched later (phase 2: timestamp → verse).
    public let timingsPath: String?

    public init(id: String, name: String, url: String, timingsPath: String?) {
        self.id = id
        self.name = name
        self.url = url
        self.timingsPath = timingsPath
    }
}

/// Everything needed to listen to a chapter: which translation was recorded,
/// and who read it.
public struct ChapterAudio: Codable, Equatable, Sendable {
    public let translationId: String
    public let translationName: String
    public let reference: PassageReference
    public let narrators: [AudioNarrator]

    public init(translationId: String, translationName: String, reference: PassageReference, narrators: [AudioNarrator]) {
        self.translationId = translationId
        self.translationName = translationName
        self.reference = reference
        self.narrators = narrators
    }
}
