import SwiftUI

/// A row of content that drifts sideways on its own and can be pushed sideways
/// with a finger.
///
/// The one place ambience is allowed (DESIGN_SYSTEM.md "Drifting rows"): the
/// arrival chooser, where a handful of words should read like a surface you
/// browse rather than a form you must answer. What keeps it calm and cheap:
///
/// - Drift is slow (`Motion.Drift`, points per second), so a word stays
///   readable and a finger always overtakes it.
/// - Nothing re-lays out per frame. The row is built once and only its
///   transform animates, so a drifting row costs a layer offset, not a layout
///   pass, and one implicit animation runs for the life of the row.
/// - The content is laid out `copies` times so drift and drag always have
///   material either side of the window; because it repeats every `period`, the
///   drag offset can be wrapped by a whole period without a visible seam, which
///   is what keeps it bounded however far a finger travels.
/// - Reduce Motion stops the drift (the row stays draggable). Callers that also
///   need a still layout — VoiceOver, accessibility text sizes — should reach
///   for `FlowLayout` instead of this view.
public struct DriftingRow<Content: View>: View {
    public enum Direction {
        /// Drifts towards the leading edge.
        case leading
        /// Drifts towards the trailing edge.
        case trailing

        fileprivate var sign: CGFloat { self == .leading ? -1 : 1 }
    }

    private let speed: CGFloat
    private let direction: Direction
    private let spacing: CGFloat
    private let content: () -> Content

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// Width of one copy plus the gap that follows it: the distance after which
    /// the row looks exactly like itself again.
    @State private var period: CGFloat = 0

    public init(
        speed: CGFloat = Motion.Drift.medium,
        direction: Direction = .leading,
        spacing: CGFloat = Spacing.sm,
        @ViewBuilder content: @escaping () -> Content
    ) {
        self.speed = speed
        self.direction = direction
        self.spacing = spacing
        self.content = content
    }

    public var body: some View {
        DriftingTrack(
            period: period,
            speed: speed,
            sign: direction.sign,
            spacing: spacing,
            drifts: !reduceMotion,
            content: content
        )
        // The track's animation is set up once, on appear. Re-identifying it is
        // how a new measurement (or Reduce Motion) gets a clean one instead of a
        // second animation layered over the first.
        .id(DriftKey(period: period, drifts: !reduceMotion))
        .onPreferenceChange(CopyWidthKey.self) { width in
            let measured = width + spacing
            if abs(measured - period) > 0.5 { period = measured }
        }
    }
}

private struct DriftKey: Hashable {
    let period: CGFloat
    let drifts: Bool
}

/// The repeating strip itself, at a period it is given rather than one it
/// measures, so that measuring never restarts what is already running.
private struct DriftingTrack<Content: View>: View {
    /// Copies of the content laid end to end; the row is a window onto the
    /// middle of them.
    private static var copies: Int { 6 }
    /// How many of those copies sit before the window's origin.
    private static var copiesBefore: CGFloat { 2 }

    let period: CGFloat
    let speed: CGFloat
    let sign: CGFloat
    let spacing: CGFloat
    let drifts: Bool
    let content: () -> Content

    @State private var phase: CGFloat = 0
    @State private var drag: CGFloat = 0
    @State private var dragOrigin: CGFloat = 0
    @State private var isHorizontal: Bool?
    @State private var isPushing = false
    @State private var started = false

    var body: some View {
        // A flexible frame would let the oversized strip decide the row's width
        // and position; the window gives the row exactly the width it is offered
        // and pins the strip to its leading edge, whatever the strip measures.
        RowWindow {
            HStack(spacing: spacing) {
                ForEach(0..<Self.copies, id: \.self) { copy in
                    HStack(spacing: spacing) { content() }
                        .background(
                            GeometryReader { proxy in
                                Color.clear.preference(key: CopyWidthKey.self, value: proxy.size.width)
                            }
                        )
                        // One copy is the row; the others are only its wrap-around.
                        .accessibilityHidden(copy != 0)
                }
            }
            .fixedSize()
            .offset(x: -Self.copiesBefore * period + sign * phase + drag)
            .environment(\.isPushingDriftingRow, isPushing)
        }
        .clipped()
        .simultaneousGesture(push)
        .onAppear {
            // Returning to this screen can appear again without new state; one
            // drift per track, never a second layered over it.
            guard !started, drifts, period > 0, speed > 0 else { return }
            started = true
            withAnimation(.linear(duration: Double(period / speed)).repeatForever(autoreverses: false)) {
                phase = period
            }
        }
    }

    /// A horizontal push. Vertical travel is left to whatever is scrolling the
    /// page, so a row never steals a scroll.
    private var push: some Gesture {
        DragGesture(minimumDistance: Spacing.sm)
            .onChanged { value in
                if isHorizontal == nil {
                    isHorizontal = abs(value.translation.width) > abs(value.translation.height)
                }
                guard isHorizontal == true else { return }
                isPushing = true
                withAnimation(nil) { drag = wrapped(dragOrigin + value.translation.width) }
            }
            .onEnded { value in
                defer {
                    isHorizontal = nil
                    // The button under the finger ends its press on the same
                    // touch-up, in no guaranteed order; hold the flag a moment
                    // longer so a push never lands as a choice.
                    if isPushing { Task { try? await Task.sleep(for: .milliseconds(150)); isPushing = false } }
                }
                guard isHorizontal == true, period > 0 else { return }
                let predicted = value.predictedEndTranslation.width - value.translation.width
                let glide = min(max(predicted, -period / 2), period / 2)
                let target = drag + glide
                dragOrigin = wrapped(target)
                withAnimation(Motion.Drift.settle) { drag = target }
            }
    }

    /// The nearest equivalent offset to zero. Shifting by a whole period is
    /// invisible, so this keeps the offset small without moving the content.
    private func wrapped(_ value: CGFloat) -> CGFloat {
        guard period > 0 else { return 0 }
        let remainder = value.truncatingRemainder(dividingBy: period)
        if remainder > period / 2 { return remainder - period }
        if remainder < -period / 2 { return remainder + period }
        return remainder
    }
}

/// Takes the width it is offered and the height its content wants, then places
/// that content at its leading edge at the content's own size — so a strip far
/// wider than the screen hangs out of the row instead of resizing or moving it.
private struct RowWindow: Layout {
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let content = subviews.first?.sizeThatFits(.unspecified) ?? .zero
        return CGSize(width: proposal.width ?? content.width, height: content.height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        subviews.first?.place(at: CGPoint(x: bounds.minX, y: bounds.midY), anchor: .leading, proposal: .unspecified)
    }
}

extension EnvironmentValues {
    /// True while a finger is pushing the `DriftingRow` a view sits in. Controls
    /// inside a row read it to ignore the tap that ends a push.
    public var isPushingDriftingRow: Bool {
        get { self[IsPushingKey.self] }
        set { self[IsPushingKey.self] = newValue }
    }
}

private struct IsPushingKey: EnvironmentKey {
    static let defaultValue = false
}

private struct CopyWidthKey: PreferenceKey {
    static let defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = max(value, nextValue())
    }
}

extension View {
    /// Softens both ends of a row that bleeds past the reading column, so words
    /// arrive and leave instead of being cut off at the edge of the screen.
    public func edgeFade(_ width: CGFloat = Spacing.xxl) -> some View {
        mask(
            GeometryReader { proxy in
                let fade = min(width, proxy.size.width / 4) / max(proxy.size.width, 1)
                LinearGradient(
                    stops: [
                        .init(color: .clear, location: 0),
                        .init(color: .black, location: fade),
                        .init(color: .black, location: 1 - fade),
                        .init(color: .clear, location: 1),
                    ],
                    startPoint: .leading,
                    endPoint: .trailing
                )
            }
        )
    }
}
