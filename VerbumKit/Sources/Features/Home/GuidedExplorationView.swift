import ComposableArchitecture
import DesignSystem
import Models
import SwiftUI

struct GuidedExplorationView: View {
    let store: StoreOf<GuidedExplorationFeature>
    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: Spacing.xl) {
                Text(store.feeling?.title ?? L10n.t("How are you feeling today?"))
                    .font(Typography.editorialTitle).accessibilityAddTraits(.isHeader)
                if store.feeling == nil {
                    FeelingChooser { store.send(.select($0)) }
                } else {
                    Text(L10n.t("I want to understand what Scripture says about this.")).font(Typography.editorialHeadline)
                    if store.isLoading { ProgressView() }
                    if let plan = store.plan {
                        if plan.isEditorialPreview {
                            Text(L10n.t("Editorial preview · Suggested readings, not an AI answer or a promise about your situation."))
                                .font(Typography.footnote).foregroundStyle(Palette.inkSecondary)
                        }
                        Text(plan.guidingQuestion).font(Typography.scripture)
                        ForEach(plan.passages, id: \.self) { reference in
                            VStack(alignment: .leading, spacing: Spacing.sm) {
                                Text(reference.formatted).font(Typography.editorialHeadline)
                                Button(L10n.t("Read the passage in its chapter")) { store.send(.delegate(.openPassage(reference))) }
                                Button(L10n.t("Explore chapter context")) { store.send(.delegate(.openContext(reference))) }
                            }.padding(.vertical, Spacing.sm)
                        }
                        Text(L10n.t("The passages above are the sources. Context opens available people, places, themes and related readings; missing historical information is not generated."))
                            .font(Typography.footnote).foregroundStyle(Palette.inkSecondary)
                    }
                    if store.failed, let feeling = store.feeling {
                        Text(L10n.t("Couldn't load this page"))
                        Button(L10n.t("Try Again")) { store.send(.select(feeling)) }
                    }
                    Button(L10n.t("Choose another starting point")) { store.send(.changeFeeling) }
                }
            }
            .foregroundStyle(Palette.ink)
            .frame(maxWidth: Spacing.readingMaxWidth, alignment: .leading).frame(maxWidth: .infinity)
            .padding(Spacing.readingMargin)
        }
        .background(Palette.paper.ignoresSafeArea()).tint(Palette.accent)
        .navigationTitle(L10n.t("Explore at your own pace"))
        .navigationBarTitleDisplayMode(.inline).toolbar(.visible, for: .navigationBar)
    }
}

/// The feelings as words adrift rather than a list to work through: three rows
/// that bleed past the reading column and move at walking pace in alternating
/// directions, each one also pushable with a finger. The point is that nothing
/// here asks to be completed — you reach in and take one.
///
/// Reduce Motion, VoiceOver and accessibility text sizes get the same words
/// as a still, wrapping field instead, where every word is in reach at once.
private struct FeelingChooser: View {
    let select: (ArrivalFeeling) -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.accessibilityVoiceOverEnabled) private var voiceOver
    @Environment(\.dynamicTypeSize) private var typeSize

    /// Points per second, alternating with the drift direction below so the
    /// rows never lock into a pattern.
    private static let speeds: [CGFloat] = [Motion.Drift.medium, Motion.Drift.slow, Motion.Drift.fast]

    private var isStill: Bool { reduceMotion || voiceOver || typeSize.isAccessibilitySize }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.lg) {
            Text(L10n.t("Choose a starting point, at your own pace. This choice is optional and is not saved to a profile."))
                .font(Typography.subheadline).foregroundStyle(Palette.inkSecondary)
            if isStill {
                FlowLayout(horizontalSpacing: Spacing.sm, verticalSpacing: Spacing.sm) {
                    ForEach(ArrivalFeeling.allCases, id: \.self) { feeling in
                        FeelingWord(feeling: feeling) { select(feeling) }
                    }
                }
            } else {
                VStack(spacing: Spacing.sm) {
                    ForEach(Array(Self.rows.enumerated()), id: \.offset) { index, row in
                        DriftingRow(
                            speed: Self.speeds[index % Self.speeds.count],
                            direction: index.isMultiple(of: 2) ? .leading : .trailing
                        ) {
                            ForEach(row, id: \.self) { feeling in
                                FeelingWord(feeling: feeling) { select(feeling) }
                            }
                        }
                    }
                }
                // Bleed past the reading margin: a row that ends before the edge
                // of the screen reads as a list again.
                .padding(.horizontal, -Spacing.readingMargin)
                .edgeFade()
            }
        }
    }

    /// Dealt round-robin so each row mixes long and short words, and so adding
    /// a feeling later simply lands in the next row.
    private static var rows: [[ArrivalFeeling]] {
        let count = 3
        let feelings = ArrivalFeeling.allCases
        return (0..<count).map { row in
            feelings.enumerated().filter { $0.offset % count == row }.map(\.element)
        }
    }
}

/// One feeling, set as a word on paper: serif, a hairline capsule, bronze only
/// while a finger is on it.
private struct FeelingWord: View {
    let feeling: ArrivalFeeling
    let action: () -> Void

    @Environment(\.isPushingDriftingRow) private var isPushing

    var body: some View {
        // A push that ends on a word is a push, not a choice.
        Button { if !isPushing { action() } } label: {
            Text(feeling.title)
                .font(.system(.title3, design: .serif))
                .lineLimit(1)
                .fixedSize()
        }
        .buttonStyle(FeelingWordStyle())
    }
}

private struct FeelingWordStyle: ButtonStyle {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func makeBody(configuration: Configuration) -> some View {
        let pressed = configuration.isPressed
        return configuration.label
            .foregroundStyle(pressed ? Palette.accent : Palette.ink)
            .padding(.horizontal, Spacing.xl)
            .padding(.vertical, Spacing.md)
            .background(pressed ? Palette.selectionWash : Palette.paperElevated, in: .capsule)
            .overlay(
                Capsule().strokeBorder(pressed ? Palette.accent : Palette.rule, lineWidth: 1)
            )
            .contentShape(Capsule())
            .animation(Motion.resolved(Motion.standard, reduceMotion: reduceMotion), value: pressed)
    }
}

private extension ArrivalFeeling {
    var title: String {
        switch self {
        case .anxious: L10n.t("Anxious")
        case .lost: L10n.t("Lost")
        case .grateful: L10n.t("Grateful")
        case .tired: L10n.t("Tired")
        case .afraid: L10n.t("Afraid")
        case .alone: L10n.t("Alone")
        case .angry: L10n.t("Angry")
        case .hopeless: L10n.t("Without hope")
        case .peaceful: L10n.t("At peace")
        }
    }
}
