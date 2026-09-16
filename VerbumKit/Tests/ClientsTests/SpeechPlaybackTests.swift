import Foundation
import Testing
import Models
@testable import Clients

@Suite struct SpeechPlaybackTests {
    @Test func onlySameOriginPlaybackPathsAreAccepted() throws {
        let api = VerbumAPI(baseURL: URL(string: "https://example.test")!)
        let path = "/v1/tts/playback/" + String(repeating: "a", count: 64)
        #expect(try api.playbackURL(path + "/index.m3u8").absoluteString == "https://example.test" + path + "/index.m3u8")
        for value in ["https://evil.test/audio", path + "/../secret", path + "/status?x=y"] {
            #expect(throws: VerbumAPIError.self) { try api.playbackURL(value) }
        }
    }
}
