package com.nexussoft.verbum.models

import java.text.Normalizer
import java.util.Locale

data class EntityCatalogRequest(val type: BibleEntityType, val query: String = "", val letter: String = "", val offset: Int = 0, val limit: Int = 30, val category: String = "")
data class EntityCatalogPage(val entities: List<BibleEntity>, val letters: List<String>, val nextOffset: Int? = null)

object EntityCatalog {
    private val fixtureCategories = mapOf(
        "fixture.theme.faith" to listOf("with-god", "foundations"),
        "fixture.theme.prayer" to listOf("with-god", "emotions"),
        "fixture.theme.love" to listOf("relationships", "with-god"),
        "fixture.theme.forgiveness" to listOf("relationships", "foundations"),
        "fixture.theme.anxiety" to listOf("emotions"),
        "fixture.theme.suffering" to listOf("emotions"),
        "fixture.theme.money" to listOf("daily-life"),
        "fixture.theme.justice" to listOf("daily-life", "community"),
        "fixture.theme.wisdom" to listOf("character", "daily-life"),
        "fixture.theme.grace" to listOf("foundations")
    )

    fun normalized(name: String) = Normalizer.normalize(name.trim(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
    fun letter(name: String): String = normalized(name).firstOrNull()?.takeIf { it in 'a'..'z' }?.uppercaseChar()?.toString() ?: "#"
    /** Fixture-only; live pagination is performed by the API. */
    fun page(entities: List<BibleEntity>, request: EntityCatalogRequest): EntityCatalogPage {
        val matching = entities.filter { (request.category.isEmpty() || request.category in fixtureCategories[it.id].orEmpty()) && it.type == request.type && normalized(request.query) in normalized(it.name) }
        val letters = matching.map { letter(it.name) }.distinct().sorted()
        val selected = matching.filter { request.letter.isEmpty() || letter(it.name) == request.letter }
            .sortedWith(compareBy({ letter(it.name) }, { normalized(it.name) }, { it.id }))
        val page = selected.drop(request.offset).take(request.limit)
        val next = request.offset + page.size
        return EntityCatalogPage(page, letters, next.takeIf { it < selected.size })
    }
}
