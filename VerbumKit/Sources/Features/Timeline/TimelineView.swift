import ComposableArchitecture
import DesignSystem
import Models
import SwiftUI

/// A spine down the left, one row per period or event: the dating (with its
/// uncertainty) above the title, a bar for a span and a dot for a moment.
/// Tap opens the row in place; people and places inside go to their pages.
public struct TimelineView: View {
    let store: StoreOf<TimelineFeature>
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
                ScrollViewReader { proxy in
                    ScrollView {
                        LazyVStack(alignment: .leading, spacing: 0) {
                            ForEach(events) { event in
                                TimelineRow(
                                    event: event,
                                    isSelected: store.selectedID == event.id,
                                    isHighlighted: store.highlight.map(event.entityIds.contains) ?? false,
                                    entityName: { store.entityNames[$0] },
                                    tap: { store.send(.eventTapped(event.id), animation: reduceMotion ? nil : Motion.standard) },
                                    entityTap: { store.send(.entityTapped($0)) }
                                )
                                .id(event.id)
                            }
                            Text(L10n.t("Dates are the ones commonly given; “debated” marks where scholarship is split, and the span shows both positions."))
                                .font(Typography.footnote)
                                .foregroundStyle(Palette.inkTertiary)
                                .padding(.top, Spacing.xl)
                        }
                        .frame(maxWidth: Spacing.readingMaxWidth, alignment: .leading)
                        .frame(maxWidth: .infinity)
                        .padding(.horizontal, Spacing.readingMargin)
                        .padding(.vertical, Spacing.lg)
                    }
                    .scrollIndicators(.hidden)
                    .onAppear {
                        if let id = store.highlightedEventID { proxy.scrollTo(id, anchor: .top) }
                    }
                }
            }
        }
        .background(Palette.paper)
        .navigationTitle(L10n.t("Timeline"))
        .navigationBarTitleDisplayMode(.inline)
        .task { await store.send(.task).finish() }
    }
}

private struct TimelineRow: View {
    let event: TimelineEvent
    let isSelected: Bool
    let isHighlighted: Bool
    let entityName: (EntityID) -> String?
    let tap: () -> Void
    let entityTap: (EntityID) -> Void

    var body: some View {
        HStack(alignment: .top, spacing: Spacing.lg) {
            spine
            VStack(alignment: .leading, spacing: Spacing.xs) {
                Button(action: tap) {
                    VStack(alignment: .leading, spacing: Spacing.xxs) {
                        Text(TimelineDates.text(for: event))
                            .font(Typography.caption)
                            .foregroundStyle(event.datePrecision == .debated ? Palette.accent : Palette.inkSecondary)
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

                if isSelected {
                    detail
                }
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
            if let summary = event.summary {
                Text(summary)
                    .font(Typography.body)
                    .foregroundStyle(Palette.inkSecondary)
                    .fixedSize(horizontal: false, vertical: true)
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
            Text(L10n.t("Source: Verbum editorial notes (fixture)"))
                .font(Typography.caption2)
                .foregroundStyle(Palette.inkTertiary)
        }
        .padding(.top, Spacing.xs)
        .transition(.opacity)
    }
}
