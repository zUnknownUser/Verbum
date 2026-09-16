package com.nexussoft.verbum.clients

import com.nexussoft.verbum.clients.helloao.HelloAOBibleClient
import com.nexussoft.verbum.models.*

fun interface BibleComparisonClient { suspend fun passage(reference: PassageReference): BiblePassage }
class LiveBibleComparisonClient : BibleComparisonClient {
    override suspend fun passage(reference: PassageReference): BiblePassage =
        if (BookLanguage.current==BookLanguage.PORTUGUESE) HelloAOBibleClient("por_bsl").passage(reference)
        else BundledBibleClient.passage(reference)
}
