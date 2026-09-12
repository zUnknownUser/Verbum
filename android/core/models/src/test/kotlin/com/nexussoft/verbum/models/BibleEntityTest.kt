package com.nexussoft.verbum.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BibleEntityTest {
    @Test
    fun entityTypeWireValuesMatchSpec() {
        // docs/PRODUCT.md §22.1 — these strings are the wire contract.
        assertEquals(
            listOf("person", "place", "event", "theme", "passage", "book", "prophecy", "originalTerm", "historicalPeriod"),
            BibleEntityType.entries.map { it.wireValue },
        )
    }

    @Test
    fun relationshipTypeWireValuesMatchSpec() {
        // docs/PRODUCT.md §22.2.
        assertEquals(
            listOf(
                "appearsIn", "participatesIn", "occursAt", "occursDuring", "references",
                "relatedToTheme", "relatedTo", "precedes", "follows", "fulfills", "quotes",
            ),
            RelationshipType.entries.map { it.wireValue },
        )
    }

    @Test
    fun wireValueRoundTrip() {
        for (type in BibleEntityType.entries) assertEquals(type, BibleEntityType.fromWireValue(type.wireValue))
        for (type in RelationshipType.entries) assertEquals(type, RelationshipType.fromWireValue(type.wireValue))
        assertNull(BibleEntityType.fromWireValue("angel"))
    }

    @Test
    fun entityIsValueType() {
        val entity = BibleEntity("fixture-david", BibleEntityType.PERSON, "David", null)
        assertEquals(entity, entity.copy())
        assertNull(entity.summary)
    }

    @Test
    fun relationshipIsValueType() {
        val edge = BibleRelationship("fixture-edge", "fixture-david", "fixture-goliath", RelationshipType.RELATED_TO, 0.9, listOf("fixture-src"))
        assertEquals(edge, edge.copy())
    }
}
