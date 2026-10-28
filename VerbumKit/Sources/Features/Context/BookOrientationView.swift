import DesignSystem
import Models
import SwiftUI

/// Reuses the context surfaces; Features alone owns passage navigation.
struct BookOrientationView: View {
    let orientation: BookOrientation
    let bookId: String
    let sources: [SourceReference]
    let openPassage: (PassageReference) -> Void
    private var portuguese: Bool { BookLanguage.current == .portuguese }
    private func copy(_ en: String, _ pt: String) -> String { portuguese ? pt : en }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            Text(copy("About this book", "Orientação do livro"))
                .font(Typography.editorialHeadline).accessibilityAddTraits(.isHeader)
            Text(copy("An editorial introduction to the whole book, not a commentary on this chapter.", "Uma introdução editorial ao livro inteiro, não um comentário deste capítulo."))
                .font(Typography.caption).foregroundStyle(Palette.inkSecondary)
            Text(orientation.genre).font(Typography.footnote).foregroundStyle(Palette.inkSecondary)
            Text(orientation.context).textSelection(.enabled)
            DisclosureGroup(copy("Reading focus", "Foco da leitura")) {
                Text(orientation.purpose).textSelection(.enabled).padding(.top, Spacing.sm)
            }
            DisclosureGroup(copy("Book structure", "Estrutura do livro")) {
                VStack(alignment: .leading, spacing: Spacing.sm) {
                    ForEach(orientation.outline, id: \.chapterStart) { part in
                        Button { openPassage(PassageReference(bookId: bookId, chapter: part.chapterStart)) } label: {
                            VStack(alignment: .leading, spacing: Spacing.xs) {
                                Text(part.title)
                                Text(copy("Chapters", "Capítulos") + " \(part.chapterStart)–\(part.chapterEnd)")
                                    .font(Typography.footnote)
                            }.frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                        }.buttonStyle(.plain).foregroundStyle(Palette.accent)
                    }
                }.padding(.top, Spacing.sm)
            }
            DisclosureGroup(copy("Where to begin", "Por onde começar")) {
                VStack(alignment: .leading, spacing: Spacing.sm) {
                    ForEach(orientation.readings, id: \.self) { reference in
                        Button { openPassage(reference) } label: {
                            Label(reference.formatted, systemImage: "book")
                                .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                        }.buttonStyle(.plain).foregroundStyle(Palette.accent)
                    }
                }.padding(.top, Spacing.sm)
            }
            DisclosureGroup(copy("Sources for this introduction", "Fontes desta orientação")) {
                VStack(alignment: .leading, spacing: Spacing.sm) {
                    Text(copy("Original editorial synthesis of the cited biblical book. External source: English (WEB). Reading links use your selected Bible translation.", "Síntese editorial original do livro bíblico citado. Fonte externa: inglês (WEB). As leituras usam sua tradução bíblica selecionada."))
                        .font(Typography.caption)
                    ForEach(sources.filter { orientation.sourceReferenceIds.contains($0.id) }) { source in
                        if let url = source.webURL { Link(source.citation, destination: url).font(Typography.footnote) }
                        else { Text(source.citation).font(Typography.footnote) }
                    }
                }.padding(.top, Spacing.sm)
            }
        }.font(Typography.subheadline).tint(Palette.accent)
    }
}
