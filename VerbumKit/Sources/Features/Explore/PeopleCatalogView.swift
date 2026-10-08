import ComposableArchitecture
import DesignSystem
import Models
import SwiftUI

struct PeopleCatalogView: View {
    let store: StoreOf<EntityListFeature>
    private let alphabet = Array("ABCDEFGHIJKLMNOPQRSTUVWXYZ").map(String.init) + ["#"]

    var body: some View {
        VStack(spacing: 0) {
            ScrollView(.horizontal) {
                HStack(spacing: Spacing.sm) {
                    letterButton("", title: L10n.t("All people"))
                    ForEach(alphabet, id: \.self) { letter in letterButton(letter, title: letter) }
                }.padding(.horizontal, Spacing.readingMargin).padding(.vertical, Spacing.sm)
            }.scrollIndicators(.hidden)
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 0) {
                        Color.clear.frame(height: 1).id("catalogTop")
                        ForEach(Array(store.entities.enumerated()), id: \.element.id) { index, entity in
                            let letter = EntityCatalog.letter(entity.name)
                            if index == 0 || EntityCatalog.letter(store.entities[index - 1].name) != letter {
                                Text(letter).font(Typography.editorialHeadline).foregroundStyle(Palette.accent)
                                    .padding(.top, Spacing.xl).padding(.bottom, Spacing.sm)
                                    .accessibilityAddTraits(.isHeader)
                            }
                            Button { store.send(.entityTapped(entity)) } label: {
                                HStack(spacing: Spacing.md) {
                                    VStack(alignment: .leading, spacing: Spacing.xs) {
                                        Text(entity.name).font(Typography.navigationSerif).foregroundStyle(Palette.ink)
                                        if let summary = entity.summary, !summary.isEmpty {
                                            Text(summary).font(Typography.footnote).foregroundStyle(Palette.inkSecondary).lineLimit(2)
                                        }
                                    }.frame(maxWidth: .infinity, alignment: .leading)
                                    Image(systemName: "chevron.right").font(.caption).foregroundStyle(Palette.inkTertiary).accessibilityHidden(true)
                                }.padding(.vertical, Spacing.lg).frame(minHeight: 44).contentShape(Rectangle())
                            }.buttonStyle(.plain)
                            Divider().overlay(Palette.rule)
                        }
                        if store.isLoading {
                            ProgressView().frame(maxWidth: .infinity).padding(Spacing.xl)
                                .accessibilityLabel(L10n.t("Loading people"))
                        } else if store.failed {
                            VStack(alignment: .leading, spacing: Spacing.md) {
                                Text(L10n.t("People could not be loaded. Please try again."))
                                Button(L10n.t("Try Again")) { store.send(.retry) }.buttonStyle(.bordered)
                            }.font(Typography.subheadline).padding(.vertical, Spacing.xl)
                        } else if store.hasLoaded && store.entities.isEmpty {
                            VStack(alignment: .leading, spacing: Spacing.md) {
                                EditorialIcon("person.crop.circle.badge.questionmark")
                                Text(L10n.t("No people found")).font(Typography.editorialHeadline)
                                Text(L10n.t("Try another name or letter.")).font(Typography.subheadline).foregroundStyle(Palette.inkSecondary)
                            }.padding(.vertical, Spacing.xl)
                        } else if store.nextOffset != nil {
                            Button(L10n.t("Show more")) { store.send(.loadMore) }
                                .buttonStyle(.bordered).controlSize(.large).frame(maxWidth: .infinity).padding(.vertical, Spacing.xl)
                                .accessibilityIdentifier("people.showMore")
                        }
                    }
                    .frame(maxWidth: Spacing.readingMaxWidth, alignment: .leading).frame(maxWidth: .infinity)
                    .padding(.horizontal, Spacing.readingMargin).padding(.bottom, Spacing.xl)
                }
                .scrollDismissesKeyboard(.interactively)
                .onChange(of: store.query) { _, _ in proxy.scrollTo("catalogTop", anchor: .top) }
                .onChange(of: store.letter) { _, _ in proxy.scrollTo("catalogTop", anchor: .top) }
            }
        }
        .background(Palette.paper).foregroundStyle(Palette.ink).tint(Palette.accent)
        .navigationTitle(L10n.t("People")).navigationBarTitleDisplayMode(.inline)
        .searchable(text: Binding(get: { store.query }, set: { store.send(.queryChanged($0)) }),
                    placement: .navigationBarDrawer(displayMode: .always), prompt: L10n.t("Search people"))
    }

    private func letterButton(_ letter: String, title: String) -> some View {
        Button { store.send(.letterChanged(letter)) } label: {
            Text(title).font(Typography.footnote).padding(.horizontal, Spacing.md).frame(minWidth: 44, minHeight: 44)
                .foregroundStyle(store.letter == letter ? Palette.onForest : Palette.inkSecondary)
                .background(store.letter == letter ? Palette.forest : Palette.paperElevated, in: Capsule())
        }.buttonStyle(EditorialButtonStyle())
            .accessibilityAddTraits(store.letter == letter ? .isSelected : [])
            .accessibilityIdentifier("people.letter.\(letter.isEmpty ? "all" : letter)")
    }
}
