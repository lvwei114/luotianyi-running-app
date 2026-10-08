package com.luotianyi.running.nativeapp.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import kotlin.concurrent.thread

internal interface BeatOutput {
    fun play()
    fun write(pcm:ShortArray,offset:Int):Int
    fun pause()
    fun close()
}
private class AndroidBeatOutput:BeatOutput {
    private val audio=AudioTrack.Builder()
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        .setAudioFormat(AudioFormat.Builder().setSampleRate(44100).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
        .setBufferSizeInBytes(AudioTrack.getMinBufferSize(44100,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(2048))
        .setTransferMode(AudioTrack.MODE_STREAM).build()
    override fun play()=audio.play()
    override fun write(pcm:ShortArray,offset:Int)=audio.write(pcm,offset,pcm.size-offset,AudioTrack.WRITE_BLOCKING)
    override fun pause() {audio.pause();audio.flush()}
    override fun close() {try {audio.stop()} finally {audio.release()}}
}
class NativeMetronome internal constructor(private val outputFactory:()->BeatOutput,private val onError:(String)->Unit) {
    constructor(onError:(String)->Unit):this(::AndroidBeatOutput,onError)
    @Volatile var bpm=170
    @Volatile var volume=.4f
    @Volatile var tone="wood"
    @Volatile private var playing=false
    @Volatile private var generation=0L
    private var worker:Thread?=null
    private var output:BeatOutput?=null

    @Synchronized fun start() {
        if(playing)return
        val mine=++generation;playing=true
        worker=thread(name="luotianyi-beat") {
            var audio:BeatOutput?=null
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
                audio=outputFactory()
                synchronized(this@NativeMetronome) {if(generation==mine)output=audio}
                if(generation!=mine)return@thread
                audio.play();val synth=BeatSynth();val pcm=ShortArray(256)
                while(playing && generation==mine) {
                    synth.bpm=bpm.toDouble();synth.volume=volume.toDouble();synth.wood=tone=="wood";synth.render(pcm)
                    var offset=0
                    while(playing && generation==mine && offset<pcm.size) {
                        val written=audio.write(pcm,offset)
                        if(written<0)error("音频写入失败 $written")
                        if(written==0)Thread.yield() else offset+=written
                    }
                }
            } catch(e:Exception) {if(playing && generation==mine)onError("节拍器不可用：${e.message}")}
            finally {
                synchronized(this@NativeMetronome) {if(generation==mine) {playing=false;output=null}}
                runCatching {audio?.close()}
            }
        }
    }
    fun stop() {
        val old=synchronized(this) {
            generation++;playing=false
            val previous=worker;worker=null
            runCatching {output?.pause()};output=null
            previous
        }
        old?.join(300)
    }
}
