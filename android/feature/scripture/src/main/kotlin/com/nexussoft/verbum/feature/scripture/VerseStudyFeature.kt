package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.clients.*
import com.nexussoft.verbum.common.arch.*
import com.nexussoft.verbum.models.*
import kotlinx.coroutines.CancellationException
import java.util.UUID

object VerseStudyFeature {
    enum class Tab { HIGHLIGHT, NOTE, COMPARE, CONTEXT, REFERENCES, ASK }
    data class EntityLayer(val detail: EntityDetail, val passageLimit: Int)
    data class State(
        val reference: PassageReference, val text: String, val translationId: String,
        val chapterGuide: Boolean=false,
        val selectedType: BibleEntityType?=null, val entityLimit: Int=6, val referenceLimit: Int=6, val entityPassageLimit: Int=6,
        val selectedEntity: BibleEntity?=null, val entityRequest: String?=null,
        val entityHistory: List<EntityLayer> = emptyList(),
        val previewReference: PassageReference?=null, val previewRequest: String?=null,
        val preview: BiblePassage?=null, val previewLoading: Boolean=false, val previewFailed: Boolean=false,
        val annotation: ReaderAnnotation, val savedNote: String = annotation.note,
        val tab: Tab = Tab.HIGHLIGHT, val context: PassageContext? = null,
        val referencesLoaded: Boolean=false, val contextLoading: Boolean=false, val contextFailed: Boolean=false,
        val candidates: List<BibleEntity> = emptyList(), val entity: EntityDetail? = null,
        val entityLoading: Boolean=false, val entityFailed: Boolean=false,
        val comparison: BiblePassage?=null, val comparisonLoading: Boolean=false, val comparisonFailed: Boolean=false,
        val saving: Boolean=false, val saveFailed: Boolean=false, val saved: Boolean=false,
        val passageAfterSave: PassageReference?=null, val closeAfterSave: Boolean=false, val question: String="", val ask: AskFeature.State?=null,
    ) {
        val contextEntities get()=(if(candidates.isNotEmpty()) candidates else context?.entities.orEmpty()).distinctBy {it.id}
        val entityTypes get()=BibleEntityType.entries.filter {type->contextEntities.any {it.type==type}}
        val activeType get()=selectedType?.takeIf {it in entityTypes} ?: entityTypes.firstOrNull()
        val matchingEntities get()=contextEntities.filter {it.type==activeType}
        val visibleEntities get()=matchingEntities.take(entityLimit)
        val relatedPassages get()=(context?.study?.connections.orEmpty().map {it.reference}+context?.relatedPassages.orEmpty()).distinct()
        val keyPassages get()=entity?.keyPassages.orEmpty().distinct().filter {reference->entity?.themeStudy?.readings?.none {it.reference==reference} != false}
        val availableTabs get()=if(chapterGuide) listOf(Tab.CONTEXT,Tab.REFERENCES) else Tab.entries
        companion object {
            fun initial(passage: BiblePassage,annotation: ReaderAnnotation?,context: PassageContext?,candidates: List<BibleEntity>,chapterGuide: Boolean=false): State {
                val reference=PassageReference(passage.bookId,passage.chapter,if(chapterGuide) null else passage.verseStart..passage.verseEnd)
                return State(reference,if(chapterGuide) "" else passage.text,passage.translationId,chapterGuide=chapterGuide,annotation=annotation ?: ReaderAnnotation(reference),context=context,candidates=candidates,
                    tab=if(chapterGuide || candidates.isNotEmpty()) Tab.CONTEXT else Tab.HIGHLIGHT)
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
        data class ConnectedEntityTapped(val value: BibleEntity): Action
        data class EntityLoaded(val request: String,val value: EntityDetail): Action
        data class EntityFailed(val request: String): Action
        data object RetryEntity: Action; data object CloseEntity: Action
        data class EntityTypeChanged(val type: BibleEntityType): Action
        data object MoreEntities: Action; data object MoreReferences: Action; data object MoreEntityPassages: Action
        data class PreviewPassage(val reference: PassageReference): Action
        data class PreviewLoaded(val reference: PassageReference,val request: String,val passage: BiblePassage): Action
        data class PreviewFailed(val reference: PassageReference,val request: String): Action
        data object RetryPreview: Action; data object ClosePreview: Action; data object OpenPreviewInReader: Action
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
    private object ContextId; private object EntityId; private object ComparisonId; private object SaveId; private object PreviewId
    fun cancelRequests(): Effect<Action> = Effect.Merge(listOf(ContextId,EntityId,ComparisonId,PreviewId).map {id->runEffect(id=id,cancelInFlight=true) {}})
    fun reducer(contextClient: ContextClient,graph: GraphClient,askClient: AskScriptureClient,annotations: ReaderAnnotationsClient,comparison: BibleComparisonClient,bible: BibleClient): Reducer<State,Action> = combine(
        AskFeature.reducer(askClient,graph).pullbackOptional(
            get={it.ask},set={s,c->s.copy(ask=c)},extractAction={(it as? Action.Ask)?.action},embedAction={Action.Ask(it)},
        ),
        Reducer { state,action ->
            when(action) {
                Action.BookmarkToggled -> if(state.saving || state.chapterGuide) state.only() else state.copy(annotation=state.annotation.copy(bookmarked=!state.annotation.bookmarked),saved=false).with(Effect.Send(Action.Save))
                Action.Started -> when {
                    state.candidates.size==1 -> state.with(Effect.Send(Action.EntityTapped(state.candidates[0])))
                    state.tab==Tab.CONTEXT -> state.with(Effect.Send(Action.TabChanged(Tab.CONTEXT)))
                    else -> state.only()
                }
                Action.Done -> if(state.annotation.note!=state.savedNote) state.copy(closeAfterSave=true).with(Effect.Send(Action.Save)) else state.with(Effect.Send(Action.Delegate(DelegateAction.Close)))
                is Action.TabChanged -> {
                    if(action.tab !in state.availableTabs) return@Reducer state.only()
                    val next=state.copy(tab=action.tab)
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
                is Action.EntityTapped -> {
                    val request=UUID.randomUUID().toString()
                    state.copy(entity=null,selectedEntity=action.value,entityRequest=request,entityLoading=true,entityFailed=false,entityPassageLimit=6).with(runEffect(id=EntityId,cancelInFlight=true) {send->
                        try {send(Action.EntityLoaded(request,graph.detail(action.value.id)))} catch(e:CancellationException){throw e} catch(e:Exception){send(Action.EntityFailed(request))}
                    })
                }
                is Action.ConnectedEntityTapped -> if(state.entity?.entity?.id==action.value.id) state.only()
                    else state.copy(entityHistory=state.entity?.let {state.entityHistory+EntityLayer(it,state.entityPassageLimit)} ?: state.entityHistory)
                        .with(Effect.Send(Action.EntityTapped(action.value)))
                is Action.EntityLoaded -> if(state.entityRequest!=action.request) state.only() else state.copy(entityLoading=false,entity=action.value).only()
                is Action.EntityFailed -> if(state.entityRequest!=action.request) state.only() else state.copy(entityLoading=false,entityFailed=true).only()
                Action.RetryEntity -> state.selectedEntity?.let {state.with(Effect.Send(Action.EntityTapped(it)))} ?: state.only()
                Action.CloseEntity -> state.entityHistory.lastOrNull()?.let {parent->
                    state.copy(entity=parent.detail,selectedEntity=parent.detail.entity,entityRequest=null,
                        entityLoading=false,entityFailed=false,entityPassageLimit=parent.passageLimit,entityHistory=state.entityHistory.dropLast(1))
                        .with(runEffect(id=EntityId,cancelInFlight=true) {})
                } ?: state.copy(entity=null,selectedEntity=null,entityRequest=null,entityLoading=false,entityFailed=false,candidates=emptyList()).with(merge(
                    runEffect(id=EntityId,cancelInFlight=true) {},
                    if(state.context==null) Effect.Send(Action.TabChanged(Tab.CONTEXT)) else Effect.None,
                ))
                is Action.EntityTypeChanged -> if(action.type !in state.entityTypes) state.only() else state.copy(selectedType=action.type,entityLimit=6).only()
                Action.MoreEntities -> state.copy(entityLimit=(state.entityLimit+6).coerceAtMost(state.matchingEntities.size)).only()
                Action.MoreReferences -> state.copy(referenceLimit=(state.referenceLimit+6).coerceAtMost(state.relatedPassages.size)).only()
                Action.MoreEntityPassages -> state.copy(entityPassageLimit=(state.entityPassageLimit+6).coerceAtMost(state.keyPassages.size)).only()
                is Action.PreviewPassage -> {
                    val request=UUID.randomUUID().toString()
                    state.copy(previewReference=action.reference,previewRequest=request,preview=null,previewLoading=true,previewFailed=false).with(runEffect(id=PreviewId,cancelInFlight=true) {send->
                        try {send(Action.PreviewLoaded(action.reference,request,bible.passage(action.reference)))} catch(e:CancellationException){throw e} catch(e:Exception){send(Action.PreviewFailed(action.reference,request))}
                    })
                }
                is Action.PreviewLoaded -> if(state.previewRequest!=action.request || state.previewReference!=action.reference) state.only() else state.copy(preview=action.passage,previewLoading=false,previewFailed=false).only()
                is Action.PreviewFailed -> if(state.previewRequest!=action.request || state.previewReference!=action.reference) state.only() else state.copy(preview=null,previewLoading=false,previewFailed=true).only()
                Action.RetryPreview -> state.previewReference?.let {state.with(Effect.Send(Action.PreviewPassage(it)))} ?: state.only()
                Action.ClosePreview -> state.copy(previewReference=null,previewRequest=null,preview=null,previewLoading=false,previewFailed=false).with(runEffect(id=PreviewId,cancelInFlight=true) {})
                Action.OpenPreviewInReader -> state.previewReference?.let {state.with(Effect.Send(Action.PassageTapped(it)))} ?: state.only()
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
