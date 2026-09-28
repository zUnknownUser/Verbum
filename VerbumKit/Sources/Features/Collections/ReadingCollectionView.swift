import ComposableArchitecture
import DesignSystem
import Models
import SwiftUI

/// Both tabs use the existing account-scoped annotations and reading activity.
struct ReadingCollectionView: View {
    let store: StoreOf<ReadingCollectionFeature>
    let journey: Bool

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: Spacing.lg) {
                Text(L10n.t(journey ? "Your time in Scripture" : "Keep what matters to you"))
                    .font(Typography.editorialTitle).foregroundStyle(Palette.ink)
                Text(L10n.t(journey ? "Revisit the chapters you have opened and continue at your own pace." : "Your saved passages, highlights and notes, together."))
                    .font(Typography.subheadline).foregroundStyle(Palette.inkSecondary)
                if journey { journeyContent } else { libraryContent }
                Text(L10n.t("Stored on this device. Signing in does not sync these items yet."))
                    .font(Typography.caption).foregroundStyle(Palette.inkTertiary)
                    .padding(.top, Spacing.lg)
            }
            .frame(maxWidth: Spacing.readingMaxWidth, alignment: .leading)
            .frame(maxWidth: .infinity)
            .padding(Spacing.readingMargin)
        }
        .background(Palette.paper)
        .navigationTitle(L10n.t(journey ? "Journey" : "Library"))
        .task { await store.send(.task).finish() }
        .refreshable { await store.send(.retry).finish() }
    }

    @ViewBuilder private var libraryContent: some View {
        TextField(L10n.t("Search references or your notes"), text: Binding(get: { store.query }, set: { store.send(.queryChanged($0)) }))
            .textFieldStyle(.roundedBorder).submitLabel(.search)
            .accessibilityIdentifier("library.search")
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Spacing.sm) {
                ForEach(ReadingCollectionFilter.allCases, id: \.self) { filter in
                    Button { store.send(.filterChanged(filter)) } label: {
                        Text(filterTitle(filter)).font(Typography.footnote)
                            .padding(.horizontal, Spacing.md).padding(.vertical, Spacing.sm)
                            .background(store.filter == filter ? Palette.accent.opacity(0.14) : Palette.ink.opacity(0.04), in: Capsule())
                    }.buttonStyle(.plain).foregroundStyle(Palette.ink)
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
                    Divider().overlay(Palette.rule).padding(.top, Spacing.sm)
                }.contentShape(Rectangle())
            }.buttonStyle(.plain).foregroundStyle(Palette.ink)
        }
    }

    @ViewBuilder private var journeyContent: some View {
        ViewThatFits(in: .horizontal) {
            HStack(spacing: Spacing.xxl) { metrics }
            VStack(alignment: .leading, spacing: Spacing.md) { metrics }
        }.padding(.vertical, Spacing.md)
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
            ForEach(store.activity.visits) { visit in
                Button { store.send(.open(visit.reference)) } label: {
                    VStack(alignment: .leading, spacing: Spacing.sm) {
                        Text(visit.reference.formatted).font(Typography.navigationSerif).foregroundStyle(Palette.ink)
                        Text(visit.lastOpened.formatted(Date.FormatStyle(date: .abbreviated, time: .shortened).locale(BookLanguage.current.locale)))
                            .font(Typography.caption).foregroundStyle(Palette.inkSecondary)
                        Divider().overlay(Palette.rule)
                    }.contentShape(Rectangle())
                }.buttonStyle(.plain)
            }
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
        VStack(alignment: .leading, spacing: Spacing.md) {
            Text(L10n.t(title)).font(Typography.editorialHeadline)
            Text(L10n.t(body)).font(Typography.subheadline).foregroundStyle(Palette.inkSecondary)
            Button(L10n.t("Browse books")) { store.send(.browse) }.tint(Palette.accent)
        }.padding(.vertical, Spacing.lg)
    }
    private func filterTitle(_ value: ReadingCollectionFilter) -> String {
        let key: String.LocalizationValue = switch value { case .all: "All"; case .saved: "Saved"; case .highlights: "Highlights"; case .notes: "Notes" }
        return L10n.t(key)
    }
}
