import ComposableArchitecture
import DesignSystem
import Models
import SwiftUI

/// The canon as a bookshelf: one shelf per division, one block per book, the
/// name set upright in strong serif with its chapter count beside it. Tap a
/// block and its chapters unfold beneath the shelf; tap again to close. No
/// screens are pushed.
struct BookPickerView: View {
    @Bindable var store: StoreOf<BookPickerFeature>
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @FocusState private var searchFocused: Bool

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                HStack {
                Text(L10n.t("Books"))
                    .font(Typography.editorialTitle)
                    .foregroundStyle(Palette.ink)
                    .padding(.horizontal, Spacing.readingMargin)
                    .padding(.top, Spacing.xl)
                    .padding(.bottom, Spacing.lg)
                    .accessibilityAddTraits(.isHeader)
                    Spacer()
                    Button { store.send(.toggleSearch) } label: {
                        Image(systemName: store.searchVisible ? "xmark" : "magnifyingglass")
                            .padding(Spacing.md)
                    }
                    .foregroundStyle(Palette.inkSecondary)
                    .accessibilityLabel(L10n.t(store.searchVisible ? "Close search" : "Search books"))
                    .padding(.trailing, Spacing.readingMargin)
                }

                if store.searchVisible {
                    TextField(L10n.t("Book, chapter or verse"), text: Binding(get: { store.query }, set: { store.send(.queryChanged($0)) }))
                        .textFieldStyle(.roundedBorder)
                        .autocorrectionDisabled()
                        .submitLabel(.search)
                        .focused($searchFocused)
                        .onSubmit { searchFocused = false; store.send(.searchSubmitted) }
                        .padding(.horizontal, Spacing.readingMargin)
                        .padding(.bottom, Spacing.lg)
                        .onAppear { searchFocused = true }
                }
                if !store.query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    VStack(alignment: .leading, spacing: Spacing.md) {
                        if let reference = store.matchingReference {
                            Button { searchFocused = false; store.send(.searchSubmitted) } label: {
                                Label(reference.formatted, systemImage: "book").font(Typography.editorialHeadline)
                                    .padding(.vertical, Spacing.md)
                            }
                        }
                        ForEach(store.matchingBooks) { book in
                            Button { searchFocused = false; store.send(.bookTapped(book)) } label: {
                                HStack {
                                    Text(book.localizedName).font(Typography.editorialHeadline)
                                    Spacer()
                                    Image(systemName: "chevron.right")
                                }.padding(.vertical, Spacing.md)
                            }
                        }
                        if store.matchingBooks.isEmpty && store.matchingReference == nil {
                            Text(L10n.t("No matching reference. Try John 3:16."))
                                .font(Typography.subheadline).foregroundStyle(Palette.inkSecondary)
                        }
                    }.padding(.horizontal, Spacing.readingMargin)
                } else {
                testament(.old)
                testament(.new)
                }
                }
                .padding(.bottom, Spacing.xxxl * 2)
            }
            .scrollIndicators(.hidden)
            .scrollDismissesKeyboard(.interactively)
            .background(Palette.paper)
            .onChange(of: store.searchVisible) { _, visible in
                if !visible, let book = store.selectedBook { proxy.scrollTo(book.division, anchor: .top) }
            }
            .onChange(of: store.selectedBook, initial: true) { _, book in
                guard let book else { return }
                withAnimation(Motion.resolved(Motion.spatial, reduceMotion: reduceMotion)) {
                    proxy.scrollTo(book.division, anchor: .top)
                }
            }
        }
        .navigationTitle(L10n.t("Books"))
        .toolbar(.hidden, for: .navigationBar)
    }

    private func testament(_ testament: Testament) -> some View {
        VStack(alignment: .leading, spacing: Spacing.lg) {
            Text(testament == .old ? L10n.t("Old Testament") : L10n.t("New Testament"))
                .overline()
                .padding(.horizontal, Spacing.readingMargin)
                .padding(.top, testament == .old ? 0 : Spacing.xxl)

            ForEach(Array(BibleBook.Division.allCases.filter { $0.testament == testament }.enumerated()), id: \.element) { index, division in
                Shelf(
                    division: division,
                    tint: Palette.spineTint(index),
                    current: store.current,
                    selectedBook: store.selectedBook,
                    onSpine: { book in
                        store.send(store.selectedBook == book ? .backToBooksTapped : .bookTapped(book))
                    },
                    onChapter: { store.send(.chapterTapped($0)) }
                )
                .id(division)
            }
        }
    }
}

// MARK: - Shelf

private struct Shelf: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let division: BibleBook.Division
    let tint: Color
    let current: PassageReference
    let selectedBook: BibleBook?
    let onSpine: (BibleBook) -> Void
    let onChapter: (Int) -> Void

    private var openBook: BibleBook? {
        guard let selectedBook, selectedBook.division == division else { return nil }
        return selectedBook
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Text(division.localizedTitle)
                .font(Typography.footnote.weight(.medium))
                .foregroundStyle(Palette.inkSecondary)
                .padding(.horizontal, Spacing.readingMargin)

            FlowLayout(horizontalSpacing: Spacing.sm, verticalSpacing: Spacing.sm) {
                ForEach(division.books) { book in
                    BookBlock(
                        book: book,
                        tint: tint,
                        isCurrent: book.id == current.bookId,
                        isOpen: openBook == book
                    ) { onSpine(book) }
                }
            }
            .padding(.horizontal, Spacing.readingMargin)
            .padding(.top, Spacing.xs)
            .padding(.bottom, Spacing.md)

            // The shelf itself.
            Rectangle()
                .fill(Palette.rule)
                .frame(height: 1)
                .padding(.horizontal, Spacing.readingMargin - Spacing.sm)

            if let openBook {
                VStack(alignment: .leading, spacing: Spacing.md) {
                    HStack(alignment: .firstTextBaseline) {
                        Text(openBook.localizedName).overline(color: Palette.accent)
                        Spacer()
                        Text(L10n.t("\(openBook.chapterCount) chapters"))
                            .font(Typography.caption)
                            .foregroundStyle(Palette.inkTertiary)
                    }
                    ChapterGrid(
                        book: openBook,
                        currentChapter: current.bookId == openBook.id ? current.chapter : nil,
                        onChapter: onChapter
                    )
                }
                .padding(.horizontal, Spacing.readingMargin)
                .padding(.top, Spacing.lg)
                .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
        .clipped()
        .animation(Motion.resolved(Motion.spatial, reduceMotion: reduceMotion), value: openBook)
    }
}

/// A block with the book's name set upright and its chapter count beside it.
/// The book being read is bronze; the open one lifts off the shelf.
private struct BookBlock: View {
    let book: BibleBook
    let tint: Color
    let isCurrent: Bool
    let isOpen: Bool
    let onTap: () -> Void

    private var nameColor: Color { isCurrent ? Palette.paper : Palette.ink }
    private var countColor: Color { isCurrent ? Palette.paper.opacity(0.8) : Palette.inkTertiary }
    private var fill: Color { isCurrent ? Palette.accent : tint }
    private var border: Color { isOpen ? Palette.accent : (isCurrent ? Color.clear : Palette.rule) }

    var body: some View {
        Button(action: onTap) {
            label
                .padding(.horizontal, Spacing.md)
                .frame(height: 46)
                .background(fill, in: .rect(cornerRadius: Radius.sm))
                .overlay(RoundedRectangle(cornerRadius: Radius.sm).strokeBorder(border, lineWidth: isOpen ? 1.5 : 1))
                .offset(y: isOpen ? -3 : 0)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(book.localizedName)
        .accessibilityValue(isCurrent ? L10n.t("Currently reading") : L10n.t("\(book.chapterCount) chapters"))
        .accessibilityHint(isOpen ? L10n.t("Closes chapters") : L10n.t("Shows chapters"))
        .accessibilityAddTraits(isOpen ? [.isSelected] : [])
    }

    private var label: some View {
        HStack(alignment: .firstTextBaseline, spacing: Spacing.xs) {
            Text(book.localizedName)
                .font(Typography.spine)
                .foregroundStyle(nameColor)
            Text(verbatim: String(book.chapterCount))
                .font(Typography.caption2.monospacedDigit())
                .foregroundStyle(countColor)
        }
        .lineLimit(1)
    }
}

private struct ChapterGrid: View {
    let book: BibleBook
    let currentChapter: Int?
    let onChapter: (Int) -> Void

    private let columns = [GridItem(.adaptive(minimum: 44), spacing: Spacing.sm)]

    var body: some View {
        LazyVGrid(columns: columns, spacing: Spacing.sm) {
            ForEach(1...book.chapterCount, id: \.self) { chapter in
                let isCurrent = chapter == currentChapter
                Button { onChapter(chapter) } label: {
                    Text(verbatim: String(chapter))
                        .font(.system(.subheadline, design: .serif).monospacedDigit())
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .background(isCurrent ? Palette.accent : Palette.paperElevated, in: .rect(cornerRadius: Radius.sm))
                        .overlay(
                            RoundedRectangle(cornerRadius: Radius.sm)
                                .strokeBorder(isCurrent ? Color.clear : Palette.rule, lineWidth: 1)
                        )
                        .foregroundStyle(isCurrent ? Palette.paper : Palette.ink)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(L10n.t("\(book.localizedName) chapter \(chapter)"))
                .accessibilityAddTraits(isCurrent ? [.isSelected] : [])
            }
        }
    }
}
