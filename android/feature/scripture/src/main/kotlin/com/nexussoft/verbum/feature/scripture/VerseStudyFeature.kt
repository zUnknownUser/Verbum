package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.clients.*
import com.nexussoft.verbum.common.arch.*
import com.nexussoft.verbum.models.*
import kotlinx.coroutines.CancellationException

object VerseStudyFeature {
    enum class Tab { HIGHLIGHT, NOTE, COMPARE, CONTEXT, REFERENCES, ASK }
    data class State(
        val reference: PassageReference, val text: String, val translationId: String,
        val annotation: ReaderAnnotation, val savedNote: String = annotation.note,
        val tab: Tab = Tab.HIGHLIGHT, val context: PassageContext? = null,
        val referencesLoaded: Boolean=false, val contextLoading: Boolean=false, val contextFailed: Boolean=false,
        val candidates: List<BibleEntity> = emptyList(), val entity: EntityDetail? = null,
        val entityLoading: Boolean=false, val entityFailed: Boolean=false,
        val comparison: BiblePassage?=null, val comparisonLoading: Boolean=false, val comparisonFailed: Boolean=false,
        val saving: Boolean=false, val saveFailed: Boolean=false, val saved: Boolean=false,
        val passageAfterSave: PassageReference?=null, val closeAfterSave: Boolean=false, val question: String="", val ask: AskFeature.State?=null,
    ) {
        companion object {
            fun initial(passage: BiblePassage,annotation: ReaderAnnotation?,context: PassageContext?,candidates: List<BibleEntity>): State {
                val reference=PassageReference(passage.bookId,passage.chapter,passage.verseStart..passage.verseEnd)
                return State(reference,passage.text,passage.translationId,annotation ?: ReaderAnnotation(reference),context=context,candidates=candidates,
                    tab=if(candidates.isEmpty()) Tab.HIGHLIGHT else Tab.CONTEXT)
            }
        }
    }
    sealed interface Action {
        data object BookmarkToggled: Action
        data object Started: Action; data object Save: Action; data object Done: Action
        data class TabChanged(val tab: Tab): Action
        data class HighlightStyleChanged(val style:HighlightStyle):Action
        data class HighlightChanged(val color: HighlightColor?): Action
        data class NoteChanged(val value: String): Action
        data class QuestionChanged(val value: String): Action
        data class ContextLoaded(val value: PassageContext?): Action
        data object ContextFailed: Action
        data class EntityTapped(val value: BibleEntity): Action
        data class EntityLoaded(val value: EntityDetail): Action
        data object EntityFailed: Action; data object CloseEntity: Action
        data class ComparisonLoaded(val value: BiblePassage): Action
        data object ComparisonFailed: Action
        data class Saved(val value: ReaderAnnotation): Action
        data object SaveFailed: Action
        data object Submit: Action; data object CloseAnswer: Action
        data class Ask(val action: AskFeature.Action): Action
        data class PassageTapped(val value: PassageReference): Action
        data class Delegate(val value: DelegateAction): Action
    }
    sealed interface DelegateAction {
        data class AnnotationSaved(val value: ReaderAnnotation): DelegateAction
        data class OpenPassage(val value: PassageReference): DelegateAction
        data object Close: DelegateAction
    }
    private object ContextId; private object EntityId; private object ComparisonId; private object SaveId
    fun reducer(contextClient: ContextClient,graph: GraphClient,askClient: AskScriptureClient,annotations: ReaderAnnotationsClient,comparison: BibleComparisonClient): Reducer<State,Action> = combine(
        AskFeature.reducer(askClient,graph).pullbackOptional(
            get={it.ask},set={s,c->s.copy(ask=c)},extractAction={(it as? Action.Ask)?.action},embedAction={Action.Ask(it)},
        ),
        Reducer { state,action ->
            when(action) {
                Action.BookmarkToggled -> if(state.saving) state.only() else state.copy(annotation=state.annotation.copy(bookmarked=!state.annotation.bookmarked),saved=false).with(Effect.Send(Action.Save))
                Action.Started -> if(state.candidates.size==1) state.with(Effect.Send(Action.EntityTapped(state.candidates[0]))) else state.only()
                Action.Done -> if(state.annotation.note!=state.savedNote) state.copy(closeAfterSave=true).with(Effect.Send(Action.Save)) else state.with(Effect.Send(Action.Delegate(DelegateAction.Close)))
                is Action.TabChanged -> {
                    val next=state.copy(tab=action.tab,entity=null,candidates=emptyList())
                    when {
                        ((action.tab==Tab.CONTEXT && state.context==null) || (action.tab==Tab.REFERENCES && !state.referencesLoaded)) && !state.contextLoading -> next.copy(contextLoading=true,contextFailed=false).with(runEffect(id=ContextId,cancelInFlight=true) {send->
                            try {send(Action.ContextLoaded(contextClient.chapter(state.reference)))} catch(e:CancellationException){throw e} catch(e:Exception){send(Action.ContextFailed)}
                        })
                        action.tab==Tab.COMPARE && state.comparison==null && !state.comparisonLoading -> next.copy(comparisonLoading=true,comparisonFailed=false).with(runEffect(id=ComparisonId,cancelInFlight=true) {send->
                            try {send(Action.ComparisonLoaded(comparison.passage(state.reference)))} catch(e:CancellationException){throw e} catch(e:Exception){send(Action.ComparisonFailed)}
                        })
                        else -> next.only()
                    }
                }
                is Action.HighlightStyleChanged -> state.copy(annotation=state.annotation.copy(highlightStyle=action.style,highlight=state.annotation.highlight ?: HighlightColor.GOLD),saved=false).with(Effect.Send(Action.Save))
                is Action.HighlightChanged -> state.copy(annotation=state.annotation.copy(highlight=action.color),saved=false).with(Effect.Send(Action.Save))
                is Action.NoteChanged -> state.copy(annotation=state.annotation.copy(note=action.value.take(10000)),saved=false).only()
                Action.Save -> state.copy(saving=true,saveFailed=false).with(runEffect(id=SaveId,cancelInFlight=true) {send->
                    try {annotations.save(state.annotation);send(Action.Saved(state.annotation))} catch(e:CancellationException){throw e} catch(e:Exception){send(Action.SaveFailed)}
                })
                is Action.Saved -> state.copy(saving=false,saved=action.value==state.annotation,savedNote=action.value.note).with(runEffect {send->
                    send(Action.Delegate(DelegateAction.AnnotationSaved(action.value)))
                    if(state.passageAfterSave!=null) send(Action.Delegate(DelegateAction.OpenPassage(state.passageAfterSave)))
                    else if(state.closeAfterSave) send(Action.Delegate(DelegateAction.Close))
                })
                Action.SaveFailed -> state.copy(saving=false,saveFailed=true,closeAfterSave=false,passageAfterSave=null).only()
                is Action.ContextLoaded -> state.copy(contextLoading=false,referencesLoaded=true,context=action.value).only()
                Action.ContextFailed -> state.copy(contextLoading=false,contextFailed=true).only()
                is Action.EntityTapped -> state.copy(entity=null,entityLoading=true,entityFailed=false).with(runEffect(id=EntityId,cancelInFlight=true) {send->
                    try {send(Action.EntityLoaded(graph.detail(action.value.id)))} catch(e:CancellationException){throw e} catch(e:Exception){send(Action.EntityFailed)}
                })
                is Action.EntityLoaded -> state.copy(entityLoading=false,entity=action.value).only()
                Action.EntityFailed -> state.copy(entityLoading=false,entityFailed=true).only()
                Action.CloseEntity -> state.copy(entity=null,candidates=emptyList()).only()
                is Action.ComparisonLoaded -> state.copy(comparisonLoading=false,comparison=action.value).only()
                Action.ComparisonFailed -> state.copy(comparisonLoading=false,comparisonFailed=true).only()
                is Action.QuestionChanged -> state.copy(question=action.value.take(400)).only()
                Action.Submit -> if(state.question.isBlank()) state.only() else state.copy(ask=AskFeature.State(question=state.question.trim(),reference=state.reference)).only()
                Action.CloseAnswer -> state.copy(ask=null).only()
                is Action.PassageTapped -> if(state.annotation.note!=state.savedNote)
                    state.copy(passageAfterSave=action.value).with(Effect.Send(Action.Save))
                    else state.with(Effect.Send(Action.Delegate(DelegateAction.OpenPassage(action.value))))
                is Action.Ask -> when(val delegate=(action.action as? AskFeature.Action.Delegate)?.delegate) {
                    is AskFeature.DelegateAction.OpenPassage -> state.with(Effect.Send(Action.PassageTapped(delegate.reference)))
                    is AskFeature.DelegateAction.OpenEntity -> state.copy(ask=null,tab=Tab.CONTEXT).with(Effect.Send(Action.EntityTapped(delegate.entity)))
                    is AskFeature.DelegateAction.SearchInstead, is AskFeature.DelegateAction.Talk -> state.copy(ask=null).only()
                    null -> state.only()
                }
                is Action.Delegate -> state.only()
            }
        },
    )
}
