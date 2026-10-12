import ComposableArchitecture
import DesignSystem
import Foundation
import SwiftUI

// Uses the same period IDs and editorial labels as the timeline catalogue.
struct EventPeriod: Identifiable {
    let id: String
    let title: String.LocalizationValue
    let subtitle: String.LocalizationValue
    static let all: [Self] = [
        .init(id: "origins", title: "Origins", subtitle: "Creation, rupture, preservation and scattering in the opening chapters of Genesis."),
        .init(id: "patriarchs", title: "The patriarchs", subtitle: "The promise follows a family, from Abraham to the arrival in Egypt."),
        .init(id: "exodus", title: "Exodus and wilderness", subtitle: "Deliverance, covenant and the formation of a people during the journey."),
        .init(id: "judges", title: "The land and the judges", subtitle: "Entry into the land, crises of faithfulness and different forms of leadership."),
        .init(id: "monarchy", title: "The rise of the monarchy", subtitle: "Saul, David and Solomon: leadership, promises and the limits of power."),
        .init(id: "kingdoms", title: "Kingdoms and prophets", subtitle: "Divided kingdoms, prophetic messages and the path through Samaria’s fall."),
        .init(id: "exile", title: "Crisis and exile", subtitle: "Jerusalem’s fall, life in exile, lament and hope. Readings grouped by context, without an exact order between books."),
        .init(id: "return", title: "Return and rebuilding", subtitle: "Life under Persian rule, return and the renewal of the community."),
        .init(id: "jesus", title: "The life of Jesus", subtitle: "A reading of the Gospels, from the announcement of the birth to the resurrection. Episode order varies between accounts."),
        .init(id: "church", title: "The church in Acts", subtitle: "From witness in Jerusalem to missionary journeys and the arrival in Rome."),

    ]
}

struct EventDiscoveryView: View {
    let store: StoreOf<EntityListFeature>
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.lg) {
                Text(L10n.t("Explore by period")).font(Typography.editorialTitle)
                Text(L10n.t("Choose a period to explore its events."))
                    .font(Typography.subheadline).foregroundStyle(Palette.inkSecondary)
                Button { store.send(.categoryChanged("")) } label: {
                    HStack { Text(L10n.t("All events")); Spacer(); Image(systemName: "arrow.right") }
                        .font(Typography.navigationSerif).padding(Spacing.lg)
                        .foregroundStyle(Palette.onForest).background(Palette.forest, in: RoundedRectangle(cornerRadius: 18))
                }.buttonStyle(EditorialButtonStyle()).accessibilityIdentifier("events.all")
                ForEach(EventPeriod.all) { period in
                    Button { store.send(.categoryChanged(period.id)) } label: {
                        HStack(spacing: Spacing.md) {
                            VStack(alignment: .leading, spacing: Spacing.sm) {
                                Text(L10n.t(period.title)).font(Typography.navigationSerif).foregroundStyle(Palette.ink)
                                Text(L10n.t(period.subtitle)).font(Typography.footnote).foregroundStyle(Palette.inkSecondary)
                            }.frame(maxWidth: .infinity, alignment: .leading)
                            Image(systemName: "chevron.right").foregroundStyle(Palette.accent).accessibilityHidden(true)
                        }.padding(Spacing.lg).background(Palette.paperElevated, in: RoundedRectangle(cornerRadius: 18))
                    }.buttonStyle(EditorialButtonStyle()).accessibilityIdentifier("events.period.\(period.id)")
                }
                Text(L10n.t("Follow a study sequence in Exodus and wilderness, or browse events alphabetically."))
                    .font(Typography.footnote).foregroundStyle(Palette.inkSecondary)
            }.frame(maxWidth: Spacing.readingMaxWidth).frame(maxWidth: .infinity)
                .padding(.horizontal, Spacing.readingMargin).padding(.vertical, Spacing.xl)
        }.scrollDismissesKeyboard(.interactively)
    }
}
