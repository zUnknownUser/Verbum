import DesignSystem
import Models
import SwiftUI

/// Shared by the full context page and the reader's existing study sheet.
struct ChapterStudyView: View {
    let study: ChapterStudy
    let sources: [SourceReference]
    let openPassage: (PassageReference) -> Void

    private var portuguese: Bool { BookLanguage.current == .portuguese }
    private func copy(_ en: String, _ pt: String) -> String { portuguese ? pt : en }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.lg) {
            Text(study.title).font(Typography.editorialHeadline).accessibilityAddTraits(.isHeader)
            Text(copy("Editorial reading guide · Consult the cited passages.", "Guia editorial de leitura · Consulte as passagens citadas."))
                .font(Typography.caption).foregroundStyle(Palette.inkSecondary)
            note(study.summary)
            DisclosureGroup(copy("People and roles", "Participantes e funções")) { note(study.participants) }
            DisclosureGroup(copy("Setting in the narrative", "Cenário do relato")) { note(study.setting) }
            DisclosureGroup(copy("Literary context", "Contexto literário")) { note(study.literaryContext) }
            DisclosureGroup(copy("Chapter structure", "Estrutura do capítulo")) {
                VStack(alignment: .leading, spacing: Spacing.md) {
                    ForEach(study.outline, id: \.reference) { part in
                        Text(part.title).font(Typography.subheadline)
                        passage(part.reference)
                    }
                }.padding(.top, Spacing.sm)
            }
            DisclosureGroup(copy("Connections explained", "Conexões explicadas")) {
                VStack(alignment: .leading, spacing: Spacing.md) {
                    ForEach(study.connections, id: \.reference) { connection in
                        passage(connection.reference)
                        Text(connection.explanation).font(Typography.subheadline).textSelection(.enabled)
                    }
                }.padding(.top, Spacing.sm)
            }
            DisclosureGroup(copy("Study sources", "Fontes deste estudo")) {
                VStack(alignment: .leading, spacing: Spacing.md) {
                    ForEach(sources.filter { study.sourceReferenceIds.contains($0.id) }) { source in
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
        .tint(Palette.accent)
    }

    private func note(_ note: StudyNote) -> some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Text(note.text).font(Typography.subheadline).textSelection(.enabled)
            ForEach(note.references, id: \.self) { passage($0) }
        }.padding(.top, Spacing.sm)
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
