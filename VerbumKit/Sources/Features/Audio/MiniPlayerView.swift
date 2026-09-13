import ComposableArchitecture
import DesignSystem
import Models
import SwiftUI

/// Lives in the tab bar's accessory slot (iOS 26), so it follows the listener
/// everywhere and collapses into the bar as they scroll. Tap the title to go
/// back to the chapter.
struct MiniPlayerView: View {
    let store: StoreOf<AudioPlayerFeature>

    var body: some View {
        HStack(spacing: Spacing.md) {
            Button { store.send(.chapterTapped) } label: {
                VStack(alignment: .leading, spacing: 1) {
                    Text(store.reference?.formatted ?? "")
                        .font(Typography.navigationSerif)
                        .foregroundStyle(Palette.ink)
                        .lineLimit(1)
                    Text(subtitle)
                        .font(Typography.caption)
                        .foregroundStyle(Palette.inkSecondary)
                        .lineLimit(1)
                        .monospacedDigit()
                }
            }
            .buttonStyle(.plain)
            .accessibilityHint(L10n.t("Opens the chapter"))

            Spacer(minLength: Spacing.sm)

            Button { store.send(.skipBackward) } label: {
                Image(systemName: "gobackward.15").font(.system(size: 20, weight: .regular))
            }
            .accessibilityLabel(L10n.t("Back 15 seconds"))
            .disabled(store.isLoading || store.failed)

            Button {
                if store.failed, let reference = store.reference { store.send(.play(reference)) }
                else { store.send(.togglePlayPause) }
            } label: {
                if store.isLoading {
                    ProgressView().tint(Palette.ink).frame(width: 28, height: 28)
                } else {
                    Image(systemName: store.isPlaying ? "pause.fill" : "play.fill")
                        .font(.system(size: 22, weight: .semibold))
                        .frame(width: 28, height: 28)
                }
            }
            .accessibilityLabel(store.isPlaying ? L10n.t("Pause") : L10n.t("Play"))
            .accessibilityHint(store.failed ? L10n.t("Try Again") : "")
            .disabled(store.isLoading)

            Button { store.send(.skipForward) } label: {
                Image(systemName: "goforward.15").font(.system(size: 20, weight: .regular))
            }
            .accessibilityLabel(L10n.t("Forward 15 seconds"))
            .disabled(store.isLoading || store.failed)

            Button { store.send(.stopTapped) } label: {
                Image(systemName: "xmark").font(.system(size: 14, weight: .semibold))
            }
            .accessibilityLabel(L10n.t("Stop listening"))
        }
        .tint(Palette.ink)
        .padding(.horizontal, Spacing.lg)
        .overlay(alignment: .top) {
            GeometryReader { proxy in
                Rectangle()
                    .fill(Palette.accent)
                    .frame(width: proxy.size.width * store.progress, height: 2)
            }
            .frame(height: 2)
            .accessibilityHidden(true)
        }
    }

    private var subtitle: String {
        if store.failed { return L10n.t("No recording for this chapter") }
        let time = "\(format(store.currentTime)) / \(format(store.duration))"
        if let audio = store.audio, let narrator = store.narrator {
            return "\(L10n.t("Audio in English")) · \(audio.translationId) · \(narrator.name) · \(time)"
        }
        return L10n.t("Audio in English")
    }

    private func format(_ seconds: TimeInterval) -> String {
        let total = Int(seconds.rounded(.down))
        return String(format: "%d:%02d", total / 60, total % 60)
    }
}
