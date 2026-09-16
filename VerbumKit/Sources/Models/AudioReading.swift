import Foundation

/// A measured interval in the exact recording; an English recording may omit the final end.
public struct AudioCue: Codable, Equatable, Hashable, Sendable {
    public let verseStart: Int
    public let verseEnd: Int
    public let start: Double
    public let end: Double?
    public init(verseStart: Int, verseEnd: Int, start: Double, end: Double? = nil) {
        self.verseStart = verseStart; self.verseEnd = verseEnd; self.start = start; self.end = end
    }
    public static func validated(_ cues: [Self]) -> [Self] {
        guard !cues.isEmpty, cues.count <= 256 else { return [] }
        var previousStart = -1.0; var previousEnd = 0.0
        for (index, cue) in cues.enumerated() {
            guard cue.verseStart > 0, cue.verseEnd >= cue.verseStart, cue.verseEnd <= 176,
                  cue.start.isFinite, cue.start >= 0, cue.start > previousStart, cue.start >= previousEnd,
                  cue.end.map({ $0.isFinite && $0 > cue.start }) ?? (index == cues.count - 1) else { return [] }
            previousStart = cue.start; previousEnd = cue.end ?? cue.start
        }
        return cues
    }
    public static func active(in cues: [Self], at time: Double) -> Self? {
        guard time.isFinite, time >= 0 else { return nil }
        return cues.last { time >= $0.start && ($0.end.map { time < $0 } ?? true) }
    }
}

/// Changes only at excerpt/playback boundaries, not on every player clock tick.
public struct AudioReadingPosition: Equatable, Sendable {
    public let reference: PassageReference
    public let translationID: String
    public let cue: AudioCue
    public let isPlaying: Bool
    public init(reference: PassageReference, translationID: String, cue: AudioCue, isPlaying: Bool) {
        self.reference = reference; self.translationID = translationID; self.cue = cue; self.isPlaying = isPlaying
    }
    public func contains(_ passage: BiblePassage) -> Bool {
        isPlaying && translationID == passage.translationId && reference.bookId == passage.bookId && reference.chapter == passage.chapter && (cue.verseStart...cue.verseEnd).contains(passage.verseStart)
    }
}
