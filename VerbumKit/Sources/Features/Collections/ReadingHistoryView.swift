import ComposableArchitecture
import DesignSystem
import Models
import SwiftUI

struct ReadingHistoryView: View {
    let store: StoreOf<ReadingHistoryFeature>
    @Environment(\.calendar) private var calendar

    private var days: [Date] {
        Set(store.visibleVisits.map { calendar.startOfDay(for: $0.lastOpened) }).sorted(by: >)
    }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: Spacing.lg) {
                if store.visibleVisits.isEmpty {
                    VStack(alignment: .leading, spacing: Spacing.md) {
                        EditorialIcon("magnifyingglass")
                        Text(L10n.t("No chapters found")).font(Typography.editorialHeadline)
                        Text(L10n.t("Try another book or chapter reference."))
                            .font(Typography.subheadline).foregroundStyle(Palette.inkSecondary)
                    }.frame(maxWidth: .infinity, alignment: .leading).padding(Spacing.xl).editorialSurface()
                }
                ForEach(days, id: \.self) { day in
                    Section {
                        ForEach(store.visibleVisits.filter { calendar.isDate($0.lastOpened, inSameDayAs: day) }) { visit in
                            ReadingVisitRow(visit: visit) { store.send(.open(visit.reference)) }
                        }
                    } header: {
                        Text(dayTitle(day)).overline(color: Palette.accent).padding(.top, Spacing.md)
                    }
                }
                if store.hasMore {
                    Button(L10n.t("Show more")) { store.send(.showMore) }
                        .buttonStyle(.bordered).controlSize(.large).tint(Palette.accent)
                        .frame(maxWidth: .infinity)
                        .accessibilityIdentifier("journey.history.showMore")
                }
            }
            .frame(maxWidth: Spacing.readingMaxWidth)
            .frame(maxWidth: .infinity)
            .padding(Spacing.readingMargin)
        }
        .background(Palette.paper)
        .foregroundStyle(Palette.ink)
        .navigationTitle(L10n.t("Reading history"))
        .navigationBarTitleDisplayMode(.inline)
        .searchable(text: Binding(get: { store.query }, set: { store.send(.queryChanged($0)) }),
                    placement: .navigationBarDrawer(displayMode: .always),
                    prompt: L10n.t("Search by book or chapter"))
    }

    private func dayTitle(_ day: Date) -> String {
        if calendar.isDateInToday(day) { return L10n.t("Today") }
        if calendar.isDateInYesterday(day) { return L10n.t("Yesterday") }
        return day.formatted(Date.FormatStyle(date: .complete, time: .omitted, locale: BookLanguage.current.locale, calendar: calendar))
    }
}

struct ReadingVisitRow: View {
    let visit: ReadingActivity.Visit
    let open: () -> Void

    var body: some View {
        Button(action: open) {
            VStack(alignment: .leading, spacing: Spacing.sm) {
                HStack {
                    Text(visit.reference.formatted).font(Typography.navigationSerif)
                    Spacer()
                    Image(systemName: "chevron.right").font(.caption).foregroundStyle(Palette.inkTertiary)
                        .accessibilityHidden(true)
                }
                Text(visit.lastOpened.formatted(Date.FormatStyle(date: .abbreviated, time: .shortened).locale(BookLanguage.current.locale)))
                    .font(Typography.caption).foregroundStyle(Palette.inkSecondary)
                Divider().overlay(Palette.rule)
            }.frame(minHeight: 44).contentShape(Rectangle())
        }.buttonStyle(.plain).foregroundStyle(Palette.ink)
    }
}
