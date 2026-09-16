import ComposableArchitecture
import Models
import SwiftUI
import UIKit

extension View {
    @ViewBuilder
    func readerControlMaterial() -> some View {
        if #available(iOS 26.0, *) {
            glassEffect(.regular.interactive(), in: .capsule)
        } else {
            background(.regularMaterial, in: Capsule())
        }
    }
}

// iOS 18+ keeps the native scroll APIs. On iOS 17, geometry reports what is
// visible and ScrollViewReader navigates to verses. UIKit supplies only the
// pixel-offset restoration that SwiftUI did not expose until iOS 18.
struct LegacyReaderScrollPage: View {
    let store: StoreOf<ChapterReaderFeature>
    let reference: PassageReference
    let continuous: Bool
    @Binding var followsAudio: Bool
    @Environment(\.audioReading) private var audioReading
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var space = UUID()
    @State private var position = LegacyReaderScrollPosition()
    @State private var visibleVerses: Set<String> = []
    @State private var visibleChapters: Set<PassageReference> = []
    @State private var offset = 0.0
    private var key: String { continuous ? "flow" : ReaderCanon.key(reference) }

    var body: some View {
        GeometryReader { viewport in
            ScrollViewReader { proxy in
                ScrollView {
                    ReaderScrollContent(store: store, reference: reference, continuous: continuous)
                        .background {
                            GeometryReader { geometry in
                                Color.clear.preference(
                                    key: LegacyReaderOffset.self,
                                    value: -geometry.frame(in: .named(space)).minY
                                )
                            }
                        }
                        .background(LegacyReaderScrollBridge(position: position))
                }
                .coordinateSpace(name: space)
                .environment(\.legacyReaderSpace, space)
                .scrollIndicators(.hidden)
                .simultaneousGesture(DragGesture().onChanged { value in
                    if abs(value.translation.height) > abs(value.translation.width) {
                        followsAudio = false
                    }
                })
                .onPreferenceChange(LegacyReaderOffset.self) { offset = $0 }
                .onPreferenceChange(LegacyReaderFrames.self) { frames in
                    updateVisibility(frames, viewport: CGRect(origin: .zero, size: viewport.size))
                }
                .task(id: offset) {
                    // Includes deceleration, unlike a drag gesture's onEnded.
                    do { try await Task.sleep(for: .milliseconds(200)) } catch { return }
                    saveOffset()
                }
                .onDisappear { saveOffset() }
                .task { store.send(.ensureChapter(reference)) }
                .task(id: store.navigationRevision) { restore(proxy) }
                .onChange(of: store.chapters[ReaderCanon.key(reference)]?.count) { _, _ in
                    restore(proxy)
                    followReading(proxy)
                }
                .onChange(of: audioReading) { _, _ in followReading(proxy) }
                .onChange(of: store.chapters[ReaderCanon.key(audioReading?.reference ?? reference)]?.count) { _, _ in
                    followReading(proxy)
                }
                .onChange(of: followsAudio) { _, enabled in
                    if enabled { followReading(proxy) }
                }
                .accessibilityAction(named: L10n.t("Show reading controls")) {
                    if store.focusMode { store.send(.focusToggled) }
                }
                .accessibilityAction(named: L10n.t("Next chapter")) { store.send(.nextChapterTapped) }
                .accessibilityAction(named: L10n.t("Previous chapter")) { store.send(.previousChapterTapped) }
            }
        }
    }

    private func updateVisibility(_ frames: [LegacyReaderElement: CGRect], viewport: CGRect) {
        let visible = frames.filter { Self.isVisible($0.value, in: viewport, threshold: 0.5) }
        visibleVerses = Set(visible.keys.compactMap {
            if case .verse(let id) = $0 { return id }
            return nil
        })
        guard continuous else { return }
        let chapters = frames.filter { Self.isVisible($0.value, in: viewport, threshold: 0.8) }
            .sorted { $0.value.minY < $1.value.minY }
            .compactMap { element, _ -> PassageReference? in
                if case .chapter(let reference) = element { return reference }
                return nil
            }
        for chapter in chapters where !visibleChapters.contains(chapter) {
            store.send(.chapterVisible(chapter))
        }
        visibleChapters = Set(chapters)
    }

    static func isVisible(_ frame: CGRect, in viewport: CGRect, threshold: CGFloat) -> Bool {
        guard frame.height > 0, frame.width > 0 else { return false }
        let intersection = frame.intersection(viewport)
        return !intersection.isNull && intersection.height / frame.height >= threshold
    }

    private func saveOffset() {
        guard let offset = position.currentOffset else { return }
        store.send(.scrollOffsetChanged(key, offset))
    }

    private func restore(_ proxy: ScrollViewProxy) {
        guard continuous || reference == store.reference else { return }
        // Wait for content before clamping a saved offset to the scroll range.
        guard store.chapters[ReaderCanon.key(reference)] != nil else { return }
        if let value = store.restoreOffset ?? store.scrollOffsets[key], store.requestedVerses == nil {
            position.scrollTo(y: value)
        } else if let verse = store.requestedVerses?.lowerBound {
            position.cancelPendingScroll()
            proxy.scrollTo("\(store.reference.bookId).\(store.reference.chapter).\(verse)", anchor: .top)
        } else {
            position.scrollTo(y: 0)
        }
    }

    private func followReading(_ proxy: ScrollViewProxy) {
        guard followsAudio, let audioReading, audioReading.isPlaying,
              continuous || reference == audioReading.reference,
              let verse = store.chapters[ReaderCanon.key(audioReading.reference)]?.first(where: {
                  $0.verseStart == audioReading.cue.verseStart
              }), verse.translationId == audioReading.translationID else { return }
        let id = "\(verse.bookId).\(verse.chapter).\(verse.verseStart)"
        guard !visibleVerses.contains(id) else { return }
        position.cancelPendingScroll()
        withAnimation(reduceMotion ? nil : .easeInOut(duration: 0.35)) {
            proxy.scrollTo(id, anchor: UnitPoint(x: 0.5, y: 0.28))
        }
    }
}

enum LegacyReaderElement: Hashable {
    case verse(String)
    case chapter(PassageReference)
}

private struct LegacyReaderSpace: EnvironmentKey {
    static let defaultValue: UUID? = nil
}

extension EnvironmentValues {
    fileprivate var legacyReaderSpace: UUID? {
        get { self[LegacyReaderSpace.self] }
        set { self[LegacyReaderSpace.self] = newValue }
    }
}

private struct LegacyReaderFrames: PreferenceKey {
    static var defaultValue: [LegacyReaderElement: CGRect] { [:] }
    static func reduce(value: inout [LegacyReaderElement: CGRect], nextValue: () -> [LegacyReaderElement: CGRect]) {
        value.merge(nextValue(), uniquingKeysWith: { _, new in new })
    }
}

private struct LegacyReaderOffset: PreferenceKey {
    static var defaultValue: Double { 0 }
    static func reduce(value: inout Double, nextValue: () -> Double) { value = nextValue() }
}

struct LegacyReaderFrame: ViewModifier {
    let element: LegacyReaderElement
    @Environment(\.legacyReaderSpace) private var space

    func body(content: Content) -> some View {
        content.background {
            if let space {
                GeometryReader { geometry in
                    Color.clear.preference(key: LegacyReaderFrames.self, value: [element: geometry.frame(in: .named(space))])
                }
            }
        }
    }
}

struct ReaderChapterVisibility: ViewModifier {
    let reference: PassageReference
    let action: (Bool) -> Void

    func body(content: Content) -> some View {
        if #available(iOS 18.0, *) {
            content.onScrollVisibilityChange(threshold: 0.8, action)
        } else {
            content.modifier(LegacyReaderFrame(element: .chapter(reference)))
        }
    }
}

@MainActor
final class LegacyReaderScrollPosition {
    weak var scrollView: UIScrollView?
    private var pendingOffset: Double?

    var currentOffset: Double? {
        scrollView.map { $0.contentOffset.y + $0.adjustedContentInset.top }
    }

    func scrollTo(y: Double) {
        pendingOffset = y
        DispatchQueue.main.async { [weak self] in self?.applyPendingScroll() }
    }

    func cancelPendingScroll() { pendingOffset = nil }

    func applyPendingScroll() {
        guard let scrollView, let offset = pendingOffset, scrollView.bounds.height > 0 else { return }
        scrollView.layoutIfNeeded()
        let top = scrollView.adjustedContentInset.top
        let maximum = max(0, scrollView.contentSize.height - scrollView.bounds.height + top + scrollView.adjustedContentInset.bottom)
        pendingOffset = nil
        scrollView.setContentOffset(CGPoint(x: scrollView.contentOffset.x, y: min(max(0, offset), maximum) - top), animated: false)
    }
}

private struct LegacyReaderScrollBridge: UIViewRepresentable {
    let position: LegacyReaderScrollPosition

    func makeUIView(context: Context) -> Probe {
        let view = Probe()
        view.isUserInteractionEnabled = false
        view.position = position
        return view
    }

    func updateUIView(_ view: Probe, context: Context) {
        view.position = position
        view.connectAfterLayout()
    }

    final class Probe: UIView {
        weak var position: LegacyReaderScrollPosition?

        override func didMoveToWindow() {
            super.didMoveToWindow()
            connectAfterLayout()
        }

        func connectAfterLayout() {
            DispatchQueue.main.async { [weak self] in
                guard let self, self.window != nil else { return }
                var ancestor = self.superview
                while let view = ancestor {
                    if let scrollView = view as? UIScrollView {
                        self.position?.scrollView = scrollView
                        self.position?.applyPendingScroll()
                        return
                    }
                    ancestor = view.superview
                }
            }
        }
    }
}
