import Foundation
import Models
import Testing
@testable import Clients

@Suite struct AudioTimingTests {
    @Test func timingsMustIdentifyTheExactRecording() throws {
        let data = Data(#"{"translationId":"BSB","bookId":"JHN","chapterNumber":1,"reader":"david","audioLink":"https://example.test/john.mp3","verses":[2.5,8,13.25]}"#.utf8)
        let narrator = AudioNarrator(id: "david", name: "David", url: "https://example.test/john.mp3", timingsPath: nil)
        let cues = try HelloAOChapterAudio.timings(data, narrator: narrator, translation: "BSB", book: "JHN", chapter: 1)
        #expect(cues.count == 3)
        #expect(AudioCue.active(in: cues, at: 2) == nil)
        #expect(AudioCue.active(in: cues, at: 8)?.verseStart == 2)
        #expect(try HelloAOChapterAudio.timings(data, narrator: narrator, translation: "por_blj", book: "JHN", chapter: 1).isEmpty)
        #expect(try HelloAOChapterAudio.timings(data, narrator: narrator, translation: "BSB", book: "JHN", chapter: 2).isEmpty)
    }
}
