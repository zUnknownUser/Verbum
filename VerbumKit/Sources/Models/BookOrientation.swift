/// Authored guidance for the whole book, separate from chapter commentary.
public struct BookOrientation: Equatable, Sendable, Codable {
    public let genre: String
    public let context: String
    public let purpose: String
    public let outline: [BookSection]
    public let readings: [PassageReference]
    public let sourceReferenceIds: [String]
}
public struct BookSection: Equatable, Sendable, Codable {
    public let title: String
    public let chapterStart: Int
    public let chapterEnd: Int
}
