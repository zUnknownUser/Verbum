import ComposableArchitecture
import DesignSystem
import Models
import SwiftUI

/// The verse of the day as a card on Home: the text in the reading face, its
/// reference, open + share, and the morning toggle underneath.
struct DailyVerseView: View {
    let store: StoreOf<DailyVerseFeature>
    @Environment(\.openURL) private var openURL

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.lg) {
            verse
            Divider().overlay(Palette.rule)
            mornings
        }
        .padding(Spacing.lg)
        .background(Palette.paperElevated, in: .rect(cornerRadius: Radius.lg))
        .overlay(RoundedRectangle(cornerRadius: Radius.lg).strokeBorder(Palette.rule, lineWidth: 1))
        .task { await store.send(.task).finish() }
    }

    @ViewBuilder
    private var verse: some View {
        switch store.text {
        case .loading:
            // Reserve the height of a short verse so the card does not jump.
            ProgressView()
                .frame(maxWidth: .infinity, minHeight: 72)
                .accessibilityLabel(L10n.t("Loading"))
        case .loaded(let passage):
            Text(passage.text)
                .font(Typography.scripture)
                .foregroundStyle(Palette.ink)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityLabel(Text(L10n.t("Verse of the day")) + Text(". ") + Text(passage.text))
        case .failed:
            Text(L10n.t("Couldn't load today's verse. Open it to read it in its chapter."))
                .font(Typography.footnote)
                .foregroundStyle(Palette.inkSecondary)
        }

        HStack(alignment: .firstTextBaseline, spacing: Spacing.md) {
            Button { store.send(.openTapped) } label: {
                HStack(spacing: Spacing.xs) {
                    Text(store.reference.formatted)
                        .font(.system(.title3, design: .serif).weight(.semibold))
                    Image(systemName: "arrow.right")
                        .font(Typography.subheadline.weight(.semibold))
                        .accessibilityHidden(true)
                }
                .foregroundStyle(Palette.accent)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(L10n.t("Open \(store.reference.formatted)"))

            Spacer()

            if let shareText = store.shareText {
                ShareLink(item: shareText, subject: Text(store.reference.formatted)) {
                    Image(systemName: "square.and.arrow.up")
                        .font(.system(size: 18, weight: .light))
                        .foregroundStyle(Palette.accent)
                        .frame(width: 44, height: 44)
                        .contentShape(Rectangle())
                }
                .accessibilityLabel(L10n.t("Share verse"))
            }
        }
    }

    private var mornings: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Toggle(isOn: Binding(
                get: { store.morningsEnabled },
                set: { store.send(.morningsToggled($0)) }
            )) {
                Label {
                    Text(L10n.t("Every morning at 7:00"))
                        .font(Typography.subheadline)
                        .foregroundStyle(Palette.ink)
                } icon: {
                    Image(systemName: "sunrise")
                        .font(.system(size: 18, weight: .light))
                        .foregroundStyle(Palette.accent)
                }
            }
            .tint(Palette.accent)

            if store.morningsEnabled && store.authorization == .denied {
                Button {
                    if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
                } label: {
                    Text(L10n.t("Notifications are off for Verbum. Turn them on in Settings."))
                        .font(Typography.footnote)
                        .foregroundStyle(Palette.inkSecondary)
                        .multilineTextAlignment(.leading)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .buttonStyle(.plain)
                .accessibilityHint(L10n.t("Opens Settings"))
            }
        }
    }
}

#Preview {
    ScrollView {
        DailyVerseView(store: Store(initialState: DailyVerseFeature.State()) { DailyVerseFeature() })
            .padding(Spacing.readingMargin)
    }
    .background(Palette.paper)
}
