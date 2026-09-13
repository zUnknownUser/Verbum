import AVFoundation
import Foundation

/// `AVAudioEngine` in and out at PCM16 mono 24 kHz, with the system's voice
/// processing (echo cancellation) on the input so the companion can be heard
/// through the speaker without hearing itself. `.voiceChat` mode, speaker by
/// default, Bluetooth headsets allowed.
public actor AVAudioEngineVoiceAudio: VoiceAudio {
    private var engine: AVAudioEngine?
    private var player: AVAudioPlayerNode?
    /// Buffers scheduled and not yet heard.
    private var pendingBuffers = 0
    private let rate: Double = 24_000
    private let playbackFormat = AVAudioFormat(commonFormat: .pcmFormatFloat32, sampleRate: 24_000, channels: 1, interleaved: false)!
    private let wireFormat = AVAudioFormat(commonFormat: .pcmFormatInt16, sampleRate: 24_000, channels: 1, interleaved: true)!

    public init() {}

    public func requestPermission() async -> Bool {
        await AVAudioApplication.requestRecordPermission()
    }

    public func startCapture(_ onChunk: @escaping @Sendable (Data) -> Void) async throws {
        let session = AVAudioSession.sharedInstance()
        try session.setCategory(.playAndRecord, mode: .voiceChat, options: [.defaultToSpeaker, .allowBluetoothHFP])
        try session.setActive(true)

        let engine = AVAudioEngine()
        let player = AVAudioPlayerNode()
        engine.attach(player)
        engine.connect(player, to: engine.mainMixerNode, format: playbackFormat)
        // Echo cancellation; not every route (or the simulator) supports it, and the
        // conversation still works without it.
        try? engine.inputNode.setVoiceProcessingEnabled(true)

        let input = engine.inputNode
        let inputFormat = input.outputFormat(forBus: 0)
        guard inputFormat.sampleRate > 0, let converter = AVAudioConverter(from: inputFormat, to: wireFormat) else {
            throw VoiceError.failed
        }
        let wire = wireFormat
        let ratio = rate / inputFormat.sampleRate
        input.installTap(onBus: 0, bufferSize: AVAudioFrameCount(inputFormat.sampleRate / 10), format: inputFormat) { buffer, _ in
            let capacity = AVAudioFrameCount(Double(buffer.frameLength) * ratio) + 16
            guard let out = AVAudioPCMBuffer(pcmFormat: wire, frameCapacity: capacity) else { return }
            var consumed = false
            var error: NSError?
            converter.convert(to: out, error: &error) { _, status in
                if consumed { status.pointee = .noDataNow; return nil }
                consumed = true
                status.pointee = .haveData
                return buffer
            }
            guard error == nil, out.frameLength > 0, let channel = out.int16ChannelData else { return }
            onChunk(Data(bytes: channel[0], count: Int(out.frameLength) * MemoryLayout<Int16>.size))
        }

        engine.prepare()
        try engine.start()
        self.engine = engine
        self.player = player
    }

    public func stopCapture() async {
        engine?.inputNode.removeTap(onBus: 0)
    }

    public func play(_ pcm: Data) async {
        guard let player, let engine, engine.isRunning else { return }
        let frames = pcm.count / MemoryLayout<Int16>.size
        guard frames > 0, let buffer = AVAudioPCMBuffer(pcmFormat: playbackFormat, frameCapacity: AVAudioFrameCount(frames)) else { return }
        buffer.frameLength = AVAudioFrameCount(frames)
        let samples = buffer.floatChannelData![0]
        pcm.withUnsafeBytes { raw in
            let int16 = raw.bindMemory(to: Int16.self)
            for i in 0..<frames { samples[i] = Float(Int16(littleEndian: int16[i])) / 32768 }
        }
        pendingBuffers += 1
        player.scheduleBuffer(buffer, completionCallbackType: .dataPlayedBack) { [weak self] _ in
            Task { await self?.bufferPlayedBack() }
        }
        if !player.isPlaying { player.play() }
    }

    private func bufferPlayedBack() {
        pendingBuffers = max(0, pendingBuffers - 1)
    }

    public func isPlaybackActive() async -> Bool {
        pendingBuffers > 0
    }

    public func stopPlayback() async {
        player?.stop()
        pendingBuffers = 0
    }

    public func finish() async {
        engine?.inputNode.removeTap(onBus: 0)
        player?.stop()
        pendingBuffers = 0
        engine?.stop()
        engine = nil
        player = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }
}
