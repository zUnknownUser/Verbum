import ComposableArchitecture
import DesignSystem
import Models
import SwiftUI

/// One field, grouped results on paper. A reference opens the reader on
/// return; passages and books open the reader; entities open their page.
public struct SearchView: View {
    @Bindable var store: StoreOf<SearchFeature>

    public init(store: StoreOf<SearchFeature>) {
        self.store = store
    }

    public var body: some View {
        NavigationStack {
            List {
                if store.isOffline {
                    offlineNotice
                }
                if let results = store.results, !results.isEmpty {
                    resultSections(results)
                } else if store.showsNoResults {
                    noResults
                } else if store.query.isEmpty {
                    suggestions
                }
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            .background(Palette.paper)
            .navigationTitle(L10n.t("Search"))
            .toolbarTitleDisplayMode(.inline)
            .searchable(text: $store.query, prompt: Text(L10n.t("Reference, book, person, place, theme")))
            .autocorrectionDisabled()
            .textInputAutocapitalization(.never)
            .onSubmit(of: .search) { store.send(.submitted) }
        }
    }

    // MARK: Sections

    @ViewBuilder
    private func resultSections(_ results: SearchResponse) -> some View {
        if !results.passages.isEmpty {
            section(results.passages.count == 1 ? L10n.t("Passage") : L10n.t("Passages")) {
                ForEach(results.passages, id: \.self) { reference in
                    row(title: reference.formatted, subtitle: L10n.t("Open in the reader"), symbol: "book") {
                        store.send(.passageTapped(reference))
                    }
                }
            }
        }
        if !results.books.isEmpty {
            section(L10n.t("Books")) {
                ForEach(results.books) { book in
                    row(title: book.localizedName, subtitle: L10n.t("\(book.chapterCount) chapters · \(book.division.localizedTitle)"), symbol: "books.vertical") {
                        store.send(.bookTapped(book))
                    }
                }
            }
        }
        entitySection(L10n.t("People"), results.entities(of: .person), symbol: "person")
        entitySection(L10n.t("Places"), results.entities(of: .place), symbol: "mappin.and.ellipse")
        entitySection(L10n.t("Themes"), results.entities(of: .theme), symbol: "sparkle")
        entitySection(L10n.t("Events"), results.entities(of: .event), symbol: "clock")
    }

    @ViewBuilder
    private func entitySection(_ title: String, _ entities: [BibleEntity], symbol: String) -> some View {
        if !entities.isEmpty {
            section(title) {
                ForEach(entities) { entity in
                    row(title: entity.name, subtitle: entity.summary ?? "", symbol: symbol) {
                        store.send(.entityTapped(entity))
                    }
                }
            }
        }
    }

    private func section<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        Section {
            content()
                .listRowBackground(Palette.paper)
                .listRowSeparatorTint(Palette.rule)
                .listRowInsets(EdgeInsets(top: 0, leading: Spacing.readingMargin, bottom: 0, trailing: Spacing.readingMargin))
        } header: {
            Text(title)
                .overline()
                .padding(.top, Spacing.md)
                .padding(.leading, Spacing.readingMargin - Spacing.screenMargin)
        }
        .listSectionSeparator(.hidden)
    }

    private func row(title: String, subtitle: String, symbol: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            resultLabel(title: title, subtitle: subtitle, symbol: symbol, showsChevron: true)
        }
        .buttonStyle(.plain)
    }

    private func resultLabel(title: String, subtitle: String, symbol: String, showsChevron: Bool) -> some View {
        HStack(spacing: Spacing.md) {
            Image(systemName: symbol)
                .font(Typography.subheadline)
                .foregroundStyle(Palette.accent)
                .frame(width: 22)
            VStack(alignment: .leading, spacing: Spacing.xxs) {
                Text(title)
                    .font(.system(.body, design: .serif).weight(.semibold))
                    .foregroundStyle(Palette.ink)
                if !subtitle.isEmpty {
                    Text(subtitle)
                        .font(Typography.footnote)
                        .foregroundStyle(Palette.inkSecondary)
                        .lineLimit(2)
                }
            }
            Spacer()
            if showsChevron {
                Image(systemName: "chevron.right")
                    .font(Typography.caption.weight(.semibold))
                    .foregroundStyle(Palette.inkTertiary)
            }
        }
        .padding(.vertical, Spacing.md)
        .contentShape(Rectangle())
    }

    // MARK: Empty states

    /// §52: the network state is named, never hidden behind thinner results.
    private var offlineNotice: some View {
        Section {
            Label(L10n.t("Verbum can't be reached — showing what this device knows."), systemImage: "wifi.slash")
                .font(Typography.footnote)
                .foregroundStyle(Palette.inkSecondary)
                .padding(.vertical, Spacing.sm)
                .listRowBackground(Palette.paper)
                .listRowSeparator(.hidden)
                .listRowInsets(EdgeInsets(top: 0, leading: Spacing.readingMargin, bottom: 0, trailing: Spacing.readingMargin))
        }
    }

    private var suggestions: some View {
        Section {
            VStack(alignment: .leading, spacing: Spacing.md) {
                Text(L10n.t("Try")).overline()
                ForEach(["John 3:16", "1 Samuel 17", "David", "Jerusalem", "Forgiveness", "why did Job suffer"], id: \.self) { example in
                    Button(example) { store.query = example }
                        .font(.system(.body, design: .serif))
                        .foregroundStyle(Palette.ink)
                }
            }
            .padding(.vertical, Spacing.lg)
            .listRowBackground(Palette.paper)
            .listRowSeparator(.hidden)
            .listRowInsets(EdgeInsets(top: 0, leading: Spacing.readingMargin, bottom: 0, trailing: Spacing.readingMargin))
        }
    }

    private var noResults: some View {
        Section {
            VStack(alignment: .leading, spacing: Spacing.sm) {
                Text(L10n.t("Nothing for “\(store.query)”"))
                    .font(Typography.editorialHeadline)
                    .foregroundStyle(Palette.ink)
                Text(store.isOffline
                     ? L10n.t("Without the content service, search understands references like Jn 3:16 and book names.")
                     : L10n.t("Search understands references like Jn 3:16, book names, people, places, themes — and questions in your own words, which look through the text of Scripture."))
                    .font(Typography.subheadline)
                    .foregroundStyle(Palette.inkSecondary)
            }
            .padding(.vertical, Spacing.lg)
            .listRowBackground(Palette.paper)
            .listRowSeparator(.hidden)
            .listRowInsets(EdgeInsets(top: 0, leading: Spacing.readingMargin, bottom: 0, trailing: Spacing.readingMargin))
        }
    }
}
