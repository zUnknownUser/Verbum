package com.nexussoft.verbum.clients

import com.nexussoft.verbum.models.*
import com.nexussoft.verbum.clients.sync.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

interface ReaderAnnotationsClient {
    suspend fun load(): List<ReaderAnnotation>
    suspend fun save(annotation: ReaderAnnotation)
}

/** Uses the app's existing preferences adapter; local writes remain available offline. */
class PreferenceReaderAnnotationsClient(private val preferences: PreferencesClient) : ReaderAnnotationsClient {
    @Serializable private data class Entry(val book: String,val chapter: Int,val verse: Int,val color: String?=null,val note: String="",val style:String?=null,val bookmarked:Boolean=false) {
        fun model()=ReaderAnnotation(PassageReference(book,chapter,verse..verse),color?.let { runCatching { HighlightColor.valueOf(it) }.getOrNull() },note,style?.let {runCatching {HighlightStyle.valueOf(it)}.getOrNull()},bookmarked)
    }
    private val lock=PersonalDataLock.monitor
    internal fun read(): List<ReaderAnnotation> = preferences.string("readerAnnotations")?.let { Json.decodeFromString<List<Entry>>(it).map { it.model() } } ?: emptyList()
    override suspend fun load(): List<ReaderAnnotation> = synchronized(lock) { read() }
    override suspend fun save(annotation: ReaderAnnotation) = write(annotation)
    private fun write(annotation: ReaderAnnotation) = synchronized(lock) {
        val values=read().filter { it.id!=annotation.id }.toMutableList()
        if(annotation.highlight!=null || annotation.note.isNotEmpty() || annotation.bookmarked) values+=annotation
        preferences.setString("readerAnnotations",Json.encodeToString(values.map { Entry(it.reference.bookId,it.reference.chapter,it.reference.verses?.first ?: 1,it.highlight?.name,it.note,it.highlightStyle?.name,it.bookmarked) }))
    }
    internal fun applySync(record: PersonalRecord, expected: PersonalValue?) = synchronized(lock) {
        val current=read().firstOrNull { "annotation:${it.id}"==record.id }
        if(current?.personalValue()!=expected) return@synchronized
        val incoming=record.value?.annotation()
        if(incoming!=null) write(incoming)
        else if(current!=null) write(current.copy(highlight=null,note="",bookmarked=false))
    }
}
