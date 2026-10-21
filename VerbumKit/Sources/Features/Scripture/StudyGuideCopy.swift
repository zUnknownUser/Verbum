import Models

/// Uses the same app language as Scripture and canonical content.
enum StudyGuideCopy {
    private static var portuguese: Bool { BookLanguage.current == .portuguese }
    static var openGuide: String { portuguese ? "Estudar este capítulo" : "Study this chapter" }
    static var introduction: String { portuguese ? "Explore o contexto e consulte as referências sem perder seu lugar na leitura." : "Explore the context and consult references without losing your place in the reading." }
    static var chapterScope: String { portuguese ? "Conexões documentadas para este capítulo. Consulte as fontes e as passagens para aprofundar o estudo." : "Documented connections for this chapter. Consult the sources and passages to study further." }
    static var back: String { portuguese ? "Voltar ao estudo" : "Back to study" }
    static var openReader: String { portuguese ? "Abrir na leitura" : "Open in reader" }
    static var furtherReading: String { portuguese ? "Outras leituras" : "Further reading" }
    static var showMore: String { portuguese ? "Mostrar mais" : "Show more" }
    static func count(_ visible: Int, of total: Int) -> String {
        portuguese ? "\(visible) de \(total)" : "\(visible) of \(total)"
    }
    static func category(_ type: BibleEntityType) -> String {
        switch type {
        case .person: portuguese ? "Pessoas" : "People"
        case .place: portuguese ? "Lugares" : "Places"
        case .event: portuguese ? "Eventos" : "Events"
        case .theme: portuguese ? "Temas" : "Themes"
        case .passage: portuguese ? "Passagens" : "Passages"
        case .book: portuguese ? "Livros" : "Books"
        case .prophecy: portuguese ? "Profecias" : "Prophecies"
        case .originalTerm: portuguese ? "Termos originais" : "Original terms"
        case .historicalPeriod: portuguese ? "Períodos históricos" : "Historical periods"
        }
    }
}
