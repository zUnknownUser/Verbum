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
        HStack(spacing: Spacing.xs) {
            Button { store.send(.chapterTapped) } label: {
                VStack(alignment: .leading, spacing: Spacing.xxs) {
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
            .frame(minHeight: 44)
            .accessibilityHint(L10n.t("Opens the chapter"))

            Spacer(minLength: Spacing.sm)

            Button { store.send(.skipBackward) } label: {
                Image(systemName: "gobackward.15").font(.system(size: 19, weight: .medium))
                    .frame(width: 44, height: 44)
            }
            .accessibilityLabel(L10n.t("Back 15 seconds"))
            .disabled(store.isLoading || store.failed)

            Button {
                if store.failed { store.send(.retryTapped) }
                else { store.send(.togglePlayPause) }
            } label: {
                if store.isLoading {
                    ProgressView().tint(Palette.ink).frame(width: 44, height: 44)
                } else {
                    Image(systemName: store.failed ? "arrow.clockwise" : (store.isPlaying || store.isBuffering ? "pause.fill" : "play.fill"))
                        .font(.system(size: 22, weight: .semibold))
                        .frame(width: 44, height: 44)
                }
            }
            .foregroundStyle(Palette.accent)
            .background(Palette.accentWash, in: Circle())
            .accessibilityLabel(store.failed ? L10n.t("Retry audio") : (store.isPlaying || store.isBuffering ? L10n.t("Pause") : L10n.t("Play")))
            .accessibilityHint(store.failed ? L10n.t("Try Again") : "")
            .disabled(store.isLoading)

            Button { store.send(.skipForward) } label: {
                Image(systemName: "goforward.15").font(.system(size: 19, weight: .medium))
                    .frame(width: 44, height: 44)
            }
            .accessibilityLabel(L10n.t("Forward 15 seconds"))
            .disabled(store.isLoading || store.failed)

            Button { store.send(.stopTapped) } label: {
                Image(systemName: "xmark").font(.system(size: 13, weight: .semibold))
                    .frame(width: 44, height: 44)
            }
            .accessibilityLabel(L10n.t("Stop listening"))
        }
        .tint(Palette.ink)
        .padding(.horizontal, Spacing.md)
        .padding(.vertical, Spacing.xs)
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
        if store.failed {
            switch store.failure {
            case .unavailable: return L10n.t("No recording for this chapter")
            case .restricted: return L10n.t("Audio temporarily limited. Try again later.")
            case .preparation: return L10n.t("Could not prepare audio. Tap to retry.")
            case .playback, .none: return L10n.t("Playback interrupted. Tap to resume.")
            }
        }
        if store.isLoading { return L10n.t(store.audio == nil ? "Preparing chapter…" : "Loading audio…") }
        if store.isBuffering { return L10n.t("Waiting for audio…") }
        let time = "\(format(store.currentTime)) / \(format(store.duration))"
        if let audio = store.audio, let narrator = store.narrator {
            // Synthesised reading of the translation on screen, or a recording in English (labelled).
            if narrator.isSynthesised { return "\(audio.translationName) · \(time)" }
            return "\(L10n.t("Audio in English")) · \(audio.translationId) · \(narrator.name) · \(time)"
        }
        return ""
    }

    private func format(_ seconds: TimeInterval) -> String {
        let total = Int(seconds.rounded(.down))
        return String(format: "%d:%02d", total / 60, total % 60)
    }
}
