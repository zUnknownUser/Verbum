package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.clients.AskScriptureClient
import com.nexussoft.verbum.clients.SearchClient
import com.nexussoft.verbum.clients.VoiceConfiguration
import com.nexussoft.verbum.clients.VoiceTool
import com.nexussoft.verbum.common.PassageReferenceParser
import com.nexussoft.verbum.models.BookLanguage
import com.nexussoft.verbum.models.PassageReference
import com.nexussoft.verbum.models.formatted
import com.nexussoft.verbum.models.VoiceContext
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * What the companion is told and what it may do. The instructions carry the product's rules
 * (§3.5, §13.3, §31) and the page's content; the tools are how anything beyond that page is
 * answered — through the same retrieval-first `/v1/ask` and `/v1/search` as the rest of the app,
 * never from memory (§73). Twin of iOS `VoiceScript`.
 */
object VoiceScript {
    val askTool = VoiceTool(
        "ask_scripture",
        "Answer a Bible question from Scripture itself. Returns a short answer with the passages it rests on and a confidence. Use it for anything not already in the page you were given.",
        """{"type":"object","properties":{"question":{"type":"string","description":"The question, in the app language, in one sentence."}},"required":["question"]}""",
    )
    val searchTool = VoiceTool(
        "search_scripture",
        "Find passages, people, places and themes for a word or phrase.",
        """{"type":"object","properties":{"query":{"type":"string"}},"required":["query"]}""",
    )
    val openTool = VoiceTool(
        "open_passage",
        "Open a passage in the reader for the user. Only after they agreed to it.",
        """{"type":"object","properties":{"reference":{"type":"string","description":"An English reference such as \"1 Samuel 17:45-47\" or \"John 3\"."}},"required":["reference"]}""",
    )

    fun configuration(context: VoiceContext, chapterText: String?, language: BookLanguage): VoiceConfiguration {
        val spoken = if (language == BookLanguage.PORTUGUESE) "Brazilian Portuguese" else "English"
        val lines = mutableListOf(
            "You are the study companion inside Verbum, a Bible exploration app. You talk with the reader about the page they have open, in $spoken.",
            "Turn-taking: answer only what was just asked, in one to three short sentences, then stop and wait. Never keep talking on your own, never add unasked-for material, never ask more than one question at a time. If what you heard was not a question about Scripture — a greeting, small talk, an unclear fragment — reply in one short sentence and wait. If you are not sure what was said, say so briefly and ask them to repeat.",
            "Stay with Scripture: its text, its people, places, history and how it has been read. When a question needs passages beyond this page, call ask_scripture and answer from what it returns; say which passages you are drawing on, by reference. Never quote or cite a verse you were not given by the page or by a tool.",
            "Distinguish what the text says from how traditions interpret it, and say when scholars or traditions disagree. Say plainly when you do not know.",
            "Never claim revelation from God, foretell the reader's future, promise healing or outcomes, or bless a personal decision as God's will. Prefer: \"this passage has traditionally been read as…\". For a question that is really about medical, legal, financial or mental-health help, say so kindly and point to a professional.",
            "If the reader wants to read something you mentioned, offer to open it and call open_passage only after they agree.",
        )
        when (context) {
            is VoiceContext.Chapter -> {
                lines += "The reader has ${context.reference.formatted(BookLanguage.ENGLISH)} open. Its text (the translation currently selected in the reader):"
                chapterText?.let { lines += it.take(12_000) }
            }
            is VoiceContext.Entity -> {
                val d = context.detail
                val facts = mutableListOf("The reader is on the page for ${d.entity.name} (${d.entity.type.wireValue}).")
                d.entity.summary?.let { facts += it }
                if (d.aliases.isNotEmpty()) facts += "Also called: ${d.aliases.joinToString(", ")}."
                d.role?.let { facts += "Role: $it." }
                d.approximateDates?.let { facts += "Dates: $it." }
                d.modernGeography?.let { facts += "Today: $it." }
                if (d.keyPassages.isNotEmpty()) facts += "Key passages: ${d.keyPassages.joinToString("; ") { it.formatted(BookLanguage.ENGLISH) }}."
                lines += facts.joinToString(" ")
            }
            is VoiceContext.Answer -> {
                val a = context.answer
                lines += "The reader just asked Verbum: \"${context.question}\". The written answer, grounded in ${a.passageReferences.joinToString("; ") { it.formatted(BookLanguage.ENGLISH) }}:"
                lines += a.answer.ifBlank { a.summary }
                if (a.interpretiveVariance) lines += "Traditions differ on this question; say so if it comes up."
            }
        }
        val pt = language == BookLanguage.PORTUGUESE
        val opening = when (context) {
            is VoiceContext.Chapter -> if (pt) "Estou com ${context.reference.formatted(BookLanguage.PORTUGUESE)} aberto com você. O que quer entender melhor?" else "I have ${context.reference.formatted(BookLanguage.ENGLISH)} open with you. What would you like to understand better?"
            is VoiceContext.Entity -> if (pt) "Vamos falar de ${context.detail.entity.name}. Pode perguntar." else "Let's talk about ${context.detail.entity.name}. Ask away."
            is VoiceContext.Answer -> if (pt) "Li a resposta com você. Onde quer ir a partir daqui?" else "I've read the answer with you. Where would you like to go from here?"
        }
        return VoiceConfiguration(lines.joinToString("\n\n"), opening, listOf(askTool, searchTool, openTool), "marin", language.tag)
    }

    /** Runs one tool for the model. Results are JSON text; references are given in English so the model can say them and hand them back to `open_passage`. */
    suspend fun run(
        name: String,
        argumentsJson: String,
        ask: AskScriptureClient,
        search: SearchClient,
        mentioned: suspend (List<PassageReference>) -> Unit,
        open: suspend (PassageReference) -> Unit,
    ): String {
        val arguments = runCatching { Json.parseToJsonElement(argumentsJson).jsonObject }.getOrNull() ?: JsonObject(emptyMap())
        fun arg(key: String) = (arguments[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
        return when (name) {
            askTool.name -> {
                val question = arg("question") ?: return error("question is required")
                try {
                    val answer = ask.ask(question)
                    mentioned(answer.passageReferences)
                    buildJsonObject {
                        put("answer", answer.answer.ifBlank { "No reliable, citable answer was found. Say so; do not answer from memory." })
                        put("summary", answer.summary)
                        put("passages", buildJsonArray { answer.passageReferences.forEach { add(it.formatted(BookLanguage.ENGLISH)) } })
                        put("confidence", answer.confidence.wireValue)
                        put("traditionsDisagree", answer.interpretiveVariance)
                    }.toString()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    error("Ask is not available right now. Say so; do not answer from memory.")
                }
            }
            searchTool.name -> {
                val query = arg("query") ?: return error("query is required")
                try {
                    val response = search.search(query)
                    mentioned(response.passages)
                    buildJsonObject {
                        put("passages", buildJsonArray { response.passages.forEach { add(it.formatted(BookLanguage.ENGLISH)) } })
                        put("entities", buildJsonArray {
                            response.entities.take(8).forEach { e -> add(buildJsonObject { put("name", e.name); put("kind", e.type.wireValue); put("summary", e.summary ?: "") }) }
                        })
                    }.toString()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    error("Search is not available right now.")
                }
            }
            openTool.name -> {
                val reference = arg("reference")?.let { PassageReferenceParser.parse(it, BookLanguage.ENGLISH).referenceOrNull }
                    ?: return error("not a reference I can open")
                open(reference)
                buildJsonObject { put("opened", reference.formatted(BookLanguage.ENGLISH)) }.toString()
            }
            else -> error("unknown tool $name")
        }
    }

    private fun error(message: String) = buildJsonObject { put("error", message) }.toString()
}
