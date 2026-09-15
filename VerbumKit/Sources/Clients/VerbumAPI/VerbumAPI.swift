import CryptoKit
import Foundation
import Models

/// The Verbum backend (`api/openapi.yaml`, spec §45): one function per route,
/// decoding the contract's JSON into the app's models. The `.live` values of
/// `GraphClient`, `SearchClient`, `ContextClient`, `TimelineClient` and
/// `AskScriptureClient` are thin wrappers over this.
///
/// Editorial routes (`GET /v1/...`) are keyless and cacheable: every answer is
/// kept on disk so recent entities, context and the timeline stay readable
/// offline (spec §39), and a fresh copy is served without a request for as
/// long as the server says it may be (`Cache-Control: max-age`). `POST` routes
/// (Ask, realtime) are never cached (spec §47: minimise retention of questions).
public struct VerbumAPI: Sendable {
    /// Performs one request. Injected so tests never touch the network.
    public typealias Transport = @Sendable (URLRequest) async throws -> (Data, HTTPURLResponse)
    public typealias TokenProvider = @Sendable (_ createIfNeeded: Bool) async throws -> String?

    public let baseURL: URL
    let transport: Transport
    let cache: ResponseCache
    let now: @Sendable () -> Date
    let tokenProvider: TokenProvider

    public init(baseURL: URL, transport: @escaping Transport = Self.urlSession, cache: ResponseCache = .onDisk, tokenProvider: @escaping TokenProvider = { _ in nil }, now: @escaping @Sendable () -> Date = { Date() }) {
        self.baseURL = baseURL
        self.transport = transport
        self.cache = cache
        self.now = now
        self.tokenProvider = tokenProvider
    }

    /// The backend the app is built against — see `VerbumAPI.Configuration`.
    public static let shared = VerbumAPI(baseURL: Configuration.baseURL, tokenProvider: {
        try await FirebaseAPITokens.shared.token(createIfNeeded: $0)
    })

    /// Plain `URLSession` with a short timeout: the API answers from a database,
    /// and the reader must not hang on a dead server.
    public static let urlSession: Transport = { request in
        let (data, response) = try await URLSession.shared.data(for: request, delegate: APIRedirectPolicy())
        guard let http = response as? HTTPURLResponse else { throw VerbumAPIError.malformedResponse }
        return (data, http)
    }

    static let requestTimeout: TimeInterval = 15
    /// Mirrors the server's `Cache-Control: public, max-age=3600` on editorial routes.
    static let freshFor: TimeInterval = 3600

    // MARK: Requests

    /// `GET path?query`, decoded as `T`. Cache-first while fresh; on a network
    /// failure the last good answer is returned even if stale, and only when
    /// there is none does the call fail with `.networkUnavailable`.
    func get<T: Decodable>(_ path: String, query: [URLQueryItem] = [], as type: T.Type = T.self) async throws -> T {
        let url = url(path, query: query)
        // Optional identity adds semantic search; public browsing never creates a guest.
        var token: String?
        if path == "/v1/search" {
            do { token = try await tokenProvider(false) }
            catch is CancellationError { throw CancellationError() }
            catch { token = nil }
        }
        let key = ResponseCache.key(for: url) + (path == "/v1/search" ? (token == nil ? "-lexical" : "-semantic") : "")
        if let hit = await cache.read(key), now().timeIntervalSince(hit.storedAt) < Self.freshFor {
            return try decode(T.self, from: hit.data)
        }
        var request = URLRequest(url: url, timeoutInterval: Self.requestTimeout)
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if let token { request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        let data: Data
        do {
            data = try await send(request)
        } catch VerbumAPIError.networkUnavailable {
            if let stale = await cache.read(key) { return try decode(T.self, from: stale.data) }
            throw VerbumAPIError.networkUnavailable
        }
        let value = try decode(T.self, from: data)
        await cache.write(data, for: key, at: now())
        return value
    }

    /// `POST path` with a JSON body, decoded as `T`. Never cached.
    func post<T: Decodable>(_ path: String, body: some Encodable, as type: T.Type = T.self) async throws -> T {
        var request = URLRequest(url: url(path), timeoutInterval: Self.postTimeout)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONEncoder().encode(body)
        try await authorize(&request)
        return try decode(T.self, from: try await send(request))
    }

    /// Ask retrieves and then synthesises (the server allows itself 30 s).
    static let postTimeout: TimeInterval = 45

    /// `POST path` with a JSON body; the raw response bytes (audio, not the JSON contract).
    /// Never cached — matches the server's `Cache-Control: no-store` on this route.
    func postForData(_ path: String, body: some Encodable, timeout: TimeInterval) async throws -> Data {
        var request = URLRequest(url: url(path), timeoutInterval: timeout)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONEncoder().encode(body)
        try await authorize(&request)
        return try await send(request)
    }

    private func authorize(_ request: inout URLRequest) async throws {
        if let token = try await tokenProvider(true) {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
    }

    /// Chapter speech generation: the server allows itself up to `tts.GenerationTimeout` (10 min)
    /// plus 30 s to reply; the client waits slightly longer still.
    static let speechTimeout: TimeInterval = 630

    private func url(_ path: String, query: [URLQueryItem] = []) -> URL {
        var components = URLComponents(url: baseURL.appending(path: path), resolvingAgainstBaseURL: false)!
        if !query.isEmpty { components.queryItems = query }
        return components.url!
    }

    /// One place that turns transport outcomes into `VerbumAPIError` (spec §52:
    /// network, content and malformed answers are different states).
    private func send(_ request: URLRequest) async throws -> Data {
        let data: Data
        let response: HTTPURLResponse
        do {
            (data, response) = try await transport(request)
        } catch let error as VerbumAPIError {
            throw error
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            throw VerbumAPIError.networkUnavailable
        }
        guard (200..<300).contains(response.statusCode) else {
            let problem = try? JSONDecoder().decode(Problem.self, from: data)
            throw VerbumAPIError.problem(problem?.code ?? .unknown, status: response.statusCode)
        }
        return data
    }

    private func decode<T: Decodable>(_ type: T.Type, from data: Data) throws -> T {
        do {
            return try JSONDecoder().decode(type, from: data)
        } catch {
            throw VerbumAPIError.malformedResponse
        }
    }
}

/// The API has canonical endpoints. Do not forward identity or a paid POST body
/// to a redirect destination; callers handle the 3xx through ordinary errors.
private final class APIRedirectPolicy: NSObject, URLSessionTaskDelegate, Sendable {
    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse, newRequest request: URLRequest, completionHandler: @escaping @Sendable (URLRequest?) -> Void) {
        completionHandler(nil)
    }
}

/// Why a call failed, in the terms the features switch on (spec §52).
/// The server's `message` is never carried: it is for logs, not users.
public enum VerbumAPIError: Error, Equatable, Sendable {
    /// Could not reach the server and nothing is cached.
    case networkUnavailable
    /// The server answered with a `Problem` (spec §52 one error shape).
    case problem(Problem.Code, status: Int)
    /// The answer was not the contract's JSON.
    case malformedResponse
}

/// The one error shape of the API (`api/openapi.yaml` `Problem`).
public struct Problem: Decodable, Equatable, Sendable {
    public enum Code: String, Decodable, Equatable, Sendable {
        case unknownEntity = "unknown_entity"
        case unknownBook = "unknown_book"
        case contentUnavailable = "content_unavailable"
        case malformedRequest = "malformed_request"
        case internalError = "internal"
        case realtimeUnavailable = "realtime_unavailable"
        case askUnavailable = "ask_unavailable"
        case ttsUnavailable = "tts_unavailable"
        case ttsRateLimited = "tts_rate_limited"
        case ttsTimeout = "tts_timeout"
        case ttsFailed = "tts_failed"
        case unauthenticated
        case authUnavailable = "auth_unavailable"
        case rateLimited = "rate_limited"
        /// A code this build does not know; treated as a failure, never shown.
        case unknown

        public init(from decoder: Decoder) throws {
            let raw = try decoder.singleValueContainer().decode(String.self)
            self = Code(rawValue: raw) ?? .unknown
        }
    }

    public let code: Code
    public let message: String
}

// MARK: - Configuration

extension VerbumAPI {
    /// Where the backend is. The app target sets `VerbumAPIBaseURL` in its
    /// Info.plist from the `VERBUM_API_BASE_URL` build setting (Debug: the
    /// local `go run ./cmd/api`; Release: the production host from
    /// `api/openapi.yaml`). No key ships in the bundle: the API is keyless (§45)
    /// and OpenAI is only ever reached by the server (§56).
    public enum Configuration {
        public static let infoPlistKey = "VerbumAPIBaseURL"
        public static let production = URL(string: "https://api.vendlydigital.com.br")!
        public static let local = URL(string: "http://localhost:8080")!

        /// Resolved once, in this order: the launch argument / defaults key
        /// `VerbumAPIBaseURL` (a LAN backend while developing, without touching
        /// the project), the Info.plist value, then the build's default.
        public static var baseURL: URL {
            if let raw = UserDefaults.standard.string(forKey: infoPlistKey), let url = Self.url(raw) { return url }
            if let raw = Bundle.main.object(forInfoDictionaryKey: infoPlistKey) as? String, let url = Self.url(raw) { return url }
            #if DEBUG
            return local
            #else
            return production
            #endif
        }

        private static func url(_ raw: String) -> URL? {
            let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            guard let url = URL(string: trimmed), let scheme = url.scheme, ["http", "https"].contains(scheme), url.host != nil else { return nil }
            return url
        }
    }
}

// MARK: - Cache

/// Answers already received, as files in Caches keyed by the request URL.
/// Same shape as `ChapterCache`: small, explicit, enough for §39 until a
/// real local store is needed.
public actor ResponseCache {
    public struct Entry: Sendable {
        public let data: Data
        public let storedAt: Date
    }

    private let directory: URL?
    private var memory: [String: Entry] = [:]

    public init(directory: URL?) {
        self.directory = directory
    }

    public static let onDisk = ResponseCache(
        directory: FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first?.appendingPathComponent("VerbumAPI", isDirectory: true)
    )
    /// A fresh, empty cache — one per test.
    public static var inMemory: ResponseCache { ResponseCache(directory: nil) }

    static func key(for url: URL) -> String {
        let digest = SHA256.hash(data: Data(url.absoluteString.utf8))
        return digest.map { String(format: "%02x", $0) }.joined()
    }

    func read(_ key: String) -> Entry? {
        if let hit = memory[key] { return hit }
        guard let url = fileURL(key), let data = try? Data(contentsOf: url),
              let attributes = try? FileManager.default.attributesOfItem(atPath: url.path),
              let modified = attributes[.modificationDate] as? Date else { return nil }
        let entry = Entry(data: data, storedAt: modified)
        memory[key] = entry
        return entry
    }

    func write(_ data: Data, for key: String, at date: Date) {
        memory[key] = Entry(data: data, storedAt: date)
        guard let url = fileURL(key) else { return }
        try? FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        try? data.write(to: url, options: .atomic)
        try? FileManager.default.setAttributes([.modificationDate: date], ofItemAtPath: url.path)
    }

    private func fileURL(_ key: String) -> URL? {
        directory?.appendingPathComponent("\(key).json")
    }
}
