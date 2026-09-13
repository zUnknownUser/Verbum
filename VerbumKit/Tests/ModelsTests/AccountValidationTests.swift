import Models
import Testing

struct AccountValidationTests {
    @Test func emailAndPasswordRules() {
        #expect(AccountValidation.email("  reader@example.com \n") == "reader@example.com")
        #expect(AccountValidation.validate(email: "reader", password: "12345678", confirmation: nil) == .invalidEmail)
        #expect(AccountValidation.validate(email: "reader@example.com", password: "", confirmation: nil) == .passwordRequired)
        #expect(AccountValidation.validate(email: "reader@example.com", password: "1234567", confirmation: "1234567") == .passwordTooShort)
        #expect(AccountValidation.validate(email: "reader@example.com", password: "12345678", confirmation: "87654321") == .passwordMismatch)
        // Existing accounts may have shorter passwords; only registration applies the new minimum.
        #expect(AccountValidation.validate(email: "reader@example.com", password: "123456", confirmation: nil) == nil)
        #expect(AccountValidation.validate(email: "reader@example.com", password: "", confirmation: nil, reset: true) == nil)
    }
}
