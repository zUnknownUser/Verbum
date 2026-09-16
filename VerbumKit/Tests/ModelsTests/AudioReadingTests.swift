import Foundation
import Models
import Testing

@Suite struct AudioReadingTests {
    @Test func followsPlaybackTimeIncludingSeeksWithoutEstimatingProgress() {
        let cues = [AudioCue(verseStart: 1, verseEnd: 3, start: 0, end: 10), AudioCue(verseStart: 4, verseEnd: 6, start: 10, end: 25)]
        #expect(AudioCue.active(in: cues, at: 9.99)?.verseStart == 1)
        #expect(AudioCue.active(in: cues, at: 10)?.verseStart == 4)
        #expect(AudioCue.active(in: cues, at: 2)?.verseStart == 1)
        #expect(AudioCue.active(in: cues, at: 25) == nil)
        #expect(AudioCue.validated([cues[1], cues[0]]).isEmpty)
        #expect(AudioCue.validated([.init(verseStart: 1, verseEnd: 1, start: .nan)]).isEmpty)
    }
    @Test func translationAndPlaybackMustMatch() {
        let verse = BiblePassage(id: "v", translationId: "por_blj", bookId: "John", chapter: 1, verseStart: 1, verseEnd: 1, text: "Texto")
        let cue = AudioCue(verseStart: 1, verseEnd: 3, start: 0, end: 10)
        let ref = PassageReference(bookId: "John", chapter: 1)
        #expect(!AudioReadingPosition(reference: ref, translationID: "BSB", cue: cue, isPlaying: true).contains(verse))
        #expect(!AudioReadingPosition(reference: ref, translationID: "por_blj", cue: cue, isPlaying: false).contains(verse))
        #expect(AudioReadingPosition(reference: ref, translationID: "por_blj", cue: cue, isPlaying: true).contains(verse))
    }
    @Test func previousAnnotationsDecodeAndNewStylesRoundTrip() throws {
        let note = ReaderAnnotation(reference: .init(bookId: "John", chapter: 1, verses: 1...1), highlight: .gold, note: "Nota", highlightStyle: .margin)
        let data = try JSONEncoder().encode(note)
        #expect(try JSONDecoder().decode(ReaderAnnotation.self, from: data) == note)
        var json = try #require(JSONSerialization.jsonObject(with: data) as? [String: Any])
        json.removeValue(forKey: "highlightStyle")
        let legacy = try JSONDecoder().decode(ReaderAnnotation.self, from: JSONSerialization.data(withJSONObject: json))
        #expect(legacy.note == "Nota")
        #expect(legacy.highlightStyle == nil)
    }
}
