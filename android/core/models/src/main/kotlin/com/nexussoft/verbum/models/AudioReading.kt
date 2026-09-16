package com.nexussoft.verbum.models

data class AudioCue(val verseStart:Int,val verseEnd:Int,val start:Double,val end:Double?=null) {
    companion object {
        fun validated(cues:List<AudioCue>):List<AudioCue> {
            if(cues.isEmpty() || cues.size>256) return emptyList()
            var previousStart=-1.0;var previousEnd=0.0
            cues.forEachIndexed { index,c->
                if(c.verseStart<1 || c.verseEnd<c.verseStart || c.verseEnd>176 || !c.start.isFinite() || c.start<0 || c.start<=previousStart || c.start<previousEnd ||
                    !(c.end?.let {it.isFinite() && it>c.start} ?: (index==cues.lastIndex))) return emptyList()
                previousStart=c.start;previousEnd=c.end ?: c.start
            }
            return cues
        }
        fun active(cues:List<AudioCue>,time:Double):AudioCue? = if(!time.isFinite() || time<0) null else cues.lastOrNull {time>=it.start && (it.end?.let {end->time<end} ?: true)}
    }
}
data class AudioReadingPosition(val reference:PassageReference,val translationId:String,val cue:AudioCue,val isPlaying:Boolean) {
    fun contains(passage:BiblePassage)=isPlaying && translationId==passage.translationId && reference.bookId==passage.bookId && reference.chapter==passage.chapter && passage.verseStart in cue.verseStart..cue.verseEnd
}
