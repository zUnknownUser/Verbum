import Testing
@testable import Models

@Suite struct SourceReferenceTests {
    @Test func opensOnlyCompleteHTTPSReferences() {
        for address in ["https://worldenglish.bible", "https://example.org/passage?search=Gen+1%3A1-3", "HTTPS://example.org/source"] {
            #expect(SourceReference(id: "source", citation: "Citation", url: address).webURL != nil)
        }
        let invalid: [String?] = [nil, "", "https://", "https:///missing-host", "http://example.org", "javascript:alert(1)", "https://user:password@example.org", "https://example.org/a b"]
        for address in invalid {
            #expect(SourceReference(id: "source", citation: "Citation", url: address).webURL == nil)
        }
    }
}
