import ComposableArchitecture
import Foundation
import Models

@DependencyClient
public struct ReaderAnnotationsClient: Sendable {
    public var load: @Sendable () async throws -> [ReaderAnnotation]
    public var save: @Sendable (_ annotation: ReaderAnnotation) async throws -> Void
}

extension ReaderAnnotationsClient: DependencyKey {
    public static let liveValue = Self(
        load: { try await AnnotationFile.shared.load() },
        save: { try await AnnotationFile.shared.save($0) }
    )
    public static let previewValue = Self(load: { [] }, save: { _ in })
}

extension DependencyValues {
    public var readerAnnotations: ReaderAnnotationsClient {
        get { self[ReaderAnnotationsClient.self] }
        set { self[ReaderAnnotationsClient.self] = newValue }
    }
}

/// Serialized, atomic writes; annotations remain on this device and never enter RAG prompts.
private actor AnnotationFile {
    static let shared = AnnotationFile()
    private var cache: [ReaderAnnotation]?
    private var url: URL {
        FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("Verbum", isDirectory: true).appendingPathComponent("reader-annotations.json")
    }
    func load() throws -> [ReaderAnnotation] {
        if let cache { return cache }
        guard FileManager.default.fileExists(atPath: url.path) else { cache = []; return [] }
        let result = try JSONDecoder().decode([ReaderAnnotation].self, from: Data(contentsOf: url))
        cache = result; return result
    }
    func save(_ annotation: ReaderAnnotation) throws {
        var values = try load().filter { $0.id != annotation.id }
        if annotation.highlight != nil || !annotation.note.isEmpty { values.append(annotation) }
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        try JSONEncoder().encode(values).write(to: url, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
        cache = values
    }
}
