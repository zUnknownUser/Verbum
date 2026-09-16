package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.clients.AccountClient
import com.nexussoft.verbum.common.arch.TestStore
import com.nexussoft.verbum.feature.scripture.AccountFeature.Action
import com.nexussoft.verbum.feature.scripture.AccountFeature.Operation
import com.nexussoft.verbum.feature.scripture.AccountFeature.Page
import com.nexussoft.verbum.feature.scripture.AccountFeature.State
import com.nexussoft.verbum.models.AccountException
import com.nexussoft.verbum.models.AccountFailure
import com.nexussoft.verbum.models.AuthSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountFeatureTest {
    private val guest = AuthSession("guest", null, true, false)
    @Test fun optionalEntryAndSecretsClearedOnDismiss() = runTest {
        val store = TestStore(State(), AccountFeature.reducer(StubAccountClient()))
        store.send(Action.Open) { it.copy(presented = true) }
        store.send(Action.Navigate(Page.SIGN_IN)) { it.copy(page = Page.SIGN_IN) }
        store.send(Action.Password("not-a-real-password")) { it.copy(password = "not-a-real-password") }
        store.send(Action.Close) { it.copy(presented = false, password = "") }
        store.finish()
    }
    @Test fun invalidFormNeverCallsFirebase() = runTest {
        val store = TestStore(State(), AccountFeature.reducer(StubAccountClient()))
        store.send(Action.Perform(Operation.REGISTER)) { it.copy(failure = AccountFailure.invalidEmail) }
        store.finish()
    }
    @Test fun anonymousSuccessDismissesWithoutForcingRegistration() = runTest {
        val client = object : StubAccountClient() { override suspend fun anonymous() = guest }
        val store = TestStore(State(), AccountFeature.reducer(client))
        store.send(Action.Open) { it.copy(presented = true) }
        store.send(Action.Perform(Operation.ANONYMOUS)) { it.copy(busy = true) }
        store.receive(Action.Completed(Operation.ANONYMOUS, guest)) { it.copy(busy = false, session = guest, presented = false) }
        store.finish()
    }
    @Test fun busyPreventsDismissalAndDuplicateRequests() = runTest {
        val store = TestStore(State(busy = true, presented = true), AccountFeature.reducer(StubAccountClient()))
        store.send(Action.Perform(Operation.ANONYMOUS))
        store.send(Action.Close)
        store.send(Action.Navigate(Page.REGISTER))
        store.finish()
    }
    @Test fun registrationKeepsGuestIdentityAndClearsSecrets() = runTest {
        val registered = AuthSession(guest.id, "reader@example.com", false, false)
        val client = object : StubAccountClient() {
            override suspend fun register(email: String, password: String): AuthSession {
                assertEquals("reader@example.com", email); assertEquals("password123", password)
                return registered
            }
        }
        val store = TestStore(State(session = guest, email = " reader@example.com ", password = "password123", confirmation = "password123"), AccountFeature.reducer(client))
        store.send(Action.Perform(Operation.REGISTER)) { it.copy(busy = true) }
        store.receive(Action.Completed(Operation.REGISTER, registered)) { it.copy(busy = false, session = registered, page = Page.PROFILE, password = "", confirmation = "") }
        store.finish()
    }
    @Test fun recoveryHasNeutralResponse() = runTest {
        val client = object : StubAccountClient() { override suspend fun resetPassword(email: String) {} }
        val store = TestStore(State(email = "reader@example.com", page = Page.RESET), AccountFeature.reducer(client))
        store.send(Action.Perform(Operation.RESET)) { it.copy(busy = true) }
        store.receive(Action.Completed(Operation.RESET, null)) { it.copy(busy = false, notice = "resetSent") }
        store.finish()
    }
    @Test fun failureIsSafeAndClearsPassword() = runTest {
        val client = object : StubAccountClient() {
            override suspend fun signIn(email: String, password: String): AuthSession = throw AccountException(AccountFailure.network)
        }
        val store = TestStore(State(email = "reader@example.com", password = "password123"), AccountFeature.reducer(client))
        store.send(Action.Perform(Operation.SIGN_IN)) { it.copy(busy = true) }
        store.receive(Action.Failed(AccountFailure.network)) { it.copy(busy = false, failure = AccountFailure.network, password = "") }
        store.finish()
    }
    @Test fun restoredSessionOpensAccountAndDeletionClearsIt() = runTest {
        val client = object : StubAccountClient() { override suspend fun deleteAccount(password: String) {} }
        val store = TestStore(State(), AccountFeature.reducer(client))
        store.send(Action.SessionChanged(guest)) { it.copy(session = guest) }
        store.send(Action.Open) { it.copy(presented = true, page = Page.PROFILE) }
        store.send(Action.Navigate(Page.DELETE)) { it.copy(page = Page.DELETE) }
        store.send(Action.Perform(Operation.DELETE)) { it.copy(busy = true) }
        store.receive(Action.Completed(Operation.DELETE, null)) { it.copy(busy = false, session = null, page = Page.PROFILE, notice = "deleted") }
        store.finish()
    }
    @Test fun passwordResetUsesSessionEmail() = runTest {
        val session = AuthSession("reader", "reader@example.com", false, true, providers = listOf("password"))
        val client = object : StubAccountClient() {
            override suspend fun resetPassword(email: String) { assertEquals("reader@example.com", email) }
        }
        val store = TestStore(State(session = session, email = "unrelated@example.com"), AccountFeature.reducer(client))
        store.send(Action.Perform(Operation.RESET_CURRENT_PASSWORD)) { it.copy(busy = true) }
        store.receive(Action.Completed(Operation.RESET_CURRENT_PASSWORD, null)) { it.copy(busy = false, notice = "resetSent") }
        store.finish()
    }
    @Test fun emailChangeWaitsForVerificationAndClearsPassword() = runTest {
        val session = AuthSession("reader", "old@example.com", false, true, providers = listOf("password"))
        val client = object : StubAccountClient() {
            override suspend fun changeEmail(email: String, password: String) {
                assertEquals("new@example.com", email); assertEquals("password123", password)
            }
        }
        val store = TestStore(State(session = session, page = Page.CHANGE_EMAIL, email = " new@example.com ", password = "password123"), AccountFeature.reducer(client))
        store.send(Action.Perform(Operation.CHANGE_EMAIL)) { it.copy(busy = true) }
        store.receive(Action.Completed(Operation.CHANGE_EMAIL, null)) { it.copy(busy = false, password = "", page = Page.ACCOUNT, notice = "emailChangeSent") }
        store.finish()
    }
    @Test fun providerWithoutPasswordCannotResetIt() = runTest {
        val session = AuthSession("reader", "reader@example.com", false, true, providers = listOf("apple.com"))
        val store = TestStore(State(session = session), AccountFeature.reducer(StubAccountClient()))
        store.send(Action.Perform(Operation.RESET_CURRENT_PASSWORD)) { it.copy(failure = AccountFailure.credentials) }
        store.finish()
    }
    @Test fun nameIsValidatedAndTrimmed() = runTest {
        val renamed = AuthSession("reader", "reader@example.com", false, true, displayName = "Lucas Amorim")
        val client = object : StubAccountClient() {
            override suspend fun updateName(name: String): AuthSession { assertEquals("Lucas Amorim", name); return renamed }
        }
        val store = TestStore(State(displayName = "  "), AccountFeature.reducer(client))
        store.send(Action.Perform(Operation.UPDATE_NAME)) { it.copy(failure = AccountFailure.nameRequired) }
        store.send(Action.DisplayName(" Lucas Amorim ")) { it.copy(displayName = " Lucas Amorim ", failure = null) }
        store.send(Action.Perform(Operation.UPDATE_NAME)) { it.copy(busy = true) }
        store.receive(Action.Completed(Operation.UPDATE_NAME, renamed)) { it.copy(busy = false, session = renamed, page = Page.ACCOUNT, notice = "nameUpdated") }
        store.finish()
    }
}
private open class StubAccountClient : AccountClient {
    override fun sessions(): Flow<AuthSession?> = error("not stubbed")
    override suspend fun signIn(email: String, password: String): AuthSession = error("not stubbed")
    override suspend fun register(email: String, password: String): AuthSession = error("not stubbed")
    override suspend fun anonymous(): AuthSession = error("not stubbed")
    override suspend fun resetPassword(email: String): Unit = error("not stubbed")
    override suspend fun sendVerification(): Unit = error("not stubbed")
    override suspend fun refresh(): AuthSession? = error("not stubbed")
    override suspend fun updateName(name: String): AuthSession = error("not stubbed")
    override suspend fun changeEmail(email: String, password: String): Unit = error("not stubbed")
    override suspend fun signOut(): Unit = error("not stubbed")
    override suspend fun deleteAccount(password: String): Unit = error("not stubbed")
}
