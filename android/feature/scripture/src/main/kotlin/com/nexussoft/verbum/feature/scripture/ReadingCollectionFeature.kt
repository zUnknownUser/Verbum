package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.clients.*
import com.nexussoft.verbum.common.arch.*
import com.nexussoft.verbum.models.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ReadingCollectionFeature {
    data class State(
        val activity: ReadingActivity = ReadingActivity(),
        val lastRead: PassageReference? = null,
        val annotations: List<ReaderAnnotation> = emptyList(),
        val query: String = "",
        val filter: ReadingCollectionFilter = ReadingCollectionFilter.ALL,
        val loading: Boolean = false,
        val failed: Boolean = false,
    )
    sealed interface Action {
        data object Started : Action
        data object Retry : Action
        data class Loaded(val activity: ReadingActivity, val lastRead: PassageReference?, val annotations: List<ReaderAnnotation>) : Action
        data object Failed : Action
        data class QueryChanged(val query: String) : Action
        data class FilterChanged(val filter: ReadingCollectionFilter) : Action
        data class Open(val reference: PassageReference) : Action
        data object Browse : Action
        data class Delegate(val value: DelegateAction) : Action
    }
    sealed interface DelegateAction {
        data class Open(val reference: PassageReference) : DelegateAction
        data object Browse : DelegateAction
    }
    fun reducer(preferences: PreferencesClient, annotations: ReaderAnnotationsClient = PreferenceReaderAnnotationsClient(preferences)): Reducer<State, Action> = Reducer { state, action ->
        when (action) {
            Action.Started, Action.Retry -> state.copy(loading = true, failed = false).with(runEffect(id = "reading-collection", cancelInFlight = true) { send ->
                try {
                    val loaded = withContext(Dispatchers.IO) {
                        Action.Loaded(ReadingActivityClient(preferences).load(), preferences.string(ChapterReaderFeature.LAST_READ_KEY)?.let(LastRead::decode), annotations.load())
                    }
                    send(loaded)
                } catch (error: CancellationException) { throw error }
                catch (_: Exception) { send(Action.Failed) }
            })
            is Action.Loaded -> state.copy(activity = action.activity, lastRead = action.lastRead, annotations = action.annotations, loading = false, failed = false).only()
            Action.Failed -> state.copy(loading = false, failed = true).only()
            is Action.QueryChanged -> state.copy(query = action.query).only()
            is Action.FilterChanged -> state.copy(filter = action.filter).only()
            is Action.Open -> state.with(Effect.Send(Action.Delegate(DelegateAction.Open(action.reference))))
            Action.Browse -> state.with(Effect.Send(Action.Delegate(DelegateAction.Browse)))
            is Action.Delegate -> state.only()
        }
    }
}
