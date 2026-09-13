package com.nexussoft.verbum.feature.scripture.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexussoft.verbum.common.arch.Store
import com.nexussoft.verbum.designsystem.tokens.Spacing
import com.nexussoft.verbum.designsystem.tokens.VerbumTypography
import com.nexussoft.verbum.feature.scripture.AccountFeature
import com.nexussoft.verbum.feature.scripture.AccountFeature.Action
import com.nexussoft.verbum.feature.scripture.AccountFeature.Operation
import com.nexussoft.verbum.feature.scripture.AccountFeature.Page

internal val LocalOpenAccount = staticCompositionLocalOf<() -> Unit> { {} }

@Composable
fun AccountHost(store: Store<AccountFeature.State, Action>, content: @Composable () -> Unit) {
    val open = remember(store) { { store.send(Action.Open) } }
    CompositionLocalProvider(LocalOpenAccount provides open) { content() }
    AccountPresentation(store)
}

/** Only this small sibling observes account changes, not the reader/app shell. */
@Composable
private fun AccountPresentation(store: Store<AccountFeature.State, Action>) {
    val state by store.state.collectAsStateWithLifecycle()
    if (state.presented) AccountScreen(state, store::send)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountScreen(state: AccountFeature.State, send: (Action) -> Unit) {
    var showPassword by remember(state.page) { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    fun submit() {
        focus.clearFocus()
        send(Action.Perform(when (state.page) { Page.REGISTER -> Operation.REGISTER; Page.RESET -> Operation.RESET; else -> Operation.SIGN_IN }))
    }
    val title = when (state.page) {
        Page.WELCOME -> "welcome"; Page.SIGN_IN -> "signInTitle"; Page.REGISTER -> "registerTitle"
        Page.RESET -> "resetTitle"; Page.ACCOUNT -> "account"; Page.DELETE -> "deleteTitle"
    }
    val subtitle = when (state.page) {
        Page.WELCOME -> "intro"; Page.SIGN_IN -> "signInBody"; Page.REGISTER -> "registerBody"; Page.RESET -> "resetBody"
        Page.ACCOUNT -> if (state.session?.isAnonymous == true) "guestBody" else "accountBody"
        Page.DELETE -> if (state.session?.isAnonymous == true) "deleteGuestBody" else "deleteBody"
    }
    Dialog(onDismissRequest = { send(Action.Close) }, properties = DialogProperties(
        usePlatformDefaultWidth = true, dismissOnBackPress = !state.busy, dismissOnClickOutside = !state.busy,
    )) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.imePadding()) {
                TopAppBar(title = { Text(accountText("account")) }, navigationIcon = {
                    IconButton(onClick = { send(Action.Close) }, enabled = !state.busy) {
                        Icon(Icons.Default.Close, contentDescription = accountText("close"))
                    }
                }, actions = {
                    if (state.page != Page.WELCOME && state.page != Page.ACCOUNT) {
                        TextButton(onClick = { send(Action.Navigate(if (state.session == null) Page.WELCOME else Page.ACCOUNT)) }, enabled = !state.busy) { Text(accountText("back")) }
                    }
                })
                Column(Modifier.verticalScroll(rememberScrollState()).padding(Spacing.readingMargin), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                    Text("VERBUM", style = VerbumTypography.overline, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(accountText(title), style = VerbumTypography.editorialTitle, modifier = Modifier.semantics { heading() })
                    Text(accountText(subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    when (state.page) {
                        Page.WELCOME -> {
                            AccountPrimary("register", !state.busy) { send(Action.Navigate(Page.REGISTER)) }
                            OutlinedButton(onClick = { send(Action.Navigate(Page.SIGN_IN)) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(accountText("signIn")) }
                            TextButton(onClick = { send(Action.Perform(Operation.ANONYMOUS)) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(accountText("anonymous")) }
                            TextButton(onClick = { send(Action.Close) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(accountText("later")) }
                        }
                        Page.SIGN_IN, Page.REGISTER, Page.RESET -> {
                            OutlinedTextField(value = state.email, onValueChange = { send(Action.Email(it)) }, label = { Text(accountText("email")) },
                                enabled = !state.busy, singleLine = true, modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.EmailAddress },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = if (state.page == Page.RESET) ImeAction.Send else ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }, onSend = { submit() }))
                            if (state.page != Page.RESET) {
                                AccountPassword("password", state.password, showPassword, !state.busy,
                                    next = state.page == Page.REGISTER, newPassword = state.page == Page.REGISTER, onChange = { send(Action.Password(it)) }, onSubmit = { submit() })
                                if (state.page == Page.REGISTER) {
                                    AccountPassword("confirm", state.confirmation, showPassword, !state.busy, newPassword = true, onChange = { send(Action.Confirmation(it)) }, onSubmit = { submit() })
                                }
                                TextButton(onClick = { showPassword = !showPassword }, enabled = !state.busy) { Text(accountText(if (showPassword) "hide" else "show")) }
                            }
                            AccountPrimary(if (state.page == Page.RESET) "sendReset" else if (state.page == Page.REGISTER) "register" else "signIn", !state.busy) { submit() }
                            if (state.page == Page.SIGN_IN) {
                                TextButton(onClick = { send(Action.Navigate(Page.RESET)) }, enabled = !state.busy) { Text(accountText("forgot")) }
                                if (state.session?.isAnonymous == true) Text(accountText("existingGuest"), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Page.ACCOUNT -> state.session?.let { session ->
                            Text(session.email ?: accountText("guest"), style = VerbumTypography.editorialHeadline)
                            if (session.isAnonymous) {
                                AccountPrimary("register", !state.busy) { send(Action.Navigate(Page.REGISTER)) }
                                TextButton(onClick = { send(Action.Navigate(Page.SIGN_IN)) }, enabled = !state.busy) { Text(accountText("signIn")) }
                            } else {
                                Text(accountText(if (session.isEmailVerified) "verified" else "unverified"))
                                if (!session.isEmailVerified) {
                                    TextButton(onClick = { send(Action.Perform(Operation.VERIFY)) }, enabled = !state.busy && !state.verificationSent) { Text(accountText("verify")) }
                                    TextButton(onClick = { send(Action.Perform(Operation.REFRESH)) }, enabled = !state.busy) { Text(accountText("refresh")) }
                                }
                            }
                            HorizontalDivider()
                            TextButton(onClick = { confirmSignOut = true }, enabled = !state.busy) { Text(accountText("signOut")) }
                            TextButton(onClick = { send(Action.Navigate(Page.DELETE)) }, enabled = !state.busy) { Text(accountText("delete"), color = MaterialTheme.colorScheme.error) }
                        }
                        Page.DELETE -> {
                            if (state.session?.isAnonymous == false) AccountPassword("password", state.password, false, !state.busy, onChange = { send(Action.Password(it)) }, onSubmit = {})
                            OutlinedButton(onClick = { send(Action.Perform(Operation.DELETE)) }, enabled = !state.busy) { Text(accountText("delete"), color = MaterialTheme.colorScheme.error) }
                        }
                    }
                    state.notice?.let { Text(accountText(it), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium) }
                    state.failure?.let { Text(accountText(it.name), color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) }
                    if (state.busy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(accountText("working"), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    if (confirmSignOut) AlertDialog(onDismissRequest = { confirmSignOut = false }, title = { Text(accountText("signOut")) },
        text = { Text(accountText("signOutBody")) }, confirmButton = {
            TextButton(onClick = { confirmSignOut = false; send(Action.Perform(Operation.SIGN_OUT)) }) { Text(accountText("signOut")) }
        }, dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text(accountText("cancel")) } })
}

@Composable
private fun AccountPrimary(key: String, enabled: Boolean, action: () -> Unit) {
    Button(onClick = action, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(accountText(key)) }
}

@Composable
private fun AccountPassword(key: String, value: String, visible: Boolean, enabled: Boolean, next: Boolean = false, newPassword: Boolean = false, onChange: (String) -> Unit, onSubmit: () -> Unit) {
    val focus = LocalFocusManager.current
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(accountText(key)) },
        modifier = Modifier.fillMaxWidth().semantics { contentType = if (newPassword) ContentType.NewPassword else ContentType.Password },
        enabled = enabled, singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = if (next) ImeAction.Next else ImeAction.Go),
        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }, onGo = { onSubmit() }))
}
