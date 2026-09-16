import CoreGraphics
import ComposableArchitecture
import Models
import SwiftUI
import Testing
import UIKit
@testable import Features

@MainActor
@Suite(.serialized) struct ReaderCompatibilityTests {
    @Test func legacyReaderRestoresSavedPositionInHostedScrollView() async throws {
        let reference = PassageReference(bookId: "John", chapter: 3)
        var state = ChapterReaderFeature.State(reference: reference)
        state.chapters["John.3"] = (1...40).map {
            BiblePassage(id: "t:John.3.\($0)", translationId: "t", bookId: "John", chapter: 3,
                         verseStart: $0, verseEnd: $0, text: "A verse long enough to occupy several lines in the reader and exercise its saved scroll position.")
        }
        state.scrollOffsets["John.3"] = 734.5
        let store = Store(initialState: state) {
            Reduce<ChapterReaderFeature.State, ChapterReaderFeature.Action> { _, _ in .none }
        }
        let host = UIHostingController(rootView: LegacyReaderScrollPage(
            store: store, reference: reference, continuous: false, followsAudio: .constant(false)
        ))
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 390, height: 844))
        window.rootViewController = host
        window.makeKeyAndVisible()
        defer { window.isHidden = true; window.rootViewController = nil }

        // Allow SwiftUI's lazy layout and the UIKit bridge to attach.
        var restored = false
        for _ in 0..<100 {
            host.view.layoutIfNeeded()
            if let scroll = findScrollView(in: host.view),
               abs(scroll.contentOffset.y + scroll.adjustedContentInset.top - 734.5) < 1 {
                restored = true
                break
            }
            try await Task.sleep(for: .milliseconds(20))
        }
        #expect(restored)
    }

    @Test func visibilityUsesViewportIntersectionInsteadOfLazyViewCreation() {
        let viewport = CGRect(x: 0, y: 0, width: 320, height: 600)
        #expect(!LegacyReaderScrollPage.isVisible(CGRect(x: 0, y: 650, width: 320, height: 100), in: viewport, threshold: 0.5))
        #expect(LegacyReaderScrollPage.isVisible(CGRect(x: 0, y: -50, width: 320, height: 100), in: viewport, threshold: 0.5))
        #expect(!LegacyReaderScrollPage.isVisible(CGRect(x: 0, y: -51, width: 320, height: 100), in: viewport, threshold: 0.5))
        #expect(!LegacyReaderScrollPage.isVisible(CGRect(x: 0, y: 0, width: 0, height: 0), in: viewport, threshold: 0.5))
    }

    @Test func chapterHeaderRequiresEightyPercentVisibility() {
        let viewport = CGRect(x: 0, y: 0, width: 320, height: 600)
        #expect(LegacyReaderScrollPage.isVisible(CGRect(x: 0, y: 520, width: 320, height: 100), in: viewport, threshold: 0.8))
        #expect(!LegacyReaderScrollPage.isVisible(CGRect(x: 0, y: 521, width: 320, height: 100), in: viewport, threshold: 0.8))
    }

    @Test func restoresOffsetIncludingSafeAreaInsets() {
        let scroll = makeScrollView()
        let position = LegacyReaderScrollPosition()
        position.scrollView = scroll
        position.scrollTo(y: 734.5)
        position.applyPendingScroll()
        // UIScrollView aligns offsets to physical pixels.
        #expect(abs(scroll.contentOffset.y - 690.5) <= 1 / scroll.traitCollection.displayScale)
        #expect(abs((position.currentOffset ?? -1) - 734.5) <= 1 / scroll.traitCollection.displayScale)
    }

    @Test func clampsRestorationAfterContentSizeChanges() {
        let scroll = makeScrollView()
        let position = LegacyReaderScrollPosition()
        position.scrollView = scroll
        position.scrollTo(y: 10_000)
        position.applyPendingScroll()
        #expect(scroll.contentOffset.y == 1_434)
        position.scrollTo(y: -100)
        position.applyPendingScroll()
        #expect(scroll.contentOffset.y == -44)
        scroll.contentSize.height = 100
        position.scrollTo(y: 734.5)
        position.applyPendingScroll()
        #expect(scroll.contentOffset.y == -44)
    }

    @Test func queuesRestorationUntilAttachedAndVerseNavigationCancelsIt() {
        let position = LegacyReaderScrollPosition()
        position.scrollTo(y: 734.5)
        position.applyPendingScroll()
        let scroll = makeScrollView()
        position.scrollView = scroll
        position.applyPendingScroll()
        #expect(abs((position.currentOffset ?? -1) - 734.5) <= 1 / scroll.traitCollection.displayScale)
        position.scrollTo(y: 100)
        position.cancelPendingScroll()
        position.applyPendingScroll()
        #expect(abs((position.currentOffset ?? -1) - 734.5) <= 1 / scroll.traitCollection.displayScale)
    }

    private func makeScrollView() -> UIScrollView {
        let scroll = UIScrollView(frame: CGRect(x: 0, y: 0, width: 320, height: 600))
        scroll.contentInsetAdjustmentBehavior = .never
        scroll.contentInset = UIEdgeInsets(top: 44, left: 0, bottom: 34, right: 0)
        scroll.contentSize = CGSize(width: 320, height: 2_000)
        return scroll
    }

    private func findScrollView(in view: UIView) -> UIScrollView? {
        if let scroll = view as? UIScrollView { return scroll }
        return view.subviews.lazy.compactMap { findScrollView(in: $0) }.first
    }
}
