package com.luotianyi.running.nativeapp.audio
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class VoicePlayer(private val context:Context,private val duck:(Boolean)->Unit) {
    private val handler=Handler(Looper.getMainLooper())
    private val queue=ArrayDeque<Pair<String,String>>()
    private val caption=MutableStateFlow("");val subtitle=caption.asStateFlow()
    private var player:MediaPlayer?=null;private var busy=false;private var ttsReady=false;private var token=0
    private var tts:TextToSpeech?=null
    var volume=.7f
        set(value) {field=value.coerceIn(0f,1f);player?.setVolume(field,field)}
    init {
        tts=TextToSpeech(context) { status -> handler.post { ttsReady=status==TextToSpeech.SUCCESS;tts?.language=Locale.SIMPLIFIED_CHINESE } }
        tts?.setOnUtteranceProgressListener(object:UtteranceProgressListener() {
            override fun onStart(id:String?) {}
            override fun onDone(id:String?) { handler.post { if(id==token.toString())done() } }
            @Deprecated("Platform API") override fun onError(id:String?) { onDone(id) }
        })
    }
    fun speak(id:String,text:String) { queue.addLast(id to text);next() }
    fun cancel() { token++;queue.clear();busy=false;player?.release();player=null;tts?.stop();caption.value="";duck(false) }
    private fun next() {
        if(busy || queue.isEmpty())return
        val (id,text)=queue.removeFirst();busy=true;caption.value=text;duck(true);val generation=++token
        try {
            val fd=context.assets.openFd("voices/$id.wav")
            val media=MediaPlayer();player=media
            media.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            fd.use { media.setDataSource(it.fileDescriptor,it.startOffset,it.length) };media.setVolume(volume,volume)
            media.setOnCompletionListener { if(generation==token)done() }
            media.setOnErrorListener { _,_,_->if(generation==token)fallback(text,generation);true }
            media.setOnPreparedListener {if(generation==token)it.start()}
            media.prepareAsync()
            handler.postDelayed({if(generation==token && busy && player===media && !media.isPlaying)fallback(text,generation)},5000)
        } catch(_:Exception) { fallback(text,generation) }
    }
    private fun fallback(text:String,generation:Int) {
        player?.release();player=null
        if(ttsReady && tts?.speak(text,TextToSpeech.QUEUE_FLUSH,android.os.Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME,volume) },generation.toString())==TextToSpeech.SUCCESS) {
            handler.postDelayed({if(generation==token && busy)done()},15000)
        } else handler.postDelayed({if(generation==token)done()},3500)
    }
    private fun done() { player?.release();player=null;busy=false;caption.value="";duck(false);next() }
    fun release() { cancel();tts?.shutdown() }
}
