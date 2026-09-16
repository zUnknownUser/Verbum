package com.nexussoft.verbum.models

data class AuthSession(val id: String, val email: String?, val isAnonymous: Boolean, val isEmailVerified: Boolean,
    val displayName: String? = null, val createdAt: Long? = null, val providers: List<String> = emptyList(),
) {
    val hasPassword: Boolean get() = "password" in providers
    val initials: String? get() {
        val parts = displayName?.trim()?.split(Regex("\\s+"))?.filter { it.isNotEmpty() }.orEmpty()
        return if (parts.isNotEmpty()) (parts.first().take(1) + if (parts.size > 1) parts.last().take(1) else "").uppercase()
            else email?.take(1)?.uppercase()
    }
}

enum class AccountFailure {
    invalidEmail, nameRequired, passwordRequired, passwordTooShort, passwordMismatch, credentials, emailInUse,
    network, tooManyRequests, disabled, configuration, recentLoginRequired, unexpected,
}
class AccountException(val failure: AccountFailure) : Exception(failure.name)

object AccountValidation {
    private val emailPattern = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    fun email(value: String) = value.trim()
    fun validate(email: String, password: String, confirmation: String? = null, reset: Boolean = false): AccountFailure? {
        val address = email(email)
        if (address.length > 254 || !emailPattern.matches(address)) return AccountFailure.invalidEmail
        if (reset) return null
        if (password.isEmpty()) return AccountFailure.passwordRequired
        if (confirmation != null) {
            if (password.codePointCount(0, password.length) < 8) return AccountFailure.passwordTooShort
            if (password != confirmation) return AccountFailure.passwordMismatch
        }
        return null
    }
}
