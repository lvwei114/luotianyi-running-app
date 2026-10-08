package com.luotianyi.running.nativeapp.audio

import android.app.Application
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class MetronomeLifecycleTest {
    @Test fun blockedOldWriterCannotStopNewPlaybackAfterQuickResume() {
        val oldEntered=CountDownLatch(1);val releaseOld=CountDownLatch(1);val oldClosed=CountDownLatch(1)
        val newEntered=CountDownLatch(1);val releaseNew=CountDownLatch(1);val newContinued=CountDownLatch(1)
        val opens=AtomicInteger();val writes=AtomicInteger();val errors=mutableListOf<String>()
        val metronome=NativeMetronome(outputFactory={
            val old=opens.getAndIncrement()==0
            object:BeatOutput {
                override fun play() {}
                override fun write(pcm:ShortArray,offset:Int):Int {
                    if(old) {oldEntered.countDown();releaseOld.await(5,TimeUnit.SECONDS)}
                    else {val n=writes.incrementAndGet();newEntered.countDown();releaseNew.await(5,TimeUnit.SECONDS);if(n>=2)newContinued.countDown();Thread.sleep(2)}
                    return pcm.size-offset
                }
                override fun pause() {}
                override fun close() {if(old)oldClosed.countDown()}
            }
        },onError={errors.add(it)})
        try {
            metronome.start();assertTrue(oldEntered.await(3,TimeUnit.SECONDS))
            metronome.stop();metronome.start();assertTrue(newEntered.await(3,TimeUnit.SECONDS))
            releaseOld.countDown();assertTrue(oldClosed.await(3,TimeUnit.SECONDS));releaseNew.countDown()
            assertTrue("Resumed writer must continue after the previous generation exits",newContinued.await(3,TimeUnit.SECONDS))
            assertTrue(errors.isEmpty())
        } finally {releaseOld.countDown();releaseNew.countDown();metronome.stop()}
    }
}
