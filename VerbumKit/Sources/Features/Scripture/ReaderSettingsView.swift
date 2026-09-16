import ComposableArchitecture
import DesignSystem
import Models
import SwiftUI

struct ReaderSettingsView: View {
    let store: StoreOf<ReaderSettingsFeature>
    @ScaledMetric(relativeTo: .body) private var basePointSize = Typography.scriptureBasePointSize

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.lg) {
            Text(L10n.t("Reading")).overline()
            Picker(L10n.t("Reading mode"), selection: Binding(get: { store.readingMode }, set: { store.send(.readingModeChanged($0)) })) {
                Text(L10n.t("Pages")).tag(ReadingMode.pages)
                Text(L10n.t("Continuous")).tag(ReadingMode.continuous)
            }.pickerStyle(.segmented)
            Toggle(L10n.t("Quiet reading"), isOn: Binding(get: { store.focusMode }, set: { store.send(.focusModeChanged($0)) }))
            Text(L10n.t("Text Size")).overline()

            Picker(L10n.t("Text Size"), selection: Binding(
                get: { store.textScale },
                set: { store.send(.textScaleChanged($0)) }
            )) {
                ForEach(ReaderTextScale.allCases, id: \.self) { scale in
                    Text(scale.title).tag(scale)
                }
            }
            .pickerStyle(.segmented)

            Text(L10n.t("In the beginning, God created the heavens and the earth."))
                .font(Typography.scripture(pointSize: basePointSize * store.textScale.factor))
                .foregroundStyle(Palette.ink)
                .lineSpacing(basePointSize * store.textScale.factor * Typography.scriptureLineSpacingRatio)
                .frame(maxWidth: .infinity, alignment: .leading)
                .frame(minHeight: basePointSize * ReaderTextScale.extraLarge.factor * 3.2, alignment: .top)
                .animation(Motion.standard, value: store.textScale)
                .accessibilityLabel(L10n.t("Preview: In the beginning, God created the heavens and the earth. Genesis 1:1"))
        }
        .padding(Spacing.xl)
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
        .presentationBackground(Palette.paper)
    }
}
