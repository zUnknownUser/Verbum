import Clients
import ComposableArchitecture
import DesignSystem
import Models
import SwiftUI

/// The conversation sheet: what it is about, what is being said, the passages
/// it rests on, and two controls — mute and end. Calm on purpose: no waveform
/// theatre, the state is a line of text.
public struct VoiceView: View {
    let store: StoreOf<VoiceFeature>
    @Environment(\.openURL) private var openURL

    public init(store: StoreOf<VoiceFeature>) { self.store = store }

    public var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            header
            Divider().overlay(Palette.rule)
            content
            Divider().overlay(Palette.rule)
            controls
        }
        .background(Palette.paper.ignoresSafeArea())
        .tint(Palette.accent)
        .task { await store.send(.task).finish() }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text(L10n.t("Talking about")).overline(color: Palette.accent)
            Text(store.context.title)
                .font(Typography.editorialHeadline)
                .foregroundStyle(Palette.ink)
                .lineLimit(2)
            Label(status, systemImage: statusSymbol)
                .font(Typography.footnote)
                .foregroundStyle(Palette.inkSecondary)
                .padding(.top, Spacing.xs)
                .accessibilityLabel(status)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, Spacing.readingMargin)
        .padding(.vertical, Spacing.lg)
    }

    private var status: String {
        switch store.phase {
        case .idle, .connecting: L10n.t("Connecting…")
        case .listening: store.isMuted ? L10n.t("Muted") : (store.isUserSpeaking ? L10n.t("Hearing you") : L10n.t("Listening"))
        case .speaking: L10n.t("Speaking")
        case .thinking: L10n.t("Looking it up in Scripture")
        case .ended: L10n.t("Ended")
        case .failed: L10n.t("Not connected")
        }
    }

    private var statusSymbol: String {
        switch store.phase {
        case .idle, .connecting: "ellipsis"
        case .listening: store.isMuted ? "mic.slash" : "mic"
        case .speaking: "speaker.wave.2"
        case .thinking: "book"
        case .ended: "checkmark"
        case .failed: "exclamationmark.circle"
        }
    }

    @ViewBuilder
    private var content: some View {
        if case .failed(let error) = store.phase {
            failure(error)
        } else {
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: Spacing.lg) {
                        if store.lines.isEmpty, store.partial.isEmpty {
                            Text(L10n.t("Ask anything about this page — what a word means, who someone is, why it matters. The companion answers from Scripture and says which passages it is drawing on."))
                                .font(Typography.subheadline)
                                .foregroundStyle(Palette.inkSecondary)
                        }
                        ForEach(store.lines) { line in
                            lineView(line)
                        }
                        if !store.partial.isEmpty {
                            lineView(VoiceFeature.Line(id: -1, role: .companion, text: store.partial))
                        }
                        Color.clear.frame(height: 1).id("end")
                    }
                    .padding(.horizontal, Spacing.readingMargin)
                    .padding(.vertical, Spacing.lg)
                }
                .onChange(of: store.lines.count) { _, _ in withAnimation { proxy.scrollTo("end") } }
                .onChange(of: store.partial) { _, _ in proxy.scrollTo("end") }
            }
            if !store.passages.isEmpty {
                passages
            }
        }
    }

    private func lineView(_ line: VoiceFeature.Line) -> some View {
        VStack(alignment: .leading, spacing: Spacing.xxs) {
            Text(line.role == .user ? L10n.t("You") : L10n.t("Companion"))
                .overline()
            Text(line.text)
                .font(line.role == .companion ? Typography.scripture : Typography.body)
                .foregroundStyle(Palette.ink)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var passages: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Text(L10n.t("Passages mentioned")).overline()
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Spacing.sm) {
                    ForEach(store.passages, id: \.self) { reference in
                        Button { store.send(.passageTapped(reference)) } label: {
                            Label(reference.formatted, systemImage: "book")
                                .font(Typography.footnote.weight(.semibold))
                                .padding(.horizontal, Spacing.md)
                                .padding(.vertical, Spacing.sm)
                                .background(Palette.fillSecondary, in: Capsule())
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
        .padding(.horizontal, Spacing.readingMargin)
        .padding(.bottom, Spacing.md)
    }

    private func failure(_ error: VoiceError) -> some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            switch error {
            case .unavailable:
                Text(L10n.t("Voice isn't available on this server yet")).font(Typography.editorialHeadline)
                Text(L10n.t("Ask Scripture in writing still works from Search.")).font(Typography.subheadline)
            case .microphoneDenied:
                Text(L10n.t("Verbum needs the microphone to talk")).font(Typography.editorialHeadline)
                Text(L10n.t("Allow the microphone in Settings, then come back.")).font(Typography.subheadline)
                Button(L10n.t("Open Settings")) {
                    if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
                }
                .font(Typography.subheadline.weight(.semibold))
            case .networkUnavailable:
                Text(L10n.t("Verbum can't be reached")).font(Typography.editorialHeadline)
                Text(L10n.t("Talking needs a connection.")).font(Typography.subheadline)
                Button(L10n.t("Try Again")) { store.send(.retryTapped) }.font(Typography.subheadline.weight(.semibold))
            case .failed:
                Text(L10n.t("The conversation dropped")).font(Typography.editorialHeadline)
                Button(L10n.t("Try Again")) { store.send(.retryTapped) }.font(Typography.subheadline.weight(.semibold))
            }
        }
        .foregroundStyle(Palette.ink)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .padding(.horizontal, Spacing.readingMargin)
        .padding(.vertical, Spacing.lg)
    }

    private var controls: some View {
        HStack {
            Button {
                store.send(.muteToggled)
            } label: {
                Label(store.isMuted ? L10n.t("Unmute") : L10n.t("Mute"), systemImage: store.isMuted ? "mic.slash.fill" : "mic.fill")
                    .font(Typography.subheadline.weight(.semibold))
            }
            .disabled(!(store.phase == .listening || store.phase == .speaking || store.phase == .thinking))
            Spacer()
            Button(role: .cancel) {
                store.send(.endTapped)
            } label: {
                Text(store.phase == .ended || store.phase.isFailed ? L10n.t("Close") : L10n.t("End"))
                    .font(Typography.subheadline.weight(.semibold))
            }
        }
        .padding(.horizontal, Spacing.readingMargin)
        .padding(.vertical, Spacing.md)
    }
}
