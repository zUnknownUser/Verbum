import Models
import SwiftUI

private struct AudioReadingKey: EnvironmentKey {
    static let defaultValue: AudioReadingPosition? = nil
}
extension EnvironmentValues {
    var audioReading: AudioReadingPosition? {
        get { self[AudioReadingKey.self] }
        set { self[AudioReadingKey.self] = newValue }
    }
}
