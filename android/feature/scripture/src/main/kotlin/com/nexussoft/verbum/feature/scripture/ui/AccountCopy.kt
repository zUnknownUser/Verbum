package com.nexussoft.verbum.feature.scripture.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nexussoft.verbum.feature.scripture.R

@Composable
internal fun accountText(key: String): String = stringResource(when (key) {
    "account" -> R.string.account_account
    "welcome" -> R.string.account_welcome
    "intro" -> R.string.account_intro
    "signIn" -> R.string.account_sign_in
    "register" -> R.string.account_register
    "anonymous" -> R.string.account_anonymous
    "later" -> R.string.account_later
    "close" -> R.string.account_close
    "back" -> R.string.account_back
    "email" -> R.string.account_email
    "password" -> R.string.account_password
    "confirm" -> R.string.account_confirm
    "show" -> R.string.account_show
    "hide" -> R.string.account_hide
    "reset" -> R.string.account_reset
    "forgot" -> R.string.account_forgot
    "sendReset" -> R.string.account_send_reset
    "signInTitle" -> R.string.account_sign_in_title
    "signInBody" -> R.string.account_sign_in_body
    "registerTitle" -> R.string.account_register_title
    "registerBody" -> R.string.account_register_body
    "resetTitle" -> R.string.account_reset_title
    "resetBody" -> R.string.account_reset_body
    "resetSent" -> R.string.account_reset_sent
    "guest" -> R.string.account_guest
    "guestBody" -> R.string.account_guest_body
    "existingGuest" -> R.string.account_existing_guest
    "accountBody" -> R.string.account_account_body
    "verified" -> R.string.account_verified
    "unverified" -> R.string.account_unverified
    "verify" -> R.string.account_verify
    "refresh" -> R.string.account_refresh
    "verificationSent" -> R.string.account_verification_sent
    "signOut" -> R.string.account_sign_out
    "signOutBody" -> R.string.account_sign_out_body
    "delete" -> R.string.account_delete
    "deleteTitle" -> R.string.account_delete_title
    "deleteBody" -> R.string.account_delete_body
    "deleteGuestBody" -> R.string.account_delete_guest_body
    "deleted" -> R.string.account_deleted
    "cancel" -> R.string.account_cancel
    "working" -> R.string.account_working
    "invalidEmail" -> R.string.account_invalid_email
    "passwordRequired" -> R.string.account_password_required
    "passwordTooShort" -> R.string.account_password_too_short
    "passwordMismatch" -> R.string.account_password_mismatch
    "credentials" -> R.string.account_credentials
    "emailInUse" -> R.string.account_email_in_use
    "network" -> R.string.account_network
    "tooManyRequests" -> R.string.account_too_many_requests
    "disabled" -> R.string.account_disabled
    "configuration" -> R.string.account_configuration
    "recentLoginRequired" -> R.string.account_recent_login_required
    "unexpected" -> R.string.account_unexpected
    else -> R.string.account_unexpected
})

