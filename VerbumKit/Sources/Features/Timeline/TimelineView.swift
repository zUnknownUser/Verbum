import ComposableArchitecture
import DesignSystem
import Models
import SwiftUI

/// A spine down the left, one row per period or event: the dating (with its
/// uncertainty) above the title, a bar for a span and a dot for a moment.
/// Tap opens the row in place; people and places inside go to their pages.
public struct TimelineView: View {
    let store: StoreOf<TimelineFeature>
    @State private var query = ""
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    public init(store: StoreOf<TimelineFeature>) {
        self.store = store
    }

    public var body: some View {
        Group {
            switch store.content {
            case .idle, .loading:
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            case .failed:
                ContentUnavailableView {
                    Label(L10n.t("Couldn't load the timeline"), systemImage: "clock")
                } actions: {
                    Button(L10n.t("Try again")) { store.send(.retryTapped) }
                }
            case .loaded(let events):
                let eras = events.reduce(into: [TimelineDiscovery]()) { result, event in
                    if let d = event.discovery, !result.contains(where: { $0.eraId == d.eraId }) { result.append(d) }
                }
                let searching = !query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                let visible = events.filter { event in
                    if searching {
                        return [event.title, event.summary ?? "", event.discovery?.eraTitle ?? "", event.discovery?.context ?? ""]
                            .joined(separator: " ").localizedStandardContains(query.trimmingCharacters(in: .whitespacesAndNewlines))
                    }
                    return store.selectedEraID == nil ? eras.isEmpty : event.discovery?.eraId == store.selectedEraID
                }
                ScrollViewReader { proxy in
                    ScrollView {
                        LazyVStack(alignment: .leading, spacing: Spacing.md) {
                            if !searching && store.selectedEraID == nil && !eras.isEmpty {
                                Text(L10n.t("Follow the story. Explore its connections."))
                                    .font(.system(.largeTitle, design: .serif)).foregroundStyle(Palette.ink)
                                Text(L10n.t("Choose an era to study its events and read the biblical accounts."))
                                    .font(Typography.body).foregroundStyle(Palette.inkSecondary)
                                ForEach(Array(eras.enumerated()), id: \.element.eraId) { index, era in
                                    Button { store.send(.eraTapped(era.eraId)) } label: {
                                        HStack(alignment: .top, spacing: Spacing.md) {
                                            Text(String(format: "%02d", index + 1)).font(Typography.caption).foregroundStyle(Palette.accent)
                                            VStack(alignment: .leading, spacing: Spacing.sm) {
                                                Text(era.eraTitle).font(.system(.title2, design: .serif)).foregroundStyle(Palette.ink)
                                                Text(era.eraSummary).font(Typography.footnote).foregroundStyle(Palette.inkSecondary)
                                                let count = events.filter { $0.discovery?.eraId == era.eraId }.count
                                                Text(L10n.t("\(count) events")).font(Typography.caption).foregroundStyle(Palette.accent)
                                            }
                                            Spacer(minLength: 0)
                                            Image(systemName: "chevron.right").foregroundStyle(Palette.accent).accessibilityHidden(true)
                                        }
                                        .frame(maxWidth: .infinity, alignment: .leading).padding(Spacing.lg)
                                        .background(Palette.paperElevated, in: .rect(cornerRadius: Radius.md))
                                        .overlay(RoundedRectangle(cornerRadius: Radius.md).stroke(Palette.rule, lineWidth: 1))
                                    }.buttonStyle(.plain)
                                }
                            } else {
                                if !searching, let era = eras.first(where: { $0.eraId == store.selectedEraID }) {
                                    Button { store.send(.eraTapped(nil)) } label: {
                                        Label(L10n.t("All eras"), systemImage: "square.grid.2x2")
                                    }.tint(Palette.accent)
                                    Text(era.eraTitle).font(.system(.largeTitle, design: .serif)).foregroundStyle(Palette.ink)
                                    Text(era.eraSummary).font(Typography.body).foregroundStyle(Palette.inkSecondary)
                                }
                                if visible.isEmpty {
                                    ContentUnavailableView.search(text: query)
                                }
                                ForEach(visible) { event in
                                    TimelineRow(
                                        event: event,
                                        showEra: searching,
                                        isSelected: store.selectedID == event.id,
                                        isHighlighted: store.highlight.map(event.entityIds.contains) ?? false,
                                        entityName: { store.entityNames[$0] },
                                        tap: { store.send(.eventTapped(event.id), animation: reduceMotion ? nil : Motion.standard) },
                                        entityTap: { store.send(.entityTapped($0)) },
                                        passageTap: { store.send(.passageTapped($0)) }
                                    )
                                    .id(event.id)
                                }
                                if !searching, let index = eras.firstIndex(where: { $0.eraId == store.selectedEraID }) {
                                    HStack {
                                        if index > 0 { Button(L10n.t("Previous era")) { store.send(.eraTapped(eras[index - 1].eraId)) } }
                                        Spacer()
                                        if index + 1 < eras.count { Button(L10n.t("Next era")) { store.send(.eraTapped(eras[index + 1].eraId)) } }
                                    }.font(Typography.footnote).tint(Palette.accent).padding(.vertical, Spacing.lg)
                                }
                            }
                            Text(L10n.t("A study guide to the biblical narrative, not an exact calendar. Some readings are grouped by context; the Gospels do not always present episodes in the same order."))
                                .font(Typography.footnote).foregroundStyle(Palette.inkTertiary).padding(.top, Spacing.lg)
                        }
                        .id(store.selectedEraID ?? "overview")
                        .frame(maxWidth: Spacing.readingMaxWidth, alignment: .leading)
                        .frame(maxWidth: .infinity)
                        .padding(.horizontal, Spacing.readingMargin)
                        .padding(.vertical, Spacing.lg)
                    }
                    .scrollIndicators(.hidden)
                    .onChange(of: store.selectedEraID) { _, _ in proxy.scrollTo(store.selectedEraID ?? "overview", anchor: .top) }
                    .onAppear {
                        if let id = store.highlightedEventID { proxy.scrollTo(id, anchor: .top) }
                    }
                }
            }
        }
        .searchable(text: $query, prompt: L10n.t("Search events and eras"))
        .background(Palette.paper)
        .navigationTitle(L10n.t("Timeline"))
        .navigationBarTitleDisplayMode(.inline)
        .task { await store.send(.task).finish() }
    }
}

private struct TimelineRow: View {
    let event: TimelineEvent
    let showEra: Bool
    let isSelected: Bool
    let isHighlighted: Bool
    let entityName: (EntityID) -> String?
    let tap: () -> Void
    let entityTap: (EntityID) -> Void
    let passageTap: (PassageReference) -> Void

    var body: some View {
        HStack(alignment: .top, spacing: Spacing.lg) {
            spine
            VStack(alignment: .leading, spacing: Spacing.xs) {
                Button(action: tap) {
                    VStack(alignment: .leading, spacing: Spacing.xxs) {
                        if showEra || event.discovery == nil {
                            Text(event.discovery?.eraTitle ?? TimelineDates.text(for: event))
                                .font(Typography.caption)
                                .foregroundStyle(event.datePrecision == .debated ? Palette.accent : Palette.inkSecondary)
                        }
                        Text(event.title)
                            .font(.system(.title3, design: .serif).weight(isHighlighted ? .semibold : .regular))
                            .foregroundStyle(Palette.ink)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("\(event.title). \(TimelineDates.text(for: event))")
                .accessibilityAddTraits(isSelected ? [.isSelected] : [])
                .accessibilityHint(isSelected ? L10n.t("Collapses the event") : L10n.t("Opens the event"))

                if let summary = event.summary {
                    Text(summary).font(Typography.footnote).foregroundStyle(Palette.inkSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                if let first = event.discovery?.keyPassages.first {
                    Text(first.formatted).font(Typography.caption).foregroundStyle(Palette.accent)
                }
                if isSelected { detail }
                Button(isSelected ? L10n.t("Close study") : L10n.t("Study this event"), action: tap)
                    .font(Typography.footnote).tint(Palette.accent)
            }
            .padding(.vertical, Spacing.md)
            .padding(.horizontal, Spacing.sm)
            .background(isHighlighted ? Palette.accent.opacity(0.06) : .clear, in: .rect(cornerRadius: Radius.md))
        }
    }

    /// A dot for a moment, a bar for a span; hollow when the dating is debated
    /// or unknown, so the shape says it before the words do.
    private var spine: some View {
        ZStack(alignment: .top) {
            Rectangle().fill(Palette.rule).frame(width: 1).frame(maxHeight: .infinity)
            Group {
                if event.isPeriod {
                    Capsule()
                        .fill(hollow ? Palette.paper : Palette.accent)
                        .overlay(Capsule().strokeBorder(Palette.accent, lineWidth: 1.5))
                        .frame(width: 8, height: 36)
                } else {
                    Circle()
                        .fill(hollow ? Palette.paper : Palette.accent)
                        .overlay(Circle().strokeBorder(Palette.accent, lineWidth: 1.5))
                        .frame(width: 10, height: 10)
                }
            }
            .padding(.top, Spacing.md + 20)
        }
        .frame(width: 12)
        .accessibilityHidden(true)
    }

    private var hollow: Bool { event.datePrecision == .debated || event.datePrecision == .unknown }

    private var detail: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            if let discovery = event.discovery {
                Text(L10n.t("How it connects")).font(Typography.footnote.weight(.semibold)).foregroundStyle(Palette.ink)
                Text(discovery.context).font(Typography.body).foregroundStyle(Palette.inkSecondary)
                    .fixedSize(horizontal: false, vertical: true)
                Text(L10n.t("Study in Scripture")).font(Typography.footnote.weight(.semibold)).foregroundStyle(Palette.ink)
                ForEach(discovery.keyPassages, id: \.self) { reference in
                    Button { passageTap(reference) } label: {
                        HStack { Image(systemName: "book"); Text(reference.formatted); Spacer(); Image(systemName: "arrow.up.right") }
                            .font(Typography.body).padding(Spacing.md)
                            .background(Palette.paperElevated, in: .rect(cornerRadius: Radius.md))
                    }.tint(Palette.accent)
                }
            }
            let named = event.entityIds.compactMap { id in entityName(id).map { (id, $0) } }
            if !named.isEmpty {
                FlowLayout(horizontalSpacing: Spacing.xs, verticalSpacing: Spacing.xs) {
                    ForEach(named, id: \.0) { id, name in
                        Button { entityTap(id) } label: {
                            Text(name)
                                .font(Typography.footnote)
                                .foregroundStyle(Palette.accent)
                                .padding(.horizontal, Spacing.md)
                                .padding(.vertical, Spacing.xs)
                                .background(Palette.paperElevated, in: .capsule)
                                .overlay(Capsule().strokeBorder(Palette.rule, lineWidth: 1))
                        }
                        .buttonStyle(.plain)
                        .accessibilityHint(L10n.t("Opens the entity"))
                    }
                }
            }
            if event.discovery != nil {
                Text(L10n.t("Verbum study notes · Read the linked accounts in context."))
                    .font(Typography.caption2).foregroundStyle(Palette.inkTertiary)
            }
        }
        .padding(.top, Spacing.xs)
        .transition(.opacity)
    }
}
