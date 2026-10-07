package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.clients.*
import com.nexussoft.verbum.common.arch.*
import com.nexussoft.verbum.models.*
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ReadingCollectionFeature {
    data class State(
        val activity: ReadingActivity = ReadingActivity(),
        val lastRead: PassageReference? = null,
        val annotations: List<ReaderAnnotation> = emptyList(),
        val query: String = "",
        val historyQuery: String = "",
        val historyVisibleCount: Int = 20,
        val historyScrollIndex: Int = 0,
        val historyScrollOffset: Int = 0,
        val filter: ReadingCollectionFilter = ReadingCollectionFilter.ALL,
        val loading: Boolean = false,
        val failed: Boolean = false,
    ) {
        val matchingVisits: List<ReadingActivity.Visit> get() {
            val terms = normalized(historyQuery).split(Regex("\\s+")).filter { it.isNotEmpty() }
            return activity.visits.filter { visit ->
                val book = BibleBook.book(visit.reference.bookId)
                val names = normalized((listOf(visit.reference.bookId) + BookLanguage.entries.map { book?.localizedName(it).orEmpty() }).joinToString(" "))
                terms.all { term ->
                    term.toIntOrNull()?.let { it == visit.reference.chapter || term in names.split(" ") } ?: (term in names)
                }
            }
        }
        val visibleVisits get() = matchingVisits.take(historyVisibleCount)
        val hasMore get() = matchingVisits.size > historyVisibleCount
    }
    private fun normalized(text: String) = Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
    sealed interface Action {
        data object Started : Action
        data object Retry : Action
        data object HistoryTapped : Action
        data class HistoryQueryChanged(val query: String) : Action
        data object HistoryShowMore : Action
        data class HistoryScrollChanged(val index: Int, val offset: Int) : Action
        data object JourneyStarted : Action
        data class JourneyLoaded(val activity: ReadingActivity, val lastRead: PassageReference?) : Action
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
        data object History : DelegateAction
    }
    fun reducer(preferences: PreferencesClient, annotations: ReaderAnnotationsClient = PreferenceReaderAnnotationsClient(preferences)): Reducer<State, Action> = Reducer { state, action ->
        when (action) {
            Action.HistoryTapped -> state.with(Effect.Send(Action.Delegate(DelegateAction.History)))
            is Action.HistoryQueryChanged -> state.copy(historyQuery = action.query, historyVisibleCount = 20, historyScrollIndex = 0, historyScrollOffset = 0).only()
            Action.HistoryShowMore -> state.copy(historyVisibleCount = state.historyVisibleCount + 20).only()
            is Action.HistoryScrollChanged -> state.copy(historyScrollIndex = action.index, historyScrollOffset = action.offset).only()
            Action.JourneyStarted -> state.copy(loading = true, failed = false).with(runEffect(id = "reading-collection", cancelInFlight = true) { send ->
                try {
                    send(withContext(Dispatchers.IO) {
                        Action.JourneyLoaded(ReadingActivityClient(preferences).load(), preferences.string(ChapterReaderFeature.LAST_READ_KEY)?.let(LastRead::decode))
                    })
                } catch (error: CancellationException) { throw error }
                catch (_: Exception) { send(Action.Failed) }
            })
            is Action.JourneyLoaded -> state.copy(activity = action.activity, lastRead = action.lastRead, loading = false, failed = false).only()
            Action.Started, Action.Retry -> state.copy(loading = true, failed = false).with(runEffect(id = "reading-collection", cancelInFlight = true) { send ->
                try {
                    val loaded = withContext(Dispatchers.IO) {
                        Action.Loaded(state.activity, state.lastRead, annotations.load())
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
