import ComposableArchitecture
import DesignSystem
import Models
import SwiftUI

struct GuidedExplorationView: View {
    let store: StoreOf<GuidedExplorationFeature>
    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: Spacing.xl) {
                Text(store.feeling?.title ?? L10n.t("How are you arriving today?"))
                    .font(Typography.editorialTitle).accessibilityAddTraits(.isHeader)
                if store.feeling == nil {
                    Text(L10n.t("Choose a starting point, at your own pace. This choice is optional and is not saved to a profile."))
                        .font(Typography.subheadline).foregroundStyle(Palette.inkSecondary)
                    ForEach(ArrivalFeeling.allCases, id: \.self) { feeling in
                        Button { store.send(.select(feeling)) } label: {
                            HStack { Text(feeling.title); Spacer(); Image(systemName: "chevron.right").accessibilityHidden(true) }
                                .font(Typography.subheadline).padding(.vertical, Spacing.md).contentShape(Rectangle())
                        }
                    }
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
