package com.nexussoft.verbum.clients

import com.nexussoft.verbum.models.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

interface ReaderAnnotationsClient {
    suspend fun load(): List<ReaderAnnotation>
    suspend fun save(annotation: ReaderAnnotation)
}

/** Uses the app's existing preferences adapter; never sends personal notes to a server. */
class PreferenceReaderAnnotationsClient(private val preferences: PreferencesClient) : ReaderAnnotationsClient {
    @Serializable private data class Entry(val book: String,val chapter: Int,val verse: Int,val color: String?=null,val note: String="",val style:String?=null) {
        fun model()=ReaderAnnotation(PassageReference(book,chapter,verse..verse),color?.let { runCatching { HighlightColor.valueOf(it) }.getOrNull() },note,style?.let {runCatching {HighlightStyle.valueOf(it)}.getOrNull()})
    }
    private val lock=Any()
    private fun read(): List<ReaderAnnotation> = preferences.string("readerAnnotations")?.let { Json.decodeFromString<List<Entry>>(it).map { it.model() } } ?: emptyList()
    override suspend fun load(): List<ReaderAnnotation> = synchronized(lock) { read() }
    override suspend fun save(annotation: ReaderAnnotation) = synchronized(lock) {
        val values=read().filter { it.id!=annotation.id }.toMutableList()
        if(annotation.highlight!=null || annotation.note.isNotEmpty()) values+=annotation
        preferences.setString("readerAnnotations",Json.encodeToString(values.map { Entry(it.reference.bookId,it.reference.chapter,it.reference.verses?.first ?: 1,it.highlight?.name,it.note,it.highlightStyle?.name) }))
    }
}
