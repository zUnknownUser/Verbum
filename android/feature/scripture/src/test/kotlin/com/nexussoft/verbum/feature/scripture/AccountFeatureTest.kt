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
        store.receive(Action.Completed(Operation.REGISTER, registered)) { it.copy(busy = false, session = registered, page = Page.ACCOUNT, password = "", confirmation = "") }
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
        store.send(Action.Open) { it.copy(presented = true, page = Page.ACCOUNT) }
        store.send(Action.Navigate(Page.DELETE)) { it.copy(page = Page.DELETE) }
        store.send(Action.Perform(Operation.DELETE)) { it.copy(busy = true) }
        store.receive(Action.Completed(Operation.DELETE, null)) { it.copy(busy = false, session = null, page = Page.WELCOME, notice = "deleted") }
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
    override suspend fun signOut(): Unit = error("not stubbed")
    override suspend fun deleteAccount(password: String): Unit = error("not stubbed")
}
