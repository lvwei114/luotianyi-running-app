package com.luotianyi.running.nativeapp.domain
import com.luotianyi.running.nativeapp.audio.BeatSynth
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
class BeatSynthTest {
    @Test fun pcmBeatSpacingAndSilence() {
        val synth=BeatSynth(8000);synth.bpm=120.0;val samples=ShortArray(16000);synth.render(samples)
        assertTrue(samples.sliceArray(0..700).any { abs(it.toInt())>1000 })
        assertTrue(samples.sliceArray(1000..3500).all { it==0.toShort() })
        assertTrue(samples.sliceArray(4000..4700).any { abs(it.toInt())>1000 })
        synth.volume=0.0;synth.render(samples);assertTrue(samples.all { it==0.toShort() })
    }
}
