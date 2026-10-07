import ComposableArchitecture
import DesignSystem
import Models
import SwiftUI

/// Both tabs use the existing account-scoped annotations and reading activity.
struct ReadingCollectionView: View {
    let store: StoreOf<ReadingCollectionFeature>
    let journey: Bool
    @Environment(\.personalSync) private var sync

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: Spacing.lg) {
                Text(L10n.t(journey ? "Your time in Scripture" : "Keep what matters to you"))
                    .font(Typography.editorialTitle).foregroundStyle(Palette.ink)
                Text(L10n.t(journey ? "Revisit the chapters you have opened and continue at your own pace." : "Your saved passages, highlights and notes, together."))
                    .font(Typography.subheadline).foregroundStyle(Palette.inkSecondary)
                if journey { journeyContent } else { libraryContent }
                Text(sync.message)
                    .font(Typography.caption).foregroundStyle(Palette.inkTertiary)
                    .padding(.top, Spacing.lg)
            }
            .frame(maxWidth: Spacing.readingMaxWidth, alignment: .leading)
            .frame(maxWidth: .infinity)
            .padding(Spacing.readingMargin)
        }
        .scrollIndicators(.hidden)
        .background(Palette.paper)
        .toolbarTitleDisplayMode(.inline)
        .navigationTitle(L10n.t(journey ? "Journey" : "Library"))
        .task(id: sync.revision) { if !journey { await store.send(.task).finish() } }
        .refreshable { if !journey { await store.send(.retry).finish() } }
    }

    @ViewBuilder private var libraryContent: some View {
        HStack(spacing: Spacing.md) {
            Image(systemName: "magnifyingglass").foregroundStyle(Palette.accent).accessibilityHidden(true)
            TextField(L10n.t("Search references or your notes"), text: Binding(get: { store.query }, set: { store.send(.queryChanged($0)) }),
                      prompt: Text(L10n.t("Search references or your notes")).foregroundStyle(Palette.inkSecondary))
                .font(Typography.body).foregroundStyle(Palette.ink)
                .textFieldStyle(.plain).submitLabel(.search)
                .accessibilityIdentifier("library.search")
        }.padding(Spacing.lg).editorialSurface()
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Spacing.sm) {
                ForEach(ReadingCollectionFilter.allCases, id: \.self) { filter in
                    Button { store.send(.filterChanged(filter)) } label: {
                        Text(filterTitle(filter)).font(Typography.footnote)
                            .fixedSize(horizontal: true, vertical: false)
                            .padding(.horizontal, Spacing.lg).frame(minHeight: 44)
                            .foregroundStyle(store.filter == filter ? Palette.onForest : Palette.inkSecondary)
                            .background(store.filter == filter ? Palette.forest : Palette.paperElevated, in: Capsule())
                    }.buttonStyle(EditorialButtonStyle())
                        .accessibilityAddTraits(store.filter == filter ? .isSelected : [])
                }
            }
        }
        if store.loading && store.annotations.isEmpty { ProgressView().frame(maxWidth: .infinity) }
        if store.failed {
            Text(L10n.t("Your collection could not be loaded. Your saved items have not been changed."))
                .font(Typography.footnote).foregroundStyle(Palette.inkSecondary)
            Button(L10n.t("Try Again")) { store.send(.retry) }.tint(Palette.accent)
        } else if !store.loading && store.entries.isEmpty {
            empty(title: store.query.isEmpty && store.filter == .all ? "Your library begins with a passage" : "No matching passages",
                  body: store.query.isEmpty && store.filter == .all ? "Open a verse to save it, highlight it or write a note. It will appear here." : "Try another filter or search for a book, reference or note.")
        }
        ForEach(store.entries) { item in
            Button { store.send(.open(item.reference)) } label: {
                VStack(alignment: .leading, spacing: Spacing.sm) {
                    HStack {
                        Text(item.reference.formatted).font(Typography.navigationSerif)
                        Spacer()
                        Image(systemName: "chevron.right").font(.caption).accessibilityHidden(true)
                    }
                    HStack(spacing: Spacing.md) {
                        if item.bookmarked == true { Label(L10n.t("Saved"), systemImage: "bookmark") }
                        if item.highlight != nil { Label(L10n.t("Highlight"), systemImage: "highlighter") }
                        if !item.note.isEmpty { Label(L10n.t("Note"), systemImage: "note.text") }
                    }.font(Typography.caption).foregroundStyle(Palette.accent)
                    if !item.note.isEmpty { Text(item.note).font(Typography.subheadline).foregroundStyle(Palette.inkSecondary).lineLimit(3) }
                }.padding(Spacing.lg).editorialSurface().contentShape(Rectangle())
            }.buttonStyle(.plain).foregroundStyle(Palette.ink)
        }
    }

    @ViewBuilder private var journeyContent: some View {
        ViewThatFits(in: .horizontal) {
            HStack(spacing: Spacing.xxl) { metrics }
            VStack(alignment: .leading, spacing: Spacing.md) { metrics }
        }.frame(maxWidth: .infinity, alignment: .leading)
            .padding(Spacing.xl).editorialSurface()
        Text(L10n.t("Chapters opened, not completed. Every visit is part of your journey."))
            .font(Typography.caption).foregroundStyle(Palette.inkSecondary)
        if let reference = store.lastRead ?? store.activity.visits.first?.reference {
            Button { store.send(.open(reference)) } label: {
                VStack(alignment: .leading, spacing: Spacing.sm) {
                    Label(L10n.t("Continue reading"), systemImage: "book")
                        .font(Typography.footnote).foregroundStyle(Palette.accent)
                    Text(reference.formatted).font(Typography.editorialHeadline).foregroundStyle(Palette.ink)
                }.frame(maxWidth: .infinity, alignment: .leading).padding(Spacing.lg)
                    .background(Palette.accent.opacity(0.07), in: RoundedRectangle(cornerRadius: Radius.md))
            }.buttonStyle(.plain)
        }
        if store.activity.visits.isEmpty {
            empty(title: "Your journey starts here", body: "Open a chapter and return here whenever you want to revisit it.")
        } else {
            Text(L10n.t("Recently opened")).overline(color: Palette.accent)
            ForEach(store.activity.visits.prefix(10)) { visit in
                ReadingVisitRow(visit: visit) { store.send(.open(visit.reference)) }
            }
            Button { store.send(.historyTapped) } label: {
                HStack {
                    Text(L10n.t("View full history"))
                    Spacer()
                    Image(systemName: "arrow.right").accessibilityHidden(true)
                }.font(Typography.subheadline).padding(Spacing.lg).editorialSurface()
            }.buttonStyle(EditorialButtonStyle()).foregroundStyle(Palette.accent)
                .accessibilityIdentifier("journey.history")
        }
    }
    @ViewBuilder private var metrics: some View {
        metric(store.activity.days.count, "Days reading")
        metric(store.activity.visits.count, "Chapters opened")
    }
    private func metric(_ count: Int, _ title: String.LocalizationValue) -> some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text(verbatim: String(count)).font(Typography.editorialTitle).foregroundStyle(Palette.ink)
            Text(L10n.t(title)).font(Typography.footnote).foregroundStyle(Palette.inkSecondary)
        }
    }
    private func empty(title: String.LocalizationValue, body: String.LocalizationValue) -> some View {
        VStack(alignment: .leading, spacing: Spacing.lg) {
            EditorialIcon(journey ? "book.pages" : "bookmark")
            Text(L10n.t(title)).font(Typography.editorialHeadline).foregroundStyle(Palette.ink)
            Text(L10n.t(body)).font(Typography.subheadline).foregroundStyle(Palette.inkSecondary)
            Button(L10n.t("Browse books")) { store.send(.browse) }
                .buttonStyle(.borderedProminent).foregroundStyle(Palette.onAccent).controlSize(.large).tint(Palette.accent)
        }.frame(maxWidth: .infinity, alignment: .leading).padding(Spacing.xl).editorialSurface()
    }
    private func filterTitle(_ value: ReadingCollectionFilter) -> String {
        let key: String.LocalizationValue = switch value { case .all: "All"; case .saved: "Saved"; case .highlights: "Highlights"; case .notes: "Notes" }
        return L10n.t(key)
    }
}
