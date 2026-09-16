package com.nexussoft.verbum.models

enum class ReadingMode { PAGES, CONTINUOUS }
enum class HighlightStyle { BACKGROUND, UNDERLINE, MARGIN }
enum class HighlightColor { GOLD, SAGE, ROSE }

data class ReaderAnnotation(val reference: PassageReference, val highlight: HighlightColor? = null, val note: String = "", val highlightStyle: HighlightStyle? = null) {
    val id: String get() = "${reference.bookId}.${reference.chapter}.${reference.verses?.first ?: 1}"
}
object ReaderCanon {
    val chapters: List<PassageReference> = BibleBook.canon.flatMap { book -> (1..book.chapterCount).map { PassageReference(book.id,it) } }
    fun index(reference: PassageReference): Int = chapters.indexOfFirst { it.bookId==reference.bookId && it.chapter==reference.chapter }.coerceAtLeast(0)
    fun key(reference: PassageReference): String = "${reference.bookId}.${reference.chapter}"
}
data class StudyTextSegment(val text: String, val entityIds: List<String>)
object ReaderEntityLinker {
    fun segments(text: String, entities: List<BibleEntity>): List<StudyTextSegment> {
        val names=entities.filter { it.type in setOf(BibleEntityType.PERSON,BibleEntityType.PLACE) && it.name.length>1 }.groupBy { it.name.lowercase(java.util.Locale.ROOT) }
        if(names.isEmpty()) return listOf(StudyTextSegment(text,emptyList()))
        val regex=Regex("(?<![\\p{L}\\p{N}])("+names.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }+")(?![\\p{L}\\p{N}])",RegexOption.IGNORE_CASE)
        val result=mutableListOf<StudyTextSegment>();var end=0
        regex.findAll(text).forEach { match ->
            if(match.range.first>end) result+=StudyTextSegment(text.substring(end,match.range.first),emptyList())
            result+=StudyTextSegment(match.value,names[match.value.lowercase(java.util.Locale.ROOT)]?.map { it.id }?.sorted() ?: emptyList())
            end=match.range.last+1
        }
        if(end<text.length) result+=StudyTextSegment(text.substring(end),emptyList())
        return result
    }
}
