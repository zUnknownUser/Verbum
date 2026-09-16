import ComposableArchitecture
import Foundation
import Models

@DependencyClient
public struct ReaderAnnotationsClient: Sendable {
    public var load: @Sendable () async throws -> [ReaderAnnotation]
    public var save: @Sendable (_ annotation: ReaderAnnotation) async throws -> Void
}

extension ReaderAnnotationsClient: DependencyKey {
    public static var liveValue: Self { forCurrentAccount() }
    public static func forCurrentAccount() -> Self {
        let file = AnnotationFile.shared(owner: LocalAccountData.owner)
        return Self(load: { try await file.load() }, save: { try await file.save($0) })
    }
    public static let previewValue = Self(load: { [] }, save: { _ in })
}

extension DependencyValues {
    public var readerAnnotations: ReaderAnnotationsClient {
        get { self[ReaderAnnotationsClient.self] }
        set { self[ReaderAnnotationsClient.self] = newValue }
    }
}

/// Serialized, atomic writes; annotations remain on this device and never enter RAG prompts.
actor AnnotationFile {
    private static let files = LockIsolated<[String: AnnotationFile]>([:])
    static func shared(owner: String) -> AnnotationFile {
        files.withValue { files in
            if let file = files[owner] { return file }
            let file = AnnotationFile(owner: owner); files[owner] = file; return file
        }
    }
    let owner: String
    private let fileURL: URL?
    private let writable: (@Sendable () -> Bool)?
    init(owner: String, url: URL? = nil, writable: (@Sendable () -> Bool)? = nil) { self.owner = owner; fileURL = url; self.writable = writable }
    private var url: URL {
        fileURL ?? LocalAccountData.url("reader-annotations.json", owner: owner)
    }
    func load() throws -> [ReaderAnnotation] {
        guard FileManager.default.fileExists(atPath: url.path) else { return [] }
        let result = try JSONDecoder().decode([ReaderAnnotation].self, from: Data(contentsOf: url))
        return result
    }
    func save(_ annotation: ReaderAnnotation) throws {
        guard writable?() ?? !LocalAccountData.isDeleted(owner) else { throw CocoaError(.fileWriteNoPermission) }
        var values = try load().filter { $0.id != annotation.id }
        if annotation.highlight != nil || !annotation.note.isEmpty || annotation.bookmarked == true { values.append(annotation) }
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        try JSONEncoder().encode(values).write(to: url, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
    }
}
