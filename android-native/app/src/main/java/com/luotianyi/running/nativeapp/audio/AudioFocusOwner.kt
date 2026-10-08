package com.luotianyi.running.nativeapp.audio
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.core.content.ContextCompat
import com.luotianyi.running.nativeapp.RunningApplication
import com.luotianyi.running.nativeapp.service.RunService

class AudioFocusOwner(private val app:RunningApplication) {
    private val manager=app.getSystemService(AudioManager::class.java)
    var workoutActive=false;var musicActive=false;var voiceActive=false;var previewActive=false;private var owned=false;private var requested=false
    private val request=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()).setOnAudioFocusChangeListener {change->
        if(change<0) {
            owned=false;previewActive=false;app.voice.cancel();app.music.duckPreview(false);app.music.player.pause();if(workoutActive)RunService.command(app,"PAUSE_SILENT")
        } else if(change==AudioManager.AUDIOFOCUS_GAIN)owned=true
    }.build()
    init {ContextCompat.registerReceiver(app,object:BroadcastReceiver() {override fun onReceive(context:Context?,intent:Intent?) {app.voice.cancel();app.music.player.pause();if(workoutActive)RunService.command(app,"PAUSE_SILENT")}},IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),ContextCompat.RECEIVER_NOT_EXPORTED)}
    fun acquire():Boolean {if(owned)return true;owned=manager.requestAudioFocus(request)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;requested=owned;return owned}
    fun releaseIfIdle() {if(requested && !workoutActive && !musicActive && !voiceActive && !previewActive) {manager.abandonAudioFocusRequest(request);owned=false;requested=false}}
}
