package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.clients.*
import com.nexussoft.verbum.common.arch.*
import com.nexussoft.verbum.models.*
import kotlinx.coroutines.CancellationException

object ProfileFeature {
    const val APPEARANCE_KEY = "profileAppearance"
    enum class Appearance(val copyKey: String) { AUTOMATIC("automatic"), LIGHT("light"), DARK("dark") }
    data class State(
        val usage: UsageStatus? = null,
        val usageFailed: Boolean = false,
        val activity: ReadingActivity = ReadingActivity(),
        val lastRead: PassageReference? = null,
        val annotations: List<ReaderAnnotation> = emptyList(),
        val loading: Boolean = false,
        val failed: Boolean = false,
        val appearance: Appearance = Appearance.AUTOMATIC,
        val settings: ReaderSettingsFeature.State = ReaderSettingsFeature.State(ReaderTextScale.STANDARD),
    ) {
        val bookmarks get() = annotations.filter { it.bookmarked }
        val highlights get() = annotations.filter { it.highlight != null }
        val notes get() = annotations.filter { it.note.isNotEmpty() }
    }
    sealed interface Action {
        data class UsageLoaded(val value: UsageStatus?): Action
        data object UsageFailed: Action
        data object Started: Action
        data class Loaded(val activity: ReadingActivity, val annotations: List<ReaderAnnotation>, val settings: ReaderSettingsFeature.State, val lastRead: PassageReference?): Action
        data object Failed: Action
        data class AppearanceChanged(val value: Appearance): Action
        data class Settings(val action: ReaderSettingsFeature.Action): Action
    }
    fun initial(preferences: PreferencesClient) = State(appearance = runCatching {
        Appearance.valueOf(preferences.string(APPEARANCE_KEY).orEmpty())
    }.getOrDefault(Appearance.AUTOMATIC))

    fun reducer(preferences: PreferencesClient, annotations: ReaderAnnotationsClient = PreferenceReaderAnnotationsClient(preferences), usageStatus: suspend () -> UsageStatus? = { null }): Reducer<State, Action> = combine(
        ReaderSettingsFeature.reducer(preferences).pullback(
            get = { it.settings }, set = { s, c -> s.copy(settings = c) },
            extractAction = { (it as? Action.Settings)?.action }, embedAction = { Action.Settings(it) },
        ),
        Reducer { state, action ->
            when(action) {
                Action.Started -> state.copy(loading = true, failed = false).with(runEffect(id = "profile-load", cancelInFlight = true) { send ->
                    try {
                        send(Action.Loaded(ReadingActivityClient(preferences).load(), annotations.load(), ReaderSettingsFeature.State(
                            ReaderTextScale.fromPreference(preferences.string(ReaderTextScale.PREFERENCE_KEY)),
                            runCatching { ReadingMode.valueOf(preferences.string(ChapterReaderFeature.MODE_KEY).orEmpty()) }.getOrDefault(ReadingMode.PAGES),
                            preferences.string(ChapterReaderFeature.FOCUS_KEY) == "true",
                        ), preferences.string(ChapterReaderFeature.LAST_READ_KEY)?.let(LastRead::decode)))
                    } catch (error: CancellationException) { throw error }
                    catch (_: Exception) { send(Action.Failed) }
                    try { send(Action.UsageLoaded(usageStatus())) }
                    catch(error: CancellationException) { throw error }
                    catch(_: Exception) { send(Action.UsageFailed) }
                })
                is Action.UsageLoaded -> state.copy(usage = action.value, usageFailed = false).only()
                Action.UsageFailed -> state.copy(usageFailed = true).only()
                is Action.Loaded -> state.copy(activity = action.activity, annotations = action.annotations, settings = action.settings, lastRead = action.lastRead, loading = false).only()
                Action.Failed -> state.copy(loading = false, failed = true).only()
                is Action.AppearanceChanged -> state.copy(appearance = action.value).with(runEffect { preferences.setString(APPEARANCE_KEY, action.value.name) })
                is Action.Settings -> state.only()
            }
        },
    )
}
