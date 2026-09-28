package com.nexussoft.verbum.clients.sync

import com.nexussoft.verbum.clients.*
import com.nexussoft.verbum.models.*
import java.time.Instant
import java.time.LocalDate

internal object PersonalDataLock { val monitor = Any() }
internal fun ReaderAnnotation.personalValue() = PersonalValue(
    book = reference.bookId, chapter = reference.chapter, verse = reference.verses?.first ?: 1,
    highlight = highlight?.name?.lowercase(), style = highlightStyle?.name?.lowercase(),
    note = note.takeIf { it.isNotEmpty() }, bookmarked = true.takeIf { bookmarked },
)
internal fun PersonalValue.annotation(): ReaderAnnotation? {
    val book = book ?: return null; val chapter = chapter ?: return null; val verse = verse ?: return null
    return ReaderAnnotation(PassageReference(book, chapter, verse..verse), highlight?.let { HighlightColor.valueOf(it.uppercase()) }, note ?: "", style?.let { HighlightStyle.valueOf(it.uppercase()) }, bookmarked == true)
}
class PersonalDataStorage(private val preferences: PreferencesClient) {
    private val annotations = PreferenceReaderAnnotationsClient(preferences)
    private val activity = ReadingActivityClient(preferences)
    suspend fun snapshot(): Map<String, PersonalValue> = synchronized(PersonalDataLock.monitor) {
        buildMap {
            annotations.read().forEach { put("annotation:${it.id}", it.personalValue()) }
            val history = activity.load()
            history.visits.forEach { put("visit:${ReaderCanon.key(it.reference)}", PersonalValue(book=it.reference.bookId, chapter=it.reference.chapter, time=it.lastOpened)) }
            history.days.forEach { day -> val normalized=LocalDate.parse(day).toString(); put("day:$normalized",PersonalValue(day=normalized)) }
            position()?.let { put("position:last",it) }
        }
    }
    private fun position(): PersonalValue? {
        val parts = preferences.string("lastRead")?.split(' ') ?: return null
        if(parts.size!=2) return null
        val chapter=parts[1].toIntOrNull() ?: return null
        val reference=PassageReference(parts[0],chapter)
        return PersonalValue(book=parts[0],chapter=chapter,time=activity.load().visits.firstOrNull { it.reference==reference }?.lastOpened ?: 1)
    }
    suspend fun apply(record: PersonalRecord, expected: PersonalValue?) = synchronized(PersonalDataLock.monitor) {
        when {
            record.id.startsWith("annotation:") -> annotations.applySync(record,expected)
            record.id.startsWith("visit:") -> record.value?.let { value ->
                if(value.book!=null && value.chapter!=null && value.time!=null) activity.mergeVisit(PassageReference(value.book,value.chapter),value.time)
            }
            record.id.startsWith("day:") -> record.value?.day?.let { activity.mergeDay(it) }
            record.id=="position:last" && position()==expected -> record.value?.let { value ->
                if(value.book!=null && value.chapter!=null) preferences.setString("lastRead","${value.book} ${value.chapter}")
            }
        }
        Unit
    }
}
