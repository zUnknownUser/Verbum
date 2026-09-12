package com.nexussoft.verbum.clients

import com.nexussoft.verbum.models.*

/** Replace this boundary with retrieval later. No AI or network in the preview. */
fun interface GuidedExplorationClient {
    suspend fun explore(request: ExplorationRequest): ExplorationPlan
}

object EditorialExplorationClient : GuidedExplorationClient {
    override suspend fun explore(request: ExplorationRequest): ExplorationPlan {
        val pt = request.language == BookLanguage.PORTUGUESE
        val (passages, question) = when (request.feeling) {
            ArrivalFeeling.ANXIOUS -> listOf(PassageReference("Matt", 6, 25..34), PassageReference("Phil", 4, 4..9)) to
                (if (pt) "Observe como a passagem aborda preocupações e o dia de hoje." else "Notice how the passage addresses worry and the present day.")
            ArrivalFeeling.LOST -> listOf(PassageReference("Ps", 23, 1..6), PassageReference("Jas", 1, 5..8)) to
                (if (pt) "Explore as imagens de orientação e os pedidos de sabedoria." else "Explore images of guidance and requests for wisdom.")
            ArrivalFeeling.GRATEFUL -> listOf(PassageReference("Ps", 103, 1..5), PassageReference("1Thess", 5, 16..18)) to
                (if (pt) "Leia os motivos de gratidão e o contexto em que ela aparece." else "Read the reasons for gratitude and the context in which it appears.")
            ArrivalFeeling.TIRED -> listOf(PassageReference("Matt", 11, 28..30), PassageReference("Ps", 23, 1..6)) to
                (if (pt) "Observe como descanso e cuidado são apresentados nestes textos." else "Notice how rest and care are presented in these texts.")
            ArrivalFeeling.AFRAID -> listOf(PassageReference("Ps", 56, 1..4), PassageReference("1Sam", 17, 41..50)) to
                (if (pt) "Compare uma oração diante do medo com uma narrativa de enfrentamento." else "Compare a prayer in the face of fear with a narrative of confrontation.")
            ArrivalFeeling.ALONE -> listOf(PassageReference("Ps", 139, 1..12), PassageReference("Rom", 8, 31..39)) to
                (if (pt) "Explore as imagens de presença e vínculo, lendo além de um verso isolado." else "Explore images of presence and connection beyond an isolated verse.")
            ArrivalFeeling.ANGRY -> listOf(PassageReference("Jas", 1, 19..20), PassageReference("Eph", 4, 26..32)) to
                (if (pt) "Observe as relações entre escuta, palavras e maneiras de agir." else "Notice the connections between listening, words and ways of acting.")
            ArrivalFeeling.HOPELESS -> listOf(PassageReference("Lam", 3, 21..26), PassageReference("Rom", 8, 18..25)) to
                (if (pt) "Leia esperança junto do sofrimento ao redor, sem apagar a dificuldade." else "Read hope alongside the surrounding suffering, without erasing the difficulty.")
            ArrivalFeeling.PEACEFUL -> listOf(PassageReference("Phil", 4, 4..9), PassageReference("Ps", 23, 1..6)) to
                (if (pt) "Explore como paz e confiança aparecem no conjunto da passagem." else "Explore how peace and trust appear within the whole passage.")
        }
        return ExplorationPlan(request, question, passages, true)
    }
}
