package com.nexussoft.verbum.models

/** A narrator's recording of one chapter. */
data class AudioNarrator(val id: String, val name: String, val url: String, val timingsPath: String?) {
    /** A reading the device synthesised from the translation on screen (`native.<language>`), as opposed to a person's recording. */
    val isSynthesised: Boolean get() = id.startsWith(SYNTHESISED_PREFIX)

    companion object {
        const val SYNTHESISED_PREFIX = "native."
    }
}

/** Everything needed to listen to a chapter: which translation was recorded, and who read it. */
data class ChapterAudio(val translationId: String, val translationName: String, val reference: PassageReference, val narrators: List<AudioNarrator>)
