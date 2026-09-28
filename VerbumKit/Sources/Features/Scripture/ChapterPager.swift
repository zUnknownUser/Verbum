import Models
import SwiftUI
import UIKit

/// UIKit owns the interactive transition. Only the visible chapter and its two
/// neighbours are hosted; committing a swipe doesn't rebuild a 1,189-page TabView.
struct ChapterPager<Page: View>: UIViewControllerRepresentable {
    var reference: PassageReference
    var reduceMotion: Bool
    var onSelect: (PassageReference) -> Void
    @ViewBuilder var page: (PassageReference) -> Page

    func makeCoordinator() -> Coordinator { Coordinator(self) }
    func makeUIViewController(context: Context) -> UIPageViewController {
        let controller = UIPageViewController(transitionStyle: .scroll, navigationOrientation: .horizontal)
        controller.dataSource = context.coordinator
        controller.delegate = context.coordinator
        controller.view.backgroundColor = .clear
        context.coordinator.update(self, controller: controller)
        return controller
    }
    func updateUIViewController(_ controller: UIPageViewController, context: Context) {
        context.coordinator.update(self, controller: controller)
    }
    @MainActor final class Coordinator: NSObject, UIPageViewControllerDataSource, UIPageViewControllerDelegate {
        private var parent: ChapterPager
        private var pages: [Int: UIHostingController<Page>] = [:]
        private var transitioning = false
        private var interactiveOrigin: PassageReference?
        init(_ parent: ChapterPager) { self.parent = parent }
        private func index(of controller: UIViewController) -> Int? {
            pages.first { $0.value === controller }?.key
        }
        private func host(_ index: Int) -> UIHostingController<Page>? {
            guard ReaderCanon.chapters.indices.contains(index) else { return nil }
            if let cached = pages[index] { return cached }
            let view = UIHostingController(rootView: parent.page(ReaderCanon.chapters[index]))
            view.view.backgroundColor = .clear
            pages[index] = view
            return view
        }
        func update(_ parent: ChapterPager, controller: UIPageViewController) {
            self.parent = parent
            for (index, host) in pages { host.rootView = parent.page(ReaderCanon.chapters[index]) }
            guard !transitioning else { return }
            let target = ReaderCanon.index(parent.reference)
            let visible = controller.viewControllers?.first.flatMap(index(of:))
            guard target != visible, let host = host(target) else { return }
            transitioning = true
            controller.setViewControllers(
                [host], direction: target > (visible ?? target) ? .forward : .reverse,
                animated: visible != nil && !parent.reduceMotion && abs(target - (visible ?? target)) == 1
            ) { [weak self, weak controller] _ in
                guard let self, let controller else { return }
                self.transitioning = false
                self.trim(around: target)
                // A book-picker jump arriving during animation is applied after it settles.
                if ReaderCanon.index(self.parent.reference) != target {
                    self.update(self.parent, controller: controller)
                }
            }
        }
        private func trim(around index: Int) {
            pages = pages.filter { abs($0.key - index) <= 1 }
        }
        func pageViewController(
            _ pageViewController: UIPageViewController, viewControllerBefore viewController: UIViewController
        ) -> UIViewController? {
            index(of: viewController).flatMap { host($0 - 1) }
        }
        func pageViewController(
            _ pageViewController: UIPageViewController, viewControllerAfter viewController: UIViewController
        ) -> UIViewController? {
            index(of: viewController).flatMap { host($0 + 1) }
        }
        func pageViewController(
            _ pageViewController: UIPageViewController, willTransitionTo pendingViewControllers: [UIViewController]
        ) {
            interactiveOrigin = parent.reference
            transitioning = true
        }
        func pageViewController(
            _ pageViewController: UIPageViewController, didFinishAnimating finished: Bool,
            previousViewControllers: [UIViewController], transitionCompleted completed: Bool
        ) {
            transitioning = false
            let externalNavigation = interactiveOrigin.map { $0 != parent.reference } ?? false
            interactiveOrigin = nil
            if externalNavigation {
                update(parent, controller: pageViewController)
                return
            }
            guard let visible = pageViewController.viewControllers?.first, let index = index(of: visible) else {
                return
            }
            trim(around: index)
            if completed { parent.onSelect(ReaderCanon.chapters[index]) }
        }
    }
}
