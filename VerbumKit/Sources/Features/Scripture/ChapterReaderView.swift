import ComposableArchitecture
import DesignSystem
import Models
import SwiftUI

/// The reading page. Paper, warm ink, New York; verse numerals hang in the
/// margin like a printed Bible. Navigation stays visible so scrolling cannot
/// feed safe-area changes back into navigation-bar visibility.
struct ChapterReaderView: View {
    let store: StoreOf<ChapterReaderFeature>
    let onTitleTapped: () -> Void
    let onSettingsTapped: () -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @ScaledMetric(relativeTo: .body) private var basePointSize = Typography.scriptureBasePointSize

    @State private var pageDirection: Edge = .trailing

    private var pointSize: CGFloat { basePointSize * store.textScale.factor }

    var body: some View {
        ZStack {
            Palette.paper.ignoresSafeArea()

            switch store.content {
            case .idle, .loading:
                ProgressView()
                    .tint(Palette.inkTertiary)
                    .accessibilityLabel(L10n.t("Loading \(store.title)"))

            case .failed(let error):
                unavailable(error)

            case .loaded(let verses):
                page(verses)
                    .id(store.reference)
                    .transition(.asymmetric(
                        insertion: .move(edge: pageDirection).combined(with: .opacity),
                        removal: .opacity
                    ))
            }
        }
        .animation(Motion.resolved(Motion.spatial, reduceMotion: reduceMotion), value: store.reference)
        .onChange(of: store.reference) { old, new in
            pageDirection = isForward(from: old, to: new) ? .trailing : .leading
        }
        .navigationTitle(store.title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(.hidden, for: .navigationBar)
        // Do not toggle bar visibility from scroll geometry. Hiding the bars
        // changes the insets/offset being observed and can continuously restart
        // navigation layout and animations (observed on the iOS 27 simulator).
        .toolbar(.visible, for: .navigationBar)
        .toolbar { toolbar }
        .safeAreaInset(edge: .bottom) {
            if let citation = store.selectionCitation {
                SelectionBar(
                    citation: citation,
                    onCopy: { store.send(.copySelectionTapped) },
                    onClear: { store.send(.clearSelectionTapped) }
                )
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .animation(Motion.resolved(Motion.standard, reduceMotion: reduceMotion), value: store.selectionCitation)
        .sensoryFeedback(.selection, trigger: store.selectedVerses)
        .task { await store.send(.task).finish() }
    }

    // MARK: Page

    private func page(_ verses: [BiblePassage]) -> some View {
        ScrollViewReader { proxy in
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                ChapterOpener(
                    bookName: store.book?.localizedName ?? store.reference.bookId,
                    chapter: store.reference.chapter
                )
                .padding(.top, Spacing.xxxl)
                .padding(.bottom, Spacing.xxl)

                if let requested = store.requestedVerses, !verses.contains(where: { requested.contains($0.verseStart) }) {
                    Text(L10n.t("Verse not found in this chapter."))
                        .font(Typography.subheadline).foregroundStyle(Palette.inkSecondary)
                }

                Button { store.send(.contextTapped) } label: {
                    Label(L10n.t("Chapter context"), systemImage: "text.book.closed")
                        .font(Typography.subheadline)
                        .padding(.vertical, Spacing.md)
                }
                .tint(Palette.accent)
                .padding(.bottom, Spacing.lg)

                VStack(alignment: .leading, spacing: Spacing.xs) {
                    ForEach(verses) { verse in
                        VerseRow(
                            number: verse.verseStart,
                            text: verse.text,
                            pointSize: pointSize,
                            isSelected: store.selectedVerses.contains(verse.verseStart),
                            reduceMotion: reduceMotion
                        ) {
                            store.send(.verseTapped(verse.verseStart))
                        }
                        .id(verse.verseStart)
                    }
                }

                Button { store.send(.contextTapped) } label: {
                    Label(L10n.t("Explore chapter context"), systemImage: "text.book.closed")
                        .font(Typography.subheadline)
                        .padding(.vertical, Spacing.md)
                }
                .tint(Palette.accent)
                .padding(.top, Spacing.xxl)

                ChapterFoot(
                    next: ChapterNavigation.next(after: store.reference),
                    previous: ChapterNavigation.previous(before: store.reference),
                    onNext: { store.send(.nextChapterTapped) },
                    onPrevious: { store.send(.previousChapterTapped) }
                )
                .padding(.top, Spacing.xxxl)
            }
            .frame(maxWidth: Spacing.readingMaxWidth, alignment: .leading)
            .frame(maxWidth: .infinity)
            .padding(.horizontal, Spacing.readingMargin)
            .padding(.bottom, Spacing.xxxl * 2)
        }
        .scrollIndicators(.hidden)
        .contentShape(Rectangle())
        .simultaneousGesture(
            DragGesture(minimumDistance: 40)
                .onEnded { value in
                    // Horizontal page turn; vertical scrolling keeps the scroll view.
                    guard abs(value.translation.width) > abs(value.translation.height) * 1.5 else { return }
                    if value.translation.width < 0, store.canGoToNextChapter {
                        store.send(.nextChapterTapped)
                    } else if value.translation.width > 0, store.canGoToPreviousChapter {
                        store.send(.previousChapterTapped)
                    }
                }
        )
        .accessibilityAction(named: L10n.t("Next chapter")) { store.send(.nextChapterTapped) }
        .accessibilityAction(named: L10n.t("Previous chapter")) { store.send(.previousChapterTapped) }
        .task(id: store.requestedVerses) {
            if let requested = store.requestedVerses,
               let verse = verses.first(where: { requested.contains($0.verseStart) }) {
                proxy.scrollTo(verse.verseStart, anchor: .top)
            }
        }
        }
    }

    private func unavailable(_ error: ReaderError) -> some View {
        VStack(spacing: Spacing.md) {
            Image(systemName: "book.closed")
                .font(.system(size: 34, weight: .light))
                .foregroundStyle(Palette.inkTertiary)
            Text(error.title)
                .font(Typography.editorialHeadline)
                .foregroundStyle(Palette.ink)
                .multilineTextAlignment(.center)
            Text(error.message)
                .font(Typography.subheadline)
                .foregroundStyle(Palette.inkSecondary)
                .multilineTextAlignment(.center)
            Button(L10n.t("Try Again")) { store.send(.retryTapped) }
                .buttonStyle(.borderedProminent)
                .padding(.top, Spacing.sm)
        }
        .padding(Spacing.xxl)
        .frame(maxWidth: Spacing.readingMaxWidth)
    }

    // MARK: Chrome

    @ToolbarContentBuilder
    private var toolbar: some ToolbarContent {
        ToolbarItem(placement: .principal) {
            Button(action: onTitleTapped) {
                HStack(spacing: Spacing.xs) {
                    Text(store.title).font(Typography.navigationSerif)
                    Image(systemName: "chevron.down")
                        .font(Typography.caption.weight(.semibold))
                        .foregroundStyle(Palette.inkTertiary)
                }
            }
            .tint(Palette.ink)
            .accessibilityLabel(L10n.t("\(store.title). Choose book and chapter"))
        }

        ToolbarItemGroup(placement: .topBarTrailing) {
            Button { store.send(.listenTapped) } label: {
                Label(L10n.t("Listen"), systemImage: "headphones")
            }
            Button { store.send(.talkTapped) } label: {
                Label(L10n.t("Talk about this chapter"), systemImage: "waveform.and.mic")
            }
            Button(action: onSettingsTapped) {
                Label(L10n.t("Text Size"), systemImage: "textformat.size")
            }
        }
    }

    private func isForward(from old: PassageReference, to new: PassageReference) -> Bool {
        let o = BibleBook.book(id: old.bookId)?.order ?? 0
        let n = BibleBook.book(id: new.bookId)?.order ?? 0
        return n == o ? new.chapter >= old.chapter : n > o
    }
}

// MARK: - Visual components (no state of their own)

/// `1 SAMUEL` / `17` / a short bronze rule — centred like a book's chapter page.
private struct ChapterOpener: View {
    let bookName: String
    let chapter: Int

    var body: some View {
        VStack(spacing: Spacing.sm) {
            Text(bookName).overline(color: Palette.accent)
            Text(verbatim: String(chapter))
                .font(Typography.chapterNumeral)
                .foregroundStyle(Palette.ink)
            Rectangle()
                .fill(Palette.accent)
                .frame(width: 28, height: 1)
                .padding(.top, Spacing.xs)
        }
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(L10n.t("\(bookName), chapter \(chapter)"))
        .accessibilityAddTraits(.isHeader)
    }
}

/// One verse: numeral hanging in the margin, text flush. Poetry keeps its
/// line breaks. Selection is a bronze wash, never a grey box.
private struct VerseRow: View {
    let number: Int
    let text: String
    let pointSize: CGFloat
    let isSelected: Bool
    let reduceMotion: Bool
    let onTap: () -> Void

    private var gutter: CGFloat { max(28, pointSize * 1.7) }

    var body: some View {
        Button(action: onTap) {
            HStack(alignment: .firstTextBaseline, spacing: Spacing.sm) {
                Text(verbatim: String(number))
                    .font(Typography.verseNumeral(for: pointSize))
                    .foregroundStyle(isSelected ? Palette.accent : Palette.inkTertiary)
                    .frame(width: gutter, alignment: .trailing)
                Text(text)
                    .font(Typography.scripture(pointSize: pointSize))
                    .foregroundStyle(Palette.ink)
                    .lineSpacing(pointSize * Typography.scriptureLineSpacingRatio)
                    .multilineTextAlignment(.leading)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.vertical, Spacing.sm)
            .padding(.trailing, Spacing.md)
            .padding(.leading, Spacing.xs)
            .background(
                isSelected ? Palette.selectionWash : Color.clear,
                in: .rect(cornerRadius: Radius.md)
            )
            .padding(.leading, -gutter - Spacing.sm - Spacing.xs)
            .padding(.leading, gutter + Spacing.sm + Spacing.xs)
        }
        .buttonStyle(.plain)
        .animation(Motion.resolved(Motion.quick, reduceMotion: reduceMotion), value: isSelected)
        .accessibilityLabel(L10n.t("Verse \(number)"))
        .accessibilityValue(text)
        .accessibilityAddTraits(isSelected ? [.isSelected] : [])
        .accessibilityHint(isSelected ? L10n.t("Deselects this verse") : L10n.t("Selects this verse"))
    }
}

/// End-of-chapter mark and the way onward. Swiping does the same; this is
/// the visible, accessible version.
private struct ChapterFoot: View {
    let next: PassageReference?
    let previous: PassageReference?
    let onNext: () -> Void
    let onPrevious: () -> Void

    var body: some View {
        VStack(spacing: Spacing.xl) {
            Rectangle()
                .fill(Palette.accent)
                .frame(width: 28, height: 1)

            if let next {
                Button(action: onNext) {
                    VStack(spacing: Spacing.xs) {
                        Text(L10n.t("Continue")).overline()
                        HStack(spacing: Spacing.xs) {
                            Text(next.formatted)
                                .font(Typography.editorialHeadline)
                                .foregroundStyle(Palette.ink)
                            Image(systemName: "arrow.right")
                                .font(Typography.subheadline.weight(.semibold))
                                .foregroundStyle(Palette.accent)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, Spacing.lg)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(L10n.t("Continue to \(next.formatted)"))
            } else {
                Text(L10n.t("End of the book")).overline()
            }

            if let previous {
                Button(action: onPrevious) {
                    Label(previous.formatted, systemImage: "arrow.left")
                        .font(Typography.footnote)
                        .foregroundStyle(Palette.inkTertiary)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(L10n.t("Back to \(previous.formatted)"))
            }
        }
        .frame(maxWidth: .infinity)
    }
}

private struct SelectionBar: View {
    let citation: String
    let onCopy: () -> Void
    let onClear: () -> Void

    var body: some View {
        HStack(spacing: Spacing.md) {
            Text(citation)
                .font(Typography.navigationSerif)
                .foregroundStyle(Palette.ink)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
            Spacer(minLength: Spacing.sm)
            Button(action: onCopy) {
                Label(L10n.t("Copy"), systemImage: "doc.on.doc")
            }
            .labelStyle(.iconOnly)
            Button(action: onClear) {
                Label(L10n.t("Clear Selection"), systemImage: "xmark")
            }
            .labelStyle(.iconOnly)
        }
        .tint(Palette.accent)
        .padding(.vertical, Spacing.md)
        .padding(.horizontal, Spacing.lg)
        .glassEffect(.regular.interactive(), in: .capsule)
        .padding(.horizontal, Spacing.screenMargin)
        .padding(.bottom, Spacing.sm)
        .accessibilityElement(children: .contain)
        .accessibilityLabel(L10n.t("Selected \(citation)"))
    }
}
