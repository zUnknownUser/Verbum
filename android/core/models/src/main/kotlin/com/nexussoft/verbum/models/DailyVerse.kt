package com.nexussoft.verbum.models

import java.time.LocalDate

/**
 * The verse of the day (docs/PRODUCT.md §14: a short daily touch that must not become the
 * centre of the product — one verse, no streak, no pseudo-prophetic frame). Twin of iOS
 * `DailyVerses`: same pool, same algorithm, so both platforms show the same verse on a day.
 *
 * The pick is deterministic per calendar day. Within each cycle of `pool.size` days no verse
 * repeats: the pool is shuffled once per cycle with a seeded generator, then walked in order.
 */
object DailyVerses {
    /** Single, widely known verses across the canon. Curated, not generated. */
    val pool: List<PassageReference> = listOf(
        PassageReference("Gen", 1, 1..1),
        PassageReference("Gen", 28, 15..15),
        PassageReference("Exod", 14, 14..14),
        PassageReference("Num", 6, 24..24),
        PassageReference("Deut", 31, 6..6),
        PassageReference("Josh", 1, 9..9),
        PassageReference("1Sam", 16, 7..7),
        PassageReference("2Sam", 22, 31..31),
        PassageReference("1Chr", 16, 34..34),
        PassageReference("2Chr", 7, 14..14),
        PassageReference("Neh", 8, 10..10),
        PassageReference("Job", 19, 25..25),
        PassageReference("Ps", 16, 11..11),
        PassageReference("Ps", 18, 2..2),
        PassageReference("Ps", 19, 14..14),
        PassageReference("Ps", 23, 1..1),
        PassageReference("Ps", 27, 1..1),
        PassageReference("Ps", 34, 8..8),
        PassageReference("Ps", 37, 4..4),
        PassageReference("Ps", 46, 1..1),
        PassageReference("Ps", 46, 10..10),
        PassageReference("Ps", 51, 10..10),
        PassageReference("Ps", 55, 22..22),
        PassageReference("Ps", 56, 3..3),
        PassageReference("Ps", 62, 1..1),
        PassageReference("Ps", 73, 26..26),
        PassageReference("Ps", 90, 12..12),
        PassageReference("Ps", 91, 1..1),
        PassageReference("Ps", 103, 2..2),
        PassageReference("Ps", 118, 24..24),
        PassageReference("Ps", 119, 105..105),
        PassageReference("Ps", 121, 1..1),
        PassageReference("Ps", 139, 14..14),
        PassageReference("Ps", 143, 8..8),
        PassageReference("Ps", 147, 3..3),
        PassageReference("Prov", 3, 5..5),
        PassageReference("Prov", 3, 6..6),
        PassageReference("Prov", 4, 23..23),
        PassageReference("Prov", 16, 9..9),
        PassageReference("Prov", 18, 10..10),
        PassageReference("Eccl", 3, 1..1),
        PassageReference("Isa", 26, 3..3),
        PassageReference("Isa", 40, 31..31),
        PassageReference("Isa", 41, 10..10),
        PassageReference("Isa", 43, 2..2),
        PassageReference("Isa", 53, 5..5),
        PassageReference("Isa", 55, 8..8),
        PassageReference("Jer", 29, 11..11),
        PassageReference("Lam", 3, 22..22),
        PassageReference("Lam", 3, 23..23),
        PassageReference("Mic", 6, 8..8),
        PassageReference("Nah", 1, 7..7),
        PassageReference("Hab", 3, 19..19),
        PassageReference("Zeph", 3, 17..17),
        PassageReference("Matt", 5, 16..16),
        PassageReference("Matt", 6, 33..33),
        PassageReference("Matt", 11, 28..28),
        PassageReference("Matt", 28, 20..20),
        PassageReference("Mark", 10, 27..27),
        PassageReference("Luke", 1, 37..37),
        PassageReference("John", 1, 5..5),
        PassageReference("John", 3, 16..16),
        PassageReference("John", 8, 12..12),
        PassageReference("John", 10, 10..10),
        PassageReference("John", 14, 6..6),
        PassageReference("John", 14, 27..27),
        PassageReference("John", 15, 5..5),
        PassageReference("John", 16, 33..33),
        PassageReference("Acts", 1, 8..8),
        PassageReference("Rom", 5, 8..8),
        PassageReference("Rom", 8, 28..28),
        PassageReference("Rom", 8, 38..38),
        PassageReference("Rom", 12, 2..2),
        PassageReference("Rom", 12, 12..12),
        PassageReference("Rom", 15, 13..13),
        PassageReference("1Cor", 10, 13..13),
        PassageReference("1Cor", 13, 4..4),
        PassageReference("1Cor", 16, 14..14),
        PassageReference("2Cor", 5, 17..17),
        PassageReference("2Cor", 12, 9..9),
        PassageReference("Gal", 2, 20..20),
        PassageReference("Gal", 5, 22..22),
        PassageReference("Eph", 2, 8..8),
        PassageReference("Eph", 2, 10..10),
        PassageReference("Eph", 3, 20..20),
        PassageReference("Phil", 1, 6..6),
        PassageReference("Phil", 4, 6..6),
        PassageReference("Phil", 4, 8..8),
        PassageReference("Phil", 4, 13..13),
        PassageReference("Col", 3, 23..23),
        PassageReference("1Thess", 5, 16..16),
        PassageReference("1Thess", 5, 18..18),
        PassageReference("2Tim", 1, 7..7),
        PassageReference("Heb", 4, 16..16),
        PassageReference("Heb", 11, 1..1),
        PassageReference("Heb", 12, 2..2),
        PassageReference("Heb", 13, 8..8),
        PassageReference("Jas", 1, 5..5),
        PassageReference("1Pet", 5, 7..7),
        PassageReference("1John", 4, 19..19),
        PassageReference("Rev", 21, 4..4),
    )

    /** The verse for a civil date. [epochDay] counts days since 1970-01-01 ([LocalDate.toEpochDay]). */
    fun verse(epochDay: Long): PassageReference {
        val count = pool.size
        val cycle = Math.floorDiv(epochDay, count.toLong())
        val position = Math.floorMod(epochDay, count.toLong()).toInt()
        return pool[permutation(cycle.toULong())[position]]
    }

    fun verse(date: LocalDate): PassageReference = verse(date.toEpochDay())

    /** Fisher–Yates over the pool indices, driven by splitmix64 from [seed]. */
    internal fun permutation(seed: ULong): IntArray {
        val indices = IntArray(pool.size) { it }
        var state = seed
        var i = indices.size - 1
        while (i > 0) {
            state += 0x9E3779B97F4A7C15uL
            var z = state
            z = (z xor (z shr 30)) * 0xBF58476D1CE4E5B9uL
            z = (z xor (z shr 27)) * 0x94D049BB133111EBuL
            z = z xor (z shr 31)
            val j = (z % (i + 1).toULong()).toInt()
            val tmp = indices[i]; indices[i] = indices[j]; indices[j] = tmp
            i -= 1
        }
        return indices
    }
}
