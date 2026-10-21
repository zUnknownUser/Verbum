import DesignSystem
import Models
import SwiftUI

/// Editorial content uses the existing paper, typography and disclosure pattern.
/// Expansion is held by EntityDetail so previews and pushed links preserve it.
struct ThemeStudyView: View {
    let study: ThemeStudy
    let sources: [SourceReference]
    let expandedSections: Set<String>
    let setExpanded: (String, Bool) -> Void
    let openPassage: (PassageReference) -> Void
    let openEntity: (BibleEntity) -> Void

    private func copy(_ en: String, _ pt: String) -> String { BookLanguage.current == .portuguese ? pt : en }
    private func expanded(_ id: String) -> Binding<Bool> {
        Binding(get: { expandedSections.contains(id) }, set: { setExpanded(id, $0) })
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.lg) {
            Text(copy("Theme study", "Estudo do tema")).overline(color: Palette.accent)
            Text(copy("Editorial reading guide · Consult the cited passages.", "Guia editorial de leitura · Consulte as passagens citadas."))
                .font(Typography.caption).foregroundStyle(Palette.inkSecondary)
            Text(study.overview.text).font(Typography.subheadline).textSelection(.enabled)
            ForEach(study.overview.references.filter { reference in
                !study.readings.contains { $0.reference == reference }
            }, id: \.self) { passage($0) }

            Text(copy("Guided readings", "Leituras comentadas")).overline()
            ForEach(Array(study.readings.enumerated()), id: \.element.id) { index, reading in
                DisclosureGroup(isExpanded: expanded("reading.\(reading.id)")) {
                    VStack(alignment: .leading, spacing: Spacing.md) {
                        passage(reading.reference)
                        note(copy("Context", "Contexto"), reading.context)
                        note(copy("What to observe", "O que observar"), reading.observation)
                        note(copy("Why it matters", "Por que importa"), reading.significance)
                        note(copy("Question for study", "Pergunta para estudo"), reading.question)
                    }.padding(.top, Spacing.sm)
                } label: {
                    VStack(alignment: .leading, spacing: Spacing.xs) {
                        Text("\(index + 1). \(reading.title)").font(Typography.editorialHeadline)
                        Text(reading.reference.formatted).font(Typography.caption).foregroundStyle(Palette.inkSecondary)
                    }.padding(.vertical, Spacing.xs)
                }
            }

            Text(copy("Compare the passages", "Compare as passagens")).overline()
            ForEach(study.comparisons) { comparison in
                DisclosureGroup(comparison.title, isExpanded: expanded("comparison.\(comparison.id)")) {
                    VStack(alignment: .leading, spacing: Spacing.md) {
                        ForEach(comparison.references, id: \.self) { passage($0) }
                        note(copy("Connection", "Relação entre os textos"), comparison.explanation)
                        note(copy("Differences in context", "Diferenças de contexto"), comparison.difference)
                    }.padding(.top, Spacing.sm)
                }
            }

            DisclosureGroup(copy("Connections explained", "Conexões explicadas"), isExpanded: expanded("connections")) {
                VStack(alignment: .leading, spacing: Spacing.lg) {
                    ForEach(study.connections, id: \.entity.id) { connection in
                        VStack(alignment: .leading, spacing: Spacing.sm) {
                            Button { openEntity(connection.entity) } label: {
                                HStack {
                                    Text(connection.entity.name).font(Typography.editorialHeadline)
                                    Spacer()
                                    Image(systemName: "chevron.right").font(Typography.caption)
                                }
                                .frame(minHeight: 44)
                                .contentShape(Rectangle())
                            }.buttonStyle(.plain).foregroundStyle(Palette.accent)
                            Text(connection.explanation).font(Typography.subheadline).textSelection(.enabled)
                            passage(connection.reference)
                        }
                    }
                }.padding(.top, Spacing.sm)
            }

            DisclosureGroup(copy("Study sources", "Fontes deste estudo"), isExpanded: expanded("sources")) {
                VStack(alignment: .leading, spacing: Spacing.md) {
                    ForEach(sources) { source in
                        if let url = source.webURL {
                            Link(source.citation, destination: url).font(Typography.footnote)
                        } else {
                            Text(source.citation).font(Typography.footnote)
                        }
                    }
                }.padding(.top, Spacing.sm)
            }
        }
        .font(Typography.subheadline)
        .foregroundStyle(Palette.ink)
        .tint(Palette.accent)
    }

    private func note(_ title: String, _ text: String) -> some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text(title).font(Typography.caption.weight(.semibold)).foregroundStyle(Palette.inkSecondary)
            Text(text).font(Typography.subheadline).textSelection(.enabled)
        }
    }

    private func passage(_ reference: PassageReference) -> some View {
        Button { openPassage(reference) } label: {
            Label(reference.formatted, systemImage: "book")
                .font(Typography.footnote)
                .frame(minHeight: 44, alignment: .leading)
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
        }.buttonStyle(.plain).foregroundStyle(Palette.accent)
    }
}
