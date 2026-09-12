package com.nexussoft.verbum.clients.helloao

import com.nexussoft.verbum.models.BiblePassage
import com.nexussoft.verbum.models.BookId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Decodes `GET /api/{translation}/{book}/{chapter}.json` into verse-level passages.
 * Keeps verse text and poetry line breaks; headings, footnotes and word-of-Jesus marks
 * are parsed past but not yet surfaced. Mirrors iOS `HelloAOChapter`.
 */
internal object HelloAOChapter {
    private val json = Json { ignoreUnknownKeys = true }

    fun passages(body: String, bookId: BookId): List<BiblePassage> {
        val root = json.parseToJsonElement(body).jsonObject
        val translationId = root.getValue("translation").jsonObject.getValue("id").jsonPrimitive.content
        val chapterObj = root.getValue("chapter").jsonObject
        val chapter = chapterObj.getValue("number").jsonPrimitive.int
        return chapterObj.getValue("content").jsonArray.mapNotNull { block ->
            val obj = block.jsonObject
            if (obj["type"]?.jsonPrimitive?.content != "verse") return@mapNotNull null
            val number = obj.getValue("number").jsonPrimitive.int
            val text = join(obj["content"]?.jsonArray ?: return@mapNotNull null)
            BiblePassage("$translationId:$bookId.$chapter.$number", translationId, bookId, chapter, number, number, text)
        }
    }

    /** Inline runs join with spaces; poetry lines and explicit breaks join with newlines. */
    private fun join(parts: List<JsonElement>): String {
        val out = StringBuilder()
        var lastPoemLine: Int? = null
        for (part in parts) {
            when (part) {
                is JsonPrimitive -> append(out, part.content)
                is JsonObject -> when {
                    "noteId" in part -> Unit
                    "text" in part -> {
                        val poem = part["poem"]?.jsonPrimitive?.int
                        if (poem != null && lastPoemLine != null && poem != lastPoemLine) out.append('\n')
                        append(out, part.getValue("text").jsonPrimitive.content)
                        if (poem != null) lastPoemLine = poem
                    }
                    part["lineBreak"]?.jsonPrimitive?.boolean == true -> out.append('\n')
                }
                else -> Unit
            }
        }
        return out.split('\n').joinToString("\n") { it.trim() }
    }

    private fun append(out: StringBuilder, s: String) {
        if (out.isNotEmpty() && !out.endsWith("\n") && !out.endsWith(" ") && !s.startsWith(" ") && s.firstOrNull()?.let { it.isLetterOrDigit() || it == '“' } == true) out.append(' ')
        out.append(s)
    }
}
