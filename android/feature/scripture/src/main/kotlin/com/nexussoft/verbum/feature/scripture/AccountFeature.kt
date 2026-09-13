package com.nexussoft.verbum.feature.scripture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexussoft.verbum.clients.AccountClient
import com.nexussoft.verbum.common.arch.Reducer
import com.nexussoft.verbum.common.arch.Store
import com.nexussoft.verbum.common.arch.only
import com.nexussoft.verbum.common.arch.with
import com.nexussoft.verbum.common.arch.runEffect
import com.nexussoft.verbum.models.AccountException
import com.nexussoft.verbum.models.AccountFailure
import com.nexussoft.verbum.models.AccountValidation
import com.nexussoft.verbum.models.AuthSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

object AccountFeature {
    enum class Page { WELCOME, SIGN_IN, REGISTER, RESET, ACCOUNT, DELETE }
    enum class Operation { SIGN_IN, REGISTER, ANONYMOUS, RESET, VERIFY, REFRESH, SIGN_OUT, DELETE }
    data class State(
        val presented: Boolean = false, val page: Page = Page.WELCOME, val session: AuthSession? = null,
        val email: String = "", val password: String = "", val confirmation: String = "",
        val busy: Boolean = false, val failure: AccountFailure? = null, val notice: String? = null,
        val verificationSent: Boolean = false,
    )
    sealed interface Action {
        data object Started : Action
        data object Open : Action
        data object Close : Action
        data class Navigate(val page: Page) : Action
        data class Email(val value: String) : Action
        data class Password(val value: String) : Action
        data class Confirmation(val value: String) : Action
        data class SessionChanged(val session: AuthSession?) : Action
        data class Perform(val operation: Operation) : Action
        data class Completed(val operation: Operation, val session: AuthSession?) : Action
        data class Failed(val failure: AccountFailure) : Action
        data class VerificationCooldownEnded(val id: String?) : Action
    }
    fun reducer(client: AccountClient): Reducer<State, Action> = Reducer { state, action ->
        when (action) {
            Action.Started -> state.with(runEffect(id = "account-session", cancelInFlight = true) {
                send -> client.sessions().collect { send(Action.SessionChanged(it)) }
            })
            Action.Open -> state.copy(presented = true, page = if (state.session == null) Page.WELCOME else Page.ACCOUNT, failure = null, notice = null).only()
            Action.Close -> if (state.busy) state.only() else state.copy(presented = false, password = "", confirmation = "").only()
            is Action.Navigate -> if (state.busy) state.only() else state.copy(page = action.page, password = "", confirmation = "", failure = null, notice = null).only()
            is Action.Email -> state.copy(email = action.value, failure = null).only()
            is Action.Password -> state.copy(password = action.value, failure = null).only()
            is Action.Confirmation -> state.copy(confirmation = action.value, failure = null).only()
            is Action.SessionChanged -> state.copy(session = action.session,
                verificationSent = state.verificationSent && state.session?.id == action.session?.id,
                page = if (!state.busy && state.page == Page.ACCOUNT && action.session == null) Page.WELCOME else state.page).only()
            is Action.Perform -> {
                if (state.busy || action.operation == Operation.VERIFY && state.verificationSent) return@Reducer state.only()
                val op = action.operation
                val failure = if (op in listOf(Operation.SIGN_IN, Operation.REGISTER, Operation.RESET))
                    AccountValidation.validate(state.email, state.password, state.confirmation.takeIf { op == Operation.REGISTER }, op == Operation.RESET)
                    else if (op == Operation.DELETE && state.session?.isAnonymous == false && state.password.isEmpty()) AccountFailure.passwordRequired else null
                if (failure != null) return@Reducer state.copy(failure = failure, notice = null).only()
                state.copy(busy = true, failure = null, notice = null).with(runEffect { send ->
                    try {
                        val session = when (op) {
                            Operation.SIGN_IN -> client.signIn(AccountValidation.email(state.email), state.password)
                            Operation.REGISTER -> client.register(AccountValidation.email(state.email), state.password)
                            Operation.ANONYMOUS -> client.anonymous()
                            Operation.RESET -> { client.resetPassword(AccountValidation.email(state.email)); null }
                            Operation.VERIFY -> { client.sendVerification(); null }
                            Operation.REFRESH -> client.refresh()
                            Operation.SIGN_OUT -> { client.signOut(); null }
                            Operation.DELETE -> { client.deleteAccount(state.password); null }
                        }
                        send(Action.Completed(op, session))
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        send(Action.Failed((error as? AccountException)?.failure ?: AccountFailure.unexpected))
                    }
                })
            }
            is Action.Completed -> {
                val clean = state.copy(busy = false, password = "", confirmation = "")
                if (action.operation == Operation.VERIFY) return@Reducer clean.copy(verificationSent = true, notice = "verificationSent").with(
                    runEffect(id = "account-verification", cancelInFlight = true) { send ->
                        delay(60_000); send(Action.VerificationCooldownEnded(state.session?.id))
                    }
                )
                when (action.operation) {
                    Operation.SIGN_IN, Operation.REGISTER, Operation.REFRESH -> clean.copy(session = action.session, page = if (action.session == null) Page.WELCOME else Page.ACCOUNT)
                    Operation.ANONYMOUS -> clean.copy(session = action.session, presented = false)
                    Operation.RESET -> clean.copy(notice = "resetSent")
                    Operation.VERIFY -> clean.copy(verificationSent = true, notice = "verificationSent")
                    Operation.SIGN_OUT, Operation.DELETE -> clean.copy(session = null, page = Page.WELCOME, email = "", verificationSent = false, notice = "deleted".takeIf { action.operation == Operation.DELETE })
                }.only()
            }
            is Action.Failed -> state.copy(busy = false, failure = action.failure, password = "", confirmation = "").only()
            is Action.VerificationCooldownEnded -> state.copy(verificationSent = state.verificationSent && state.session?.id != action.id).only()
        }
    }
}
class AccountViewModel(client: AccountClient) : ViewModel() {
    val store = Store(AccountFeature.State(), AccountFeature.reducer(client), viewModelScope)
    init { store.send(AccountFeature.Action.Started) }
}
