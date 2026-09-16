import ComposableArchitecture
import DesignSystem
import Models
import SwiftUI

/// Native page physics and a lazy continuous flow share the same chapter cache and study sheet.
struct ChapterReaderView: View {
    @Bindable var store: StoreOf<ChapterReaderFeature>
    let onTitleTapped: () -> Void
    let onSettingsTapped: () -> Void
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ZStack(alignment: .topTrailing) {
            Palette.paper.ignoresSafeArea()
            if store.readingMode == .pages {
                TabView(selection: Binding(get: { ReaderCanon.index(store.reference) }, set: { index in
                    guard ReaderCanon.chapters.indices.contains(index), index != ReaderCanon.index(store.reference) else { return }
                    store.send(.go(to: ReaderCanon.chapters[index]))
                })) {
                    ForEach(ReaderCanon.chapters.indices, id: \.self) { index in
                        Group {
                            if abs(index - ReaderCanon.index(store.reference)) <= 1 {
                                ReaderScrollPage(store: store, reference: ReaderCanon.chapters[index], continuous: false)
                            } else { Palette.paper }
                        }.tag(index)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
                .animation(reduceMotion ? nil : .snappy(duration: 0.3), value: store.reference)
            } else {
                ReaderScrollPage(store: store, reference: store.flow.first ?? store.reference, continuous: true)
            }
            if store.focusMode {
                Button { store.send(.focusToggled) } label: {
                    Image(systemName: "eye").padding(12)
                }
                .accessibilityLabel(L10n.t("Show reading controls"))
                .tint(Palette.inkSecondary)
                .glassEffect(.regular.interactive(), in: .circle)
                .padding(Spacing.md)
            }
        }
        .onChange(of: store.readingMode) { _, _ in store.send(.readingModeChanged) }
        .navigationTitle(store.title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(.hidden, for: .navigationBar)
        .toolbar(store.focusMode ? .hidden : .visible, for: .navigationBar)
        .toolbar(store.focusMode ? .hidden : .visible, for: .tabBar)
        .toolbar {
            ToolbarItem(placement: .principal) {
                Button(action: onTitleTapped) {
                    HStack(spacing: Spacing.xs) {
                        Text(store.title).font(Typography.navigationSerif)
                        Image(systemName: "chevron.down").font(.caption)
                    }
                }.tint(Palette.ink)
            }
            ToolbarItemGroup(placement: .topBarTrailing) {
                Button { store.send(.focusToggled) } label: { Label(L10n.t("Quiet reading"), systemImage: "eye.slash") }
                Menu {
                    Button { store.send(.listenTapped) } label: { Label(L10n.t("Listen"), systemImage: "headphones") }
                    Button { store.send(.talkTapped) } label: { Label(L10n.t("Talk about this chapter"), systemImage: "waveform.and.mic") }
                    Button(action: onSettingsTapped) { Label(L10n.t("Reading settings"), systemImage: "textformat.size") }
                } label: { Image(systemName: "ellipsis") }
                .accessibilityLabel(L10n.t("Reading settings"))
            }
        }
        .safeAreaInset(edge: .bottom) {
            if let previous = store.history.last {
                Button { store.send(.backToReading) } label: {
                    Label(L10n.t("Back to \(previous.reference.formatted)"), systemImage: "arrow.uturn.backward")
                        .font(Typography.footnote).padding(.horizontal, Spacing.lg).padding(.vertical, Spacing.md)
                }.tint(Palette.ink).glassEffect(.regular.interactive(), in: .capsule)
                    .padding(.bottom, Spacing.sm)
            }
        }
        .overlay(alignment: .leading) {
            if !store.history.isEmpty {
                Color.clear.frame(width: 22).contentShape(Rectangle()).gesture(
                    DragGesture(minimumDistance: 30).onEnded { value in
                        if value.translation.width > 70, abs(value.translation.width) > abs(value.translation.height)*2 { store.send(.backToReading) }
                    }
                )
            }
        }
        .sheet(item: $store.scope(state: \.study, action: \.study)) { study in
            VerseStudyView(store: study)
                .presentationDetents([.height(360), .large])
                .presentationDragIndicator(.visible)
                .presentationBackground(.regularMaterial)
        }
        .task { await store.send(.task).finish() }
    }
}

private struct ReaderScrollPage: View {
    let store: StoreOf<ChapterReaderFeature>
    let reference: PassageReference
    let continuous: Bool
    @State private var position = ScrollPosition()
    @State private var offset = 0.0
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    private var key: String { continuous ? "flow" : ReaderCanon.key(reference) }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                ForEach(continuous ? store.flow : [reference], id: \.self) { chapter in
                    ReaderChapterBody(store: store, reference: chapter, continuous: continuous)
                }
                if continuous, let last = store.flow.last, store.chapters[ReaderCanon.key(last)] != nil,
                   let next = ChapterNavigation.next(after: last) {
                    VStack(spacing: Spacing.sm) {
                        Text(next.formatted).font(Typography.editorialHeadline)
                        Label(L10n.t("Continue reading"), systemImage: "arrow.down").font(Typography.footnote)
                    }.foregroundStyle(Palette.inkTertiary).frame(maxWidth: .infinity).padding(Spacing.xxl)
                        .onAppear { store.send(.appendChapter) }
                }
            }
            .scrollTargetLayout()
            .frame(maxWidth: Spacing.readingMaxWidth)
            .frame(maxWidth: .infinity)
            .padding(.horizontal, Spacing.readingMargin)
            .padding(.bottom, Spacing.xxxl)
        }
        .scrollPosition($position)
        .scrollIndicators(.hidden)
        .onScrollGeometryChange(for: Double.self, of: { Double($0.contentOffset.y + $0.contentInsets.top) }) { _, value in offset = value }
        .onScrollPhaseChange { _, phase in
            if phase == .idle { store.send(.scrollOffsetChanged(key, offset)) }
        }
        .onDisappear { store.send(.scrollOffsetChanged(key, offset)) }
        .task { store.send(.ensureChapter(reference)) }
        .task(id: store.navigationRevision) { restore() }
        .onChange(of: store.chapters[ReaderCanon.key(reference)]?.count) { _, _ in restore() }
        .accessibilityAction(named: L10n.t("Next chapter")) { store.send(.nextChapterTapped) }
        .accessibilityAction(named: L10n.t("Previous chapter")) { store.send(.previousChapterTapped) }
    }
    private func restore() {
        guard continuous || reference == store.reference else { return }
        if let value = store.restoreOffset ?? store.scrollOffsets[key], store.requestedVerses == nil {
            position.scrollTo(y: value)
        } else if let verse = store.requestedVerses?.lowerBound {
            position.scrollTo(id: "\(store.reference.bookId).\(store.reference.chapter).\(verse)", anchor: .top)
        } else { position.scrollTo(y: 0) }
    }
}

private struct ReaderChapterBody: View {
    let store: StoreOf<ChapterReaderFeature>
    let reference: PassageReference
    let continuous: Bool
    @ScaledMetric(relativeTo: .body) private var basePointSize = Typography.scriptureBasePointSize
    private var pointSize: CGFloat { basePointSize * store.textScale.factor }
    private var key: String { ReaderCanon.key(reference) }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            VStack(spacing: Spacing.sm) {
                Text(BibleBook.book(id: reference.bookId)?.localizedName ?? reference.bookId).overline(color: Palette.accent)
                Text(verbatim: String(reference.chapter)).font(Typography.chapterNumeral).foregroundStyle(Palette.ink)
                Rectangle().fill(Palette.accent).frame(width: 28, height: 1).padding(.top, Spacing.xs)
            }.frame(maxWidth: .infinity).padding(.top, Spacing.xxxl).padding(.bottom, Spacing.xxl)
                .accessibilityElement(children: .combine).accessibilityAddTraits(.isHeader)
                .onScrollVisibilityChange(threshold: 0.8) { visible in
                    if visible && continuous { store.send(.chapterVisible(reference)) }
                }
            if let verses = store.chapters[key] {
                ForEach(verses) { verse in
                    let id = "\(verse.bookId).\(verse.chapter).\(verse.verseStart)"
                    StudyVerseRow(verse: verse, pointSize: pointSize, annotation: store.annotations[id],
                        segments: store.mentions[id] ?? [],
                        requested: reference == store.reference && (store.requestedVerses?.contains(verse.verseStart) ?? false)) { ids in
                            store.send(.studyVerse(reference, verse.verseStart, ids))
                        }.id(id)
                }
                if !continuous {
                    if let next = ChapterNavigation.next(after: reference) {
                        Button { store.send(.nextChapterTapped) } label: {
                            VStack(spacing: Spacing.sm) {
                                Text(next.formatted).font(Typography.editorialHeadline)
                                Label(L10n.t("Swipe to continue"), systemImage: "arrow.right").font(Typography.footnote)
                            }.frame(maxWidth: .infinity).padding(.vertical, Spacing.xxxl)
                        }.buttonStyle(.plain).foregroundStyle(Palette.inkTertiary)
                    }
                }
            } else if let error = store.chapterErrors[key] {
                VStack(spacing: Spacing.md) {
                    Text(error.title).font(Typography.editorialHeadline)
                    Text(error.message).font(Typography.subheadline)
                    Button(L10n.t("Try Again")) { store.send(.ensureChapter(reference)) }
                }.frame(maxWidth: .infinity).padding(.vertical, Spacing.xxxl)
            } else {
                ProgressView().frame(maxWidth: .infinity, minHeight: 220)
                    .accessibilityLabel(L10n.t("Loading \(reference.formatted)"))
            }
        }
        .task { store.send(.ensureChapter(reference)) }
    }
}

private struct StudyVerseRow: View {
    let verse: BiblePassage
    let pointSize: CGFloat
    let annotation: ReaderAnnotation?
    let segments: [StudyTextSegment]
    let requested: Bool
    let onTap: ([String]) -> Void
    private var linkedText: AttributedString {
        if segments.isEmpty {
            var text = AttributedString(verse.text)
            text.link = URL(string: "verbum-study://verse")
            text.foregroundColor = Palette.ink
            return text
        }
        var result = AttributedString()
        for (index, segment) in segments.enumerated() {
            var part = AttributedString(segment.text)
            part.link = URL(string: "verbum-study://verse")
            part.foregroundColor = Palette.ink
            if !segment.entityIDs.isEmpty {
                part.link = URL(string: "verbum-study://mention/\(index)")
                part.foregroundColor = Palette.ink
                part.underlineStyle = Text.LineStyle(pattern: .dot, color: Palette.accent.opacity(0.5))
            }
            result += part
        }
        return result
    }
    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: Spacing.sm) {
            Button { onTap([]) } label: {
                VStack(spacing: 2) {
                    Text(verbatim: String(verse.verseStart)).font(Typography.verseNumeral(for: pointSize))
                    if !(annotation?.note.isEmpty ?? true) { Image(systemName: "pencil.line").font(.system(size: 8)) }
                }.frame(width: max(28, pointSize * 1.7), alignment: .trailing)
            }.buttonStyle(.plain).foregroundStyle(Palette.inkTertiary)
                .accessibilityLabel(L10n.t("Study verse \(verse.verseStart)"))
            Text(linkedText)
                .font(Typography.scripture(pointSize: pointSize)).foregroundStyle(Palette.ink)
                .lineSpacing(pointSize * Typography.scriptureLineSpacingRatio)
                .frame(maxWidth: .infinity, alignment: .leading)
                .environment(\.openURL, OpenURLAction { url in
                    guard url.scheme == "verbum-study" else { return .discarded }
                    if url.host == "verse" { onTap([]); return .handled }
                    guard let index = Int(url.lastPathComponent), segments.indices.contains(index) else { return .discarded }
                    onTap(segments[index].entityIDs); return .handled
                })

        }
        .padding(.vertical, Spacing.sm).padding(.horizontal, Spacing.xs)
        .background(highlight, in: .rect(cornerRadius: Radius.sm))
        .accessibilityElement(children: .contain)
    }
    private var highlight: Color {
        switch annotation?.highlight {
        case .gold: Color.yellow.opacity(0.19)
        case .sage: Color.green.opacity(0.12)
        case .rose: Color.pink.opacity(0.13)
        case nil: requested ? Palette.selectionWash : .clear
        }
    }
}
