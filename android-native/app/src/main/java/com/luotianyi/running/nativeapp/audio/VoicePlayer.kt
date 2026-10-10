package com.luotianyi.running.nativeapp.audio
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.luotianyi.running.nativeapp.domain.*

class VoicePlayer(private val context:Context,private val duck:(Boolean)->Unit) {
    private val handler=Handler(Looper.getMainLooper())
    private val queue=VoiceQueue()
    private val caption=MutableStateFlow("");val subtitle=caption.asStateFlow()
    private val currentId=MutableStateFlow("");val playingId=currentId.asStateFlow()
    val isBusy get()=queue.busy
    private var player:MediaPlayer?=null;private var ttsReady=false;private var token=0
    private var tts:TextToSpeech?=null
    var volume=.7f
        set(value) {field=value.coerceIn(0f,1f);player?.setVolume(field,field)}
    init {
        tts=TextToSpeech(context) { status -> handler.post { ttsReady=status==TextToSpeech.SUCCESS;tts?.language=Locale.SIMPLIFIED_CHINESE } }
        tts?.setOnUtteranceProgressListener(object:UtteranceProgressListener() {
            override fun onStart(id:String?) { handler.post { if(id==token.toString())showCaption() } }
            override fun onDone(id:String?) { handler.post { if(id==token.toString())done() } }
            @Deprecated("Platform API") override fun onError(id:String?) { onDone(id) }
        })
    }
    // Known recordings always use the canonical transcript, including callers of the old API.
    fun speak(id:String,text:String)=speak(VoiceCatalog.find(id) ?: VoiceLine(id,text,VoiceCategory.CHEER))
    fun speak(line:VoiceLine,priority:VoicePriority=VoiceCatalog.priority(line.category),
              lifetimeMs:Long=30_000,allowQueue:Boolean=false):Boolean {
        val now=SystemClock.elapsedRealtime()
        val expiry=if(priority>=VoicePriority.CONTROL)Long.MAX_VALUE else now+lifetimeMs
        val result=queue.offer(VoiceRequest(line,priority,expiry,allowQueue),now)
        if(result==VoiceQueue.Result.DROPPED)return false
        if(result==VoiceQueue.Result.INTERRUPT)stopPlayback()
        next();return true
    }
    fun preview(line:VoiceLine) {cancel();speak(line,VoicePriority.CONTROL)}
    fun cancel() {queue.clear();stopPlayback();duck(false)}
    private fun stopPlayback() {token++;player?.release();player=null;tts?.stop();caption.value="";currentId.value=""}
    private fun next() {
        val line=queue.next(SystemClock.elapsedRealtime())?.line ?: return
        val generation=++token;duck(true)
        try {
            val media=MediaPlayer();player=media
            media.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            context.assets.openFd("voices/${line.id}.wav").use {media.setDataSource(it.fileDescriptor,it.startOffset,it.length)};media.setVolume(volume,volume)
            media.setOnCompletionListener { if(generation==token)done() }
            media.setOnErrorListener { _,_,_->if(generation==token)fallback(line.text,generation);true }
            var prepared=false
            media.setOnPreparedListener {if(generation==token) {
                prepared=true;showCaption();it.start()
                handler.postDelayed({if(generation==token)done()},(it.duration.toLong()+3000).coerceAtLeast(5000))
            }}
            media.prepareAsync()
            handler.postDelayed({if(generation==token && !prepared)fallback(line.text,generation)},5000)
        } catch(_:Exception) { fallback(line.text,generation) }
    }
    private fun showCaption() {
        queue.active?.line?.let {caption.value=it.text;currentId.value=it.id}
    }
    private fun fallback(text:String,generation:Int) {
        if(generation!=token)return
        player?.release();player=null
        // Invalidate the failed player's callbacks before starting TTS.
        val fallbackToken=++token
        if(ttsReady && tts?.speak(text,TextToSpeech.QUEUE_FLUSH,android.os.Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME,volume) },fallbackToken.toString())==TextToSpeech.SUCCESS) {
            handler.postDelayed({if(fallbackToken==token)done()},30000)
        } else {showCaption();handler.postDelayed({if(fallbackToken==token)done()},3500)}
    }
    private fun done() {
        token++;player?.release();player=null;queue.complete();currentId.value="";next()
        if(!queue.busy) {
            duck(false);val generation=token
            handler.postDelayed({if(generation==token && !queue.busy)caption.value=""},1200)
        }
    }
    fun release() {cancel();tts?.shutdown();handler.removeCallbacksAndMessages(null)}
}
