package com.nexussoft.verbum.feature.scripture.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexussoft.verbum.clients.NotificationAuthorization
import com.nexussoft.verbum.clients.helloao.HelloAOTranslation
import com.nexussoft.verbum.designsystem.tokens.Spacing
import com.nexussoft.verbum.designsystem.tokens.VerbumTypography
import com.nexussoft.verbum.feature.scripture.*
import com.nexussoft.verbum.feature.scripture.R
import com.nexussoft.verbum.models.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileScreen(account: AccountFeature.State, state: ProfileFeature.State,
    notifications: DailyVerseFeature.State, send: (ProfileFeature.Action) -> Unit,
    sendAccount: (AccountFeature.Action) -> Unit, sendNotifications: (DailyVerseFeature.Action) -> Unit,
    openPassage: (PassageReference) -> Unit, signOut: () -> Unit,
) {
    var page by rememberSaveable { mutableStateOf("profile") }
    var readingSettings by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
    val language = if(BookLanguage.current == BookLanguage.PORTUGUESE) "Português" else "English"
    LaunchedEffect(Unit) { send(ProfileFeature.Action.Started) }
    BackHandler(page != "profile") { page = "profile" }
    fun settings(languageSettings: Boolean = false) {
        val intent = if(languageSettings && Build.VERSION.SDK_INT >= 33)
            Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.parse("package:${context.packageName}"))
        else if(languageSettings) Intent(Settings.ACTION_LOCALE_SETTINGS)
        else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(accountText(page)) }, navigationIcon = {
            if(page != "profile") IconButton(onClick = { page = "profile" }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, accountText("back"))
            }
        }, actions = {
            IconButton(onClick = { sendAccount(AccountFeature.Action.Close) }, enabled = !account.busy) { Icon(Icons.Outlined.Close, accountText("close")) }
        }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
        key(page) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.readingMargin).padding(bottom = Spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
                if(page == "profile") {
                    Column(Modifier.fillMaxWidth().padding(top = Spacing.sm), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Box(Modifier.size(88.dp).background(primary.copy(alpha = 0.09f), CircleShape).border(1.dp, primary.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                            val initials = account.session?.initials
                            if(initials != null) Text(initials, fontSize = 30.sp, fontFamily = FontFamily.Serif, color = primary)
                            else Icon(Icons.Outlined.PersonOutline, null, Modifier.size(36.dp), tint = primary)
                        }
                        Text(account.session?.displayName?.takeIf { it.isNotBlank() } ?: accountText("yourReadingSpace"), style = VerbumTypography.editorialHeadline, textAlign = TextAlign.Center)
                        val email = account.session?.email
                        if(email != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Text(email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if(account.session?.isEmailVerified == true) Icon(Icons.Outlined.Verified, accountText("verified"), Modifier.size(16.dp), tint = primary)
                        } else Text(accountText("profileGuestBody"), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if(account.session == null || account.session.isAnonymous) TextButton(onClick = { sendAccount(AccountFeature.Action.Navigate(AccountFeature.Page.WELCOME)) }) { Text(accountText("createYourAccount")) }
                    }
                    account.failure?.let { Text(accountText(it.name), color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) }
                    if(account.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    account.notice?.let { Text(accountText(it), style = MaterialTheme.typography.bodyMedium, color = primary) }
                    Column(Modifier.fillMaxWidth().background(primary.copy(alpha = 0.055f), RoundedCornerShape(16.dp)).border(1.dp, primary.copy(alpha = 0.15f), RoundedCornerShape(16.dp)).padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                        Text(accountText("yourJourney").uppercase(), style = VerbumTypography.overline, color = primary)
                        Text(accountText(if(state.activity.visits.isEmpty()) "journeyBeginning" else "journeyContinuing"), style = VerbumTypography.editorialHeadline)
                        val ready = !state.loading && !state.failed
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            ProfileMetric(if(ready) state.activity.days.size else null, "readingDays", Modifier.weight(1f))
                            ProfileMetric(if(ready) state.activity.visits.size else null, "chaptersOpened", Modifier.weight(1f))
                            ProfileMetric(if(ready) state.bookmarks.size else null, "savedPassages", Modifier.weight(1f))
                        }
                        (state.activity.visits.firstOrNull()?.reference ?: state.lastRead)?.let { reference ->
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(Modifier.fillMaxWidth().clickable { openPassage(reference) }.padding(vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                                Text(accountText("continueReading"), style = MaterialTheme.typography.bodySmall, color = primary, modifier = Modifier.weight(1f))
                                Text(reference.formatted, style = VerbumTypography.navigationSerif, color = primary)
                                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.padding(start = Spacing.sm).size(16.dp), tint = primary)
                            }
                        }
                    }
                    ProfileSection("usageTitle") {
                        Column(Modifier.padding(vertical = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            val usage = state.usage
                            if(state.usageFailed) Text(accountText("usageUnavailable"), style = MaterialTheme.typography.bodySmall)
                            else if(usage != null) {
                                Text(accountText("plan_" + usage.plan), style = VerbumTypography.editorialHeadline)
                                listOf("ask" to "usageAsk", "voice" to "usageVoice").forEach { (kind, key) ->
                                    Row { Text(accountText(key), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall); Text((usage.remaining[kind] ?: 0).toString()) }
                                }
                                usageDate(usage.resetsAt)?.let { Text(accountText("usageReset") + " " + it, style = MaterialTheme.typography.bodySmall) }
                                if(usage.restricted) Text(accountText("usageRestricted"), style = MaterialTheme.typography.bodySmall)
                                Text(accountText("usageTTS"), style = MaterialTheme.typography.bodySmall)
                                Text(accountText("usageCache"), style = MaterialTheme.typography.bodySmall)
                            } else Text(accountText("plan_guest"), style = VerbumTypography.editorialHeadline)
                        }
                    }
                    ProfileSection("readingSection") {
                        ProfileRow("bibleVersion", Icons.Outlined.MenuBook, HelloAOTranslation.name(HelloAOTranslation.id(BookLanguage.current))) { page = "bibleVersion" }
                        ProfileDivider()
                        ProfileRow("bibleLanguage", Icons.Outlined.Translate, language) { page = "bibleLanguage" }
                        ProfileDivider()
                        ProfileRow("readingPreferences", Icons.Outlined.FormatSize) { readingSettings = true }
                        ProfileDivider()
                        ProfileRow("audioVoice", Icons.Outlined.Headphones) { page = "audioVoice" }
                    }
                    ProfileSection("yourVerbum") {
                        ProfileRow("savedPassages", Icons.Outlined.BookmarkBorder, state.bookmarks.size.toString().takeIf { !state.loading && !state.failed }) { page = "savedPassages" }
                        ProfileDivider()
                        ProfileRow("highlights", Icons.Outlined.BorderColor, state.highlights.size.toString().takeIf { !state.loading && !state.failed }) { page = "highlights" }
                        ProfileDivider()
                        ProfileRow("notes", Icons.Outlined.EditNote, state.notes.size.toString().takeIf { !state.loading && !state.failed }) { page = "notes" }
                        ProfileDivider()
                        ProfileRow("readingHistory", Icons.Outlined.History) { page = "readingHistory" }
                    }
                    ProfileSection("preferencesSection") {
                        ProfileRow("appearance", Icons.Outlined.Contrast, accountText(state.appearance.copyKey)) { page = "appearance" }
                        ProfileDivider()
                        ProfileRow("notifications", Icons.Outlined.NotificationsNone) { page = "notifications" }
                        ProfileDivider()
                        ProfileRow("appLanguage", Icons.Outlined.Language, language) { page = "appLanguage" }
                    }
                    ProfileSection("verbumSection") {
                        ProfileRow("help", Icons.Outlined.HelpOutline) { page = "help" }
                        ProfileDivider()
                        ProfileRow("privacy", Icons.Outlined.PrivacyTip) { page = "privacy" }
                        ProfileDivider()
                        ProfileRow("about", Icons.Outlined.AutoAwesome) { page = "about" }
                    }
                    ProfileSection("account") {
                        if(account.session != null) {
                            ProfileRow("accountDetails", Icons.Outlined.AccountCircle) { sendAccount(AccountFeature.Action.Navigate(AccountFeature.Page.ACCOUNT)) }
                            ProfileDivider()
                            ProfileRow("signOut", Icons.AutoMirrored.Outlined.Logout, disclosure = false, onClick = signOut)
                        } else ProfileRow("signIn", Icons.Outlined.AccountCircle) { sendAccount(AccountFeature.Action.Navigate(AccountFeature.Page.WELCOME)) }
                    }
                    Text(accountText("deviceLocal"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                } else when(page) {
                    "savedPassages", "highlights", "notes", "readingHistory" -> {
                        if(state.loading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                        else if(state.failed) {
                            Text(accountText("collectionLoadFailed"))
                            TextButton(onClick = { send(ProfileFeature.Action.Started) }) { Text(accountText("tryAgain")) }
                        } else if(page == "readingHistory") {
                            Text(accountText("historyBody"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if(state.activity.visits.isEmpty()) ProfileEmpty("historyEmpty", Icons.Outlined.MenuBook)
                            state.activity.visits.forEach { visit ->
                                val date = Instant.ofEpochMilli(visit.lastOpened).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                                ProfileRow(visit.reference.formatted, Icons.Outlined.MenuBook, date, literal = true) { openPassage(visit.reference) }
                                ProfileDivider()
                            }
                        } else {
                            val annotations = when(page) { "savedPassages" -> state.bookmarks; "highlights" -> state.highlights; else -> state.notes }
                            if(annotations.isEmpty()) ProfileEmpty("collectionEmpty", Icons.Outlined.BookmarkBorder)
                            annotations.forEach { annotation ->
                                Column(Modifier.fillMaxWidth().clickable { openPassage(annotation.reference) }) {
                                    ProfileRow(annotation.reference.formatted, if(page == "notes") Icons.Outlined.EditNote else Icons.Outlined.BookmarkBorder, literal = true) { openPassage(annotation.reference) }
                                    if(annotation.note.isNotEmpty()) Text(annotation.note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4, modifier = Modifier.padding(bottom = Spacing.md))
                                }
                                ProfileDivider()
                            }
                        }
                    }
                    "appearance" -> ProfileFeature.Appearance.entries.forEach { appearance ->
                        Row(Modifier.fillMaxWidth().clickable { send(ProfileFeature.Action.AppearanceChanged(appearance)) }.padding(vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = appearance == state.appearance, onClick = { send(ProfileFeature.Action.AppearanceChanged(appearance)) })
                            Text(accountText(appearance.copyKey))
                        }
                    }
                    "notifications" -> {
                        LaunchedEffect(Unit) { sendNotifications(DailyVerseFeature.Action.Started) }
                        Text(accountText("notificationsBody"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.every_morning_at_seven), Modifier.weight(1f))
                            Switch(notifications.morningsEnabled, { sendNotifications(DailyVerseFeature.Action.MorningsToggled(it)) })
                        }
                        if(notifications.authorization == NotificationAuthorization.DENIED) TextButton(onClick = { settings() }) {
                            Text(stringResource(R.string.notifications_off_open_settings))
                        }
                    }
                    else -> {
                        Text(accountText(page), style = VerbumTypography.editorialTitle, modifier = Modifier.semantics { heading() })
                        val paragraphs = when(page) {
                            "bibleVersion" -> listOf("bibleVersionBody", "bibleComparisonBody")
                            "bibleLanguage" -> listOf("bibleLanguageBody")
                            "audioVoice" -> listOf("audioBody", "voiceBody")
                            "appLanguage" -> listOf("appLanguageBody")
                            "help" -> listOf("helpReading", "helpSaving", "helpOffline")
                            "privacy" -> listOf("privacyLocal", "privacyAccount", "privacyAI")
                            else -> listOf("aboutBody")
                        }
                        paragraphs.forEach { Text(accountText(it), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if(page in listOf("bibleLanguage", "appLanguage", "audioVoice")) OutlinedButton(onClick = { settings(page != "audioVoice") }) { Text(accountText("openSystemSettings")) }
                        if(page == "about") {
                            val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }
                            Text("Verbum $version", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
    if(readingSettings) ReaderSettingsSheet(state.settings, { send(ProfileFeature.Action.Settings(it)) }, { readingSettings = false })
}

@Composable
private fun ProfileMetric(value: Int?, key: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(value?.toString() ?: "—", fontSize = 30.sp, fontFamily = FontFamily.Serif)
        Text(accountText(key), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ProfileSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(accountText(title).uppercase(), style = VerbumTypography.overline, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.semantics { heading() })
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLowest, RoundedCornerShape(16.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)).padding(horizontal = Spacing.lg), content = content)
    }
}

@Composable
private fun ProfileRow(title: String, icon: ImageVector, value: String? = null, disclosure: Boolean = true, literal: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp).padding(vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Text(if(literal) title else accountText(title), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if(value != null) Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, modifier = Modifier.widthIn(max = 130.dp))
        if(disclosure) Icon(Icons.Outlined.ChevronRight, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ProfileDivider() { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }

@Composable
private fun ProfileEmpty(key: String, icon: ImageVector) {
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.xxl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Icon(icon, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        Text(accountText(key), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
