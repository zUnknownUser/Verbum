package com.nexussoft.verbum.clients.api

import com.nexussoft.verbum.models.AudioCue
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable internal data class WireSpeechVerse(val number:Int,val text:String)
@Serializable internal data class WireTimedSpeech(val bookId:String,val chapter:Int,val translation:String,val text:String,val language:String,val revision:String?,val verses:List<WireSpeechVerse>)
@Serializable data class WireAudioCue(val verseStart:Int,val verseEnd:Int,val start:Double,val end:Double?=null)
private val audioJSON=Json {ignoreUnknownKeys=true}
fun decodeAudioCues(value:String?):List<AudioCue> = runCatching {
    AudioCue.validated(audioJSON.decodeFromString(ListSerializer(WireAudioCue.serializer()),value ?: "[]").map {AudioCue(it.verseStart,it.verseEnd,it.start,it.end)})
}.getOrDefault(emptyList())
fun encodeAudioCues(cues:List<AudioCue>):String = audioJSON.encodeToString(ListSerializer(WireAudioCue.serializer()),cues.map {WireAudioCue(it.verseStart,it.verseEnd,it.start,it.end)})

@Serializable data class SpeechPlaybackStatus(val ready:Boolean,val complete:Boolean,val playlistPath:String,val audioPath:String,val cues:List<WireAudioCue>?=null)
@Serializable internal data class SpeechPlaybackStart(val statusPath:String)
