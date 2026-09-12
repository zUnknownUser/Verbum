import SwiftUI

// Visual reference for the token set. Open the canvas on this file to review
// typography, surfaces and accent in light/dark and at any Dynamic Type size.
// Not shipped: previews are stripped from release builds.

#Preview("Tokens") {
    NavigationStack {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.xl) {
                VStack(alignment: .leading, spacing: Spacing.sm) {
                    Text("David").font(Typography.editorialTitle)
                    Text("King of Israel")
                        .font(Typography.subheadline)
                        .foregroundStyle(Palette.foregroundSecondary)
                }

                VStack(alignment: .leading, spacing: Spacing.md) {
                    Text("1 Samuel 17").font(Typography.scriptureHeading)
                    HStack(alignment: .firstTextBaseline, spacing: Spacing.sm) {
                        Text("45")
                            .font(Typography.verseNumber)
                            .foregroundStyle(Palette.foregroundTertiary)
                        Text("Then David said to the Philistine, “You come to me with a sword and with a spear and with a javelin, but I come to you in the name of the LORD of hosts.”")
                            .font(Typography.scripture)
                            .lineSpacing(Typography.scriptureLineSpacing)
                    }
                }
                .padding(.horizontal, Spacing.readingMargin - Spacing.screenMargin)

                VStack(alignment: .leading, spacing: Spacing.sm) {
                    Text("Surface").font(Typography.headline)
                    Text("Secondary grouped background, card elevation.")
                        .font(Typography.footnote)
                        .foregroundStyle(Palette.foregroundSecondary)
                }
                .padding(Spacing.lg)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Palette.surface, in: .rect(cornerRadius: Radius.lg))
                .elevation(.card)

                Button("Explore connections") {}
                    .buttonStyle(.borderedProminent)
                    .tint(Palette.accent)
            }
            .padding(Spacing.screenMargin)
        }
        .background(Palette.groupedBackground)
        .navigationTitle("Tokens")
    }
}
