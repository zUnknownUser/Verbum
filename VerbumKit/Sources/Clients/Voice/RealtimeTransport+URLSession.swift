import Foundation

/// `URLSessionWebSocketTask`: one socket, text frames in and out.
public final class URLSessionRealtimeTransport: RealtimeTransport, @unchecked Sendable {
    private let lock = NSLock()
    private var task: URLSessionWebSocketTask?

    public init() {}

    public func connect(url: URL, headers: [String: String]) async throws -> AsyncThrowingStream<String, Error> {
        var request = URLRequest(url: url, timeoutInterval: 20)
        for (name, value) in headers { request.setValue(value, forHTTPHeaderField: name) }
        let task = URLSession.shared.webSocketTask(with: request)
        lock.withLock { self.task = task }
        task.resume()
        return AsyncThrowingStream { continuation in
            let receiver = Task {
                do {
                    while !Task.isCancelled {
                        let message = try await task.receive()
                        switch message {
                        case .string(let text): continuation.yield(text)
                        case .data(let data): continuation.yield(String(decoding: data, as: UTF8.self))
                        @unknown default: break
                        }
                    }
                    continuation.finish()
                } catch {
                    // A close initiated by us ends the stream quietly; anything else is a failure.
                    if task.closeCode != .invalid && task.closeCode != .abnormalClosure { continuation.finish() } else { continuation.finish(throwing: error) }
                }
            }
            continuation.onTermination = { _ in receiver.cancel() }
        }
    }

    public func send(_ text: String) async throws {
        guard let task = lock.withLock({ self.task }) else { return }
        try await task.send(.string(text))
    }

    public func close() async {
        let task = lock.withLock { () -> URLSessionWebSocketTask? in
            let t = self.task
            self.task = nil
            return t
        }
        task?.cancel(with: .normalClosure, reason: nil)
    }
}
