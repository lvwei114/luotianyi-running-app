package com.luotianyi.running.nativeapp.audio

import android.app.Application
import android.media.MediaPlayer
import android.os.Looper
import com.luotianyi.running.nativeapp.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowMediaPlayer
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28], application=Application::class)
class VoicePlayerTest {
    private fun media(voice: VoicePlayer) = VoicePlayer::class.java.getDeclaredField("player").let {
        it.isAccessible=true; it.get(voice) as MediaPlayer
    }
    @Test fun everyAssetHasOneCanonicalCaptionAndCategory() {
        val app=RuntimeEnvironment.getApplication()
        val files=app.assets.list("voices")!!.filter {it.endsWith(".wav")}.map {it.removeSuffix(".wav")}.toSet()
        assertEquals(59,files.size)
        assertEquals(files,VoiceCatalog.all.map {it.id}.toSet())
        assertEquals(VoiceCatalog.all.size,VoiceCatalog.all.distinctBy {it.id}.size)
        VoiceCatalog.all.forEach {line->
            assertTrue(line.text.isNotBlank())
            app.assets.open("voices/${line.id}.wav").use {stream->
                val header=ByteArray(12);assertEquals(12,stream.read(header))
                assertEquals("RIFF",String(header,0,4));assertEquals("WAVE",String(header,8,4))
            }
        }
        VoiceCategory.entries.forEach {assertTrue(VoiceCatalog.inCategory(it).isNotEmpty())}
    }
    @Test fun captionStartsWithPlaybackAndOldCallbacksCannotReplaceNewSpeech() {
        ShadowMediaPlayer.setMediaInfoProvider {ShadowMediaPlayer.MediaInfo(1000,100)}
        val voice=VoicePlayer(RuntimeEnvironment.getApplication()) {}
        try {
            voice.speak("jiayou","错误字幕")
            assertEquals("",voice.subtitle.value)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            assertEquals(VoiceCatalog.get("jiayou").text,voice.subtitle.value)
            val old=shadowOf(media(voice));val completion=old.onCompletionListener!!
            voice.speak(VoiceCatalog.get("zanting"),VoicePriority.CONTROL)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            assertEquals(VoiceCatalog.get("zanting").text,voice.subtitle.value)
            completion.onCompletion(null)
            assertEquals("zanting",voice.playingId.value)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1000))
            assertFalse(voice.isBusy)
            assertEquals(VoiceCatalog.get("zanting").text,voice.subtitle.value)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1200))
            assertEquals("",voice.subtitle.value)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
            assertEquals("",voice.subtitle.value)
        } finally {voice.release();ShadowMediaPlayer.resetStaticState()}
    }
    @Test fun cancelClearsCaptionQueueAndPendingTimers() {
        ShadowMediaPlayer.setMediaInfoProvider {ShadowMediaPlayer.MediaInfo(1000,0)}
        val ducked=mutableListOf<Boolean>()
        val voice=VoicePlayer(RuntimeEnvironment.getApplication()) {ducked.add(it)}
        try {
            voice.speak(VoiceCatalog.get("zhunbei"))
            voice.speak(VoiceCatalog.get("water_after"),allowQueue=true)
            shadowOf(Looper.getMainLooper()).idle()
            voice.cancel()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(35))
            assertFalse(voice.isBusy);assertEquals("",voice.subtitle.value)
            assertEquals("",voice.playingId.value);assertEquals(false,ducked.last())
        } finally {voice.release();ShadowMediaPlayer.resetStaticState()}
    }
}
