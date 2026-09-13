package com.nexussoft.verbum.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AccountValidationTest {
    @Test fun emailAndPasswordRules() {
        assertEquals("reader@example.com", AccountValidation.email(" reader@example.com \n"))
        assertEquals(AccountFailure.invalidEmail, AccountValidation.validate("reader", "12345678"))
        assertEquals(AccountFailure.passwordRequired, AccountValidation.validate("reader@example.com", ""))
        assertEquals(AccountFailure.passwordTooShort, AccountValidation.validate("reader@example.com", "1234567", "1234567"))
        assertEquals(AccountFailure.passwordMismatch, AccountValidation.validate("reader@example.com", "12345678", "87654321"))
        assertNull(AccountValidation.validate("reader@example.com", "123456"))
        assertNull(AccountValidation.validate("reader@example.com", "", reset = true))
    }
}
