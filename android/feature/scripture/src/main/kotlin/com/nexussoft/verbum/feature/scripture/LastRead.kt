package com.nexussoft.verbum.feature.scripture

import com.nexussoft.verbum.models.PassageReference

/** `bookId chapter` ⇄ reference, for the preferences store. */
object LastRead {
    fun encode(reference: PassageReference) = "${reference.bookId} ${reference.chapter}"
    fun decode(value: String?): PassageReference? {
        val parts = value?.split(' ') ?: return null
        if (parts.size != 2) return null
        return PassageReference(parts[0], parts[1].toIntOrNull() ?: return null)
    }
}
