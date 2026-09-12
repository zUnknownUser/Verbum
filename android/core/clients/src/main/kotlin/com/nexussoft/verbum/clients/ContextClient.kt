package com.nexussoft.verbum.clients

import com.nexussoft.verbum.models.PassageContext
import com.nexussoft.verbum.models.PassageReference

fun interface ContextClient {
    suspend fun chapter(reference: PassageReference): PassageContext?
}
