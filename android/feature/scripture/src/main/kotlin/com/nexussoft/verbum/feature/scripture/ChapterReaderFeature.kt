package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.clients.*
import com.nexussoft.verbum.common.arch.*
import com.nexussoft.verbum.models.*
import kotlinx.coroutines.CancellationException

/** Native pages and the continuous flow share a single reader domain and bounded prefetch. */
object ChapterReaderFeature {
    data class Visit(val reference: PassageReference,val flow: List<PassageReference>,val index: Int,val offset: Int)
    data class Position(val index: Int=0,val offset: Int=0)
    data class State(
        val reference: PassageReference,
        val content: Content = Content.Idle,
        val selectedVerses: Set<Int> = emptySet(),
        val textScale: ReaderTextScale = ReaderTextScale.STANDARD,
        val requestedVerses: IntRange? = reference.verses,
        val readingMode: ReadingMode = ReadingMode.PAGES,
        val focusMode: Boolean=false,
        val study: VerseStudyFeature.State?=null,
        val chapters: Map<String,List<BiblePassage>> = emptyMap(),
        val chapterErrors: Map<String,ReaderError> = emptyMap(),
        val loadingChapters: Set<String> = emptySet(),
        val contexts: Map<String,PassageContext> = emptyMap(),
        val mentions: Map<String,List<StudyTextSegment>> = emptyMap(),
        val annotations: Map<String,ReaderAnnotation> = emptyMap(),
        val annotationLoadFailed: Boolean=false,
        val flow: List<PassageReference> = listOf(PassageReference(reference.bookId,reference.chapter)),
        val history: List<Visit> = emptyList(),
        val positions: Map<String,Position> = emptyMap(),
        val restorePosition: Position?=null,
        val navigationRevision: Int=0,
    ) {
        constructor(reference: PassageReference,textScale: ReaderTextScale):this(PassageReference(reference.bookId,reference.chapter),Content.Idle,emptySet(),textScale,reference.verses)
        val book: BibleBook? get()=BibleBook.book(reference.bookId)
        val title: String get()=reference.formatted
        val canGoToNextChapter get()=ChapterNavigation.next(reference)!=null
        val canGoToPreviousChapter get()=ChapterNavigation.previous(reference)!=null
        val selectionCitation: String? get()=SelectionFormatter.format(reference.bookId,reference.chapter,selectedVerses)
        val selectedText: String? get()=(content as? Content.Loaded)?.verses?.filter { it.verseStart in selectedVerses }?.takeIf { it.isNotEmpty() }?.joinToString(" ") { it.text }
    }
    sealed interface Content {
        data object Idle:Content;data object Loading:Content
        data class Loaded(val verses: List<BiblePassage>):Content
        data class Failed(val error: ReaderError):Content
    }
    sealed interface Action {
        data object Started:Action;data object RetryTapped:Action
        data class ChapterLoaded(val verses:List<BiblePassage>):Action
        data class ChapterFailed(val error:ReaderError):Action
        data class EnsureChapter(val reference:PassageReference):Action
        data class CachedChapter(val reference:PassageReference,val verses:List<BiblePassage>?,val error:ReaderError?=null):Action
        data class ContextLoaded(val reference:PassageReference,val context:PassageContext?):Action
        data class AnnotationsLoaded(val values:List<ReaderAnnotation>):Action
        data object AnnotationsFailed:Action
        data class PreferencesLoaded(val mode:ReadingMode,val focus:Boolean):Action
        data class Study(val action:VerseStudyFeature.Action):Action
        data object StudyDismissed:Action
        data class StudyVerse(val reference:PassageReference,val verse:Int,val ids:List<String> = emptyList()):Action
        data class VerseTapped(val verse:Int):Action
        data object ClearSelectionTapped:Action;data object CopySelectionTapped:Action
        data object NextChapterTapped:Action;data object PreviousChapterTapped:Action
        data class Go(val reference:PassageReference):Action
        data class VisitPassage(val reference:PassageReference):Action
        data object BackToReading:Action;data object AppendChapter:Action
        data class ChapterVisible(val reference:PassageReference):Action
        data class PositionChanged(val key:String,val index:Int,val offset:Int):Action
        data class TextScaleChanged(val scale:ReaderTextScale):Action
        data class ModeChanged(val mode:ReadingMode):Action
        data class FocusChanged(val enabled:Boolean):Action
        data object FocusToggled:Action
        data object ListenTapped:Action;data object TalkTapped:Action;data object ContextTapped:Action
        data class Delegate(val delegate:DelegateAction):Action
    }
    sealed interface DelegateAction {
        data class Listen(val reference:PassageReference):DelegateAction
        data class Talk(val reference:PassageReference):DelegateAction
        data class OpenContext(val reference:PassageReference):DelegateAction
    }
    // Requests are keyed by canonical chapter identity.
    const val LAST_READ_KEY="lastRead"
    const val MODE_KEY="readingMode"
    const val FOCUS_KEY="readerFocusMode"
    fun reducer(bibleClient:BibleClient,clipboard:ClipboardClient,preferences:PreferencesClient,
        contextClient:ContextClient = com.nexussoft.verbum.clients.fixtures.FixtureContextClient,
        graphClient:GraphClient = com.nexussoft.verbum.clients.fixtures.FixtureGraphClient,
        askClient:AskScriptureClient = AskScriptureClient { throw AskScriptureException.Unavailable },
        annotations:ReaderAnnotationsClient = PreferenceReaderAnnotationsClient(preferences),
        comparison:BibleComparisonClient = LiveBibleComparisonClient(),
    ):Reducer<State,Action> = combine(
        VerseStudyFeature.reducer(contextClient,graphClient,askClient,annotations,comparison).pullbackOptional(
            get={it.study},set={s,c->s.copy(study=c)},extractAction={(it as? Action.Study)?.action},embedAction={Action.Study(it)},
        ),
        Reducer {state,action ->
            fun context(reference:PassageReference):Effect<Action> = runEffect(id="context-${ReaderCanon.key(reference)}",cancelInFlight=true) {send->
                preferences.setString(LAST_READ_KEY,LastRead.encode(reference))
                val value=try {contextClient.chapter(reference)} catch(e:CancellationException){throw e} catch(e:Exception){null}
                send(Action.ContextLoaded(reference,value))
            }
            when(action) {
                Action.Started -> state.with(runEffect {send->
                    val mode=preferences.string(MODE_KEY)?.let { runCatching { ReadingMode.valueOf(it) }.getOrNull() } ?: ReadingMode.PAGES
                    send(Action.PreferencesLoaded(mode,preferences.string(FOCUS_KEY)=="true"))
                    try {send(Action.AnnotationsLoaded(annotations.load()))} catch(e:CancellationException){throw e} catch(e:Exception){send(Action.AnnotationsFailed)}
                    if(state.content !is Content.Loaded) send(Action.RetryTapped)
                })
                Action.RetryTapped -> load(state,bibleClient)
                is Action.PreferencesLoaded -> state.copy(readingMode=action.mode,focusMode=action.focus).only()
                is Action.AnnotationsLoaded -> state.copy(annotations=action.values.associateBy { it.id },annotationLoadFailed=false).only()
                Action.AnnotationsFailed -> state.copy(annotationLoadFailed=true).only()
                is Action.ChapterLoaded -> {
                    val first=action.verses.firstOrNull()
                    if(first!=null && (first.bookId!=state.reference.bookId || first.chapter!=state.reference.chapter)) return@Reducer state.only()
                    val key=ReaderCanon.key(state.reference)
                    state.copy(content=Content.Loaded(action.verses),chapters=state.chapters+(key to action.verses),loadingChapters=state.loadingChapters-key,
                        selectedVerses=state.requestedVerses?.let {range->action.verses.map {it.verseStart}.filter {it in range}.toSet()} ?: emptySet()).with(Effect.Merge(listOf(
                            context(state.reference),runEffect {send->
                                preferences.setString(LAST_READ_KEY,LastRead.encode(state.reference))
                                ChapterNavigation.previous(state.reference)?.let {send(Action.EnsureChapter(it))}
                                ChapterNavigation.next(state.reference)?.let {send(Action.EnsureChapter(it))}
                            },
                        )))
                }
                is Action.ChapterFailed -> state.copy(content=Content.Failed(action.error),loadingChapters=state.loadingChapters-ReaderCanon.key(state.reference),chapterErrors=state.chapterErrors+(ReaderCanon.key(state.reference) to action.error)).only()
                is Action.EnsureChapter -> {
                    val key=ReaderCanon.key(action.reference)
                    if(key in state.chapters || key in state.loadingChapters) state.only()
                    else state.copy(loadingChapters=state.loadingChapters+key,chapterErrors=state.chapterErrors-key).with(runEffect(id="chapter-$key",cancelInFlight=true) {send->
                        try {send(Action.CachedChapter(action.reference,bibleClient.chapter(action.reference.bookId,action.reference.chapter)))}
                        catch(e:CancellationException){throw e} catch(e:Exception){send(Action.CachedChapter(action.reference,null,ReaderError.from(e)))}
                    })
                }
                is Action.CachedChapter -> {
                    val key=ReaderCanon.key(action.reference)
                    if(action.verses!=null) state.copy(chapters=state.chapters+(key to action.verses),loadingChapters=state.loadingChapters-key,chapterErrors=state.chapterErrors-key,
                        content=if(state.reference==action.reference) Content.Loaded(action.verses) else state.content).with(if(state.reference==action.reference) Effect.Send(Action.ChapterLoaded(action.verses)) else Effect.None)
                    else state.copy(loadingChapters=state.loadingChapters-key,chapterErrors=state.chapterErrors+(key to (action.error ?: ReaderError.from(Exception()))),
                        content=if(state.reference==action.reference) Content.Failed(action.error ?: ReaderError.from(Exception())) else state.content).only()
                }
                is Action.ContextLoaded -> {
                    val value=action.context
                    if(value==null) state.only() else {
                        val key=ReaderCanon.key(action.reference)
                        val mentions=state.chapters[key].orEmpty().associate { "${it.bookId}.${it.chapter}.${it.verseStart}" to ReaderEntityLinker.segments(it.text,value.entities) }
                        state.copy(contexts=state.contexts+(key to value),mentions=state.mentions+mentions).only()
                    }
                }
                is Action.StudyVerse -> {
                    val verse=state.chapters[ReaderCanon.key(action.reference)]?.firstOrNull {it.verseStart==action.verse}
                    if(verse==null) state.only() else {
                        val value=state.contexts[ReaderCanon.key(action.reference)]
                        state.copy(study=VerseStudyFeature.State.initial(verse,state.annotations["${verse.bookId}.${verse.chapter}.${verse.verseStart}"],value,value?.entities?.filter {it.id in action.ids}.orEmpty())).only()
                    }
                }
                is Action.Study -> when(val delegate=(action.action as? VerseStudyFeature.Action.Delegate)?.value) {
                    is VerseStudyFeature.DelegateAction.AnnotationSaved -> state.copy(annotations=state.annotations+(delegate.value.id to delegate.value)).only()
                    is VerseStudyFeature.DelegateAction.OpenPassage -> state.copy(study=null).with(Effect.Send(Action.VisitPassage(delegate.value)))
                    VerseStudyFeature.DelegateAction.Close -> state.copy(study=null).only()
                    null -> state.only()
                }
                Action.StudyDismissed -> if(state.study?.let {it.saving || it.savedNote!=it.annotation.note}==true) state.only() else state.copy(study=null).only()
                is Action.VerseTapped -> state.with(Effect.Send(Action.StudyVerse(state.reference,action.verse)))
                Action.ClearSelectionTapped -> state.copy(selectedVerses=emptySet()).only()
                Action.CopySelectionTapped -> if(state.selectedText==null) state.only() else state.with(runEffect {clipboard.copy("${state.selectedText}\n— ${state.selectionCitation}")})
                is Action.VisitPassage -> {
                    val pos=state.positions[if(state.readingMode==ReadingMode.CONTINUOUS) "flow" else ReaderCanon.key(state.reference)] ?: Position()
                    jump(state.copy(history=(state.history+Visit(state.reference,state.flow,pos.index,pos.offset)).takeLast(32)),action.reference,bibleClient,::context)
                }
                Action.BackToReading -> state.history.lastOrNull()?.let {visit->
                    val jumped=jump(state.copy(history=state.history.dropLast(1)),visit.reference,bibleClient,::context)
                    jumped.copy(state=jumped.state.copy(flow=visit.flow,restorePosition=Position(visit.index,visit.offset)))
                } ?: state.only()
                Action.AppendChapter -> {
                    val last=state.flow.lastOrNull();val next=last?.let {ChapterNavigation.next(it)}
                    if(last==null || next==null || ReaderCanon.key(last) !in state.chapters) state.only()
                    else state.copy(flow=state.flow+next).with(Effect.Send(Action.EnsureChapter(next)))
                }
                is Action.ChapterVisible -> if(state.readingMode!=ReadingMode.CONTINUOUS || state.reference==action.reference) state.only() else state.copy(reference=action.reference,
                    content=state.chapters[ReaderCanon.key(action.reference)]?.let {Content.Loaded(it)} ?: state.content).with(Effect.Merge(listOf(context(action.reference),runEffect {preferences.setString(LAST_READ_KEY,LastRead.encode(action.reference))})))
                is Action.PositionChanged -> state.copy(positions=state.positions+(action.key to Position(action.index,action.offset))).only()
                Action.NextChapterTapped -> ChapterNavigation.next(state.reference)?.let {jump(state,it,bibleClient,::context)} ?: state.only()
                Action.PreviousChapterTapped -> ChapterNavigation.previous(state.reference)?.let {jump(state,it,bibleClient,::context)} ?: state.only()
                is Action.Go -> jump(state,action.reference,bibleClient,::context)
                is Action.TextScaleChanged -> state.copy(textScale=action.scale).only()
                is Action.ModeChanged -> state.copy(readingMode=action.mode,flow=listOf(state.reference),navigationRevision=state.navigationRevision+1,restorePosition=Position(),requestedVerses=null).with(runEffect {preferences.setString(MODE_KEY,action.mode.name)})
                is Action.FocusChanged -> state.copy(focusMode=action.enabled).with(runEffect {preferences.setString(FOCUS_KEY,action.enabled.toString())})
                Action.FocusToggled -> state.with(Effect.Send(Action.FocusChanged(!state.focusMode)))
                Action.ListenTapped -> state.with(Effect.Send(Action.Delegate(DelegateAction.Listen(state.reference))))
                Action.TalkTapped -> state.with(Effect.Send(Action.Delegate(DelegateAction.Talk(state.reference))))
                Action.ContextTapped -> state.with(Effect.Send(Action.Delegate(DelegateAction.OpenContext(state.reference))))
                is Action.Delegate -> state.only()
            }
        },
    )
    private fun jump(state:State,reference:PassageReference,bible:BibleClient,context:(PassageReference)->Effect<Action>):Reduced<State,Action> {
        val chapter=PassageReference(reference.bookId,reference.chapter);val key=ReaderCanon.key(chapter)
        val next=state.copy(reference=chapter,requestedVerses=reference.verses,selectedVerses=emptySet(),flow=listOf(chapter),navigationRevision=state.navigationRevision+1,
            restorePosition=if(reference.verses==null && state.readingMode==ReadingMode.PAGES) state.positions[key] ?: Position() else null)
        val verses=state.chapters[key] ?: return load(next,bible)
        return next.copy(content=Content.Loaded(verses)).with(Effect.Merge(listOf(context(chapter),runEffect {send->
            ChapterNavigation.previous(chapter)?.let {send(Action.EnsureChapter(it))};ChapterNavigation.next(chapter)?.let {send(Action.EnsureChapter(it))}
        })))
    }
    private fun load(state:State,bible:BibleClient):Reduced<State,Action> {
        return state.copy(content=Content.Loading).with(Effect.Send(Action.EnsureChapter(state.reference)))
    }
}
