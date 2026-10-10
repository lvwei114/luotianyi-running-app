package com.luotianyi.running.nativeapp.domain

import org.junit.Assert.*
import org.junit.Test

class VoiceQueueTest {
    private fun request(id: String, priority: VoicePriority, expiry: Long = 10_000, allowQueue: Boolean = false) =
        VoiceRequest(VoiceLine(id, id, VoiceCategory.CHEER), priority, expiry, allowQueue)

    @Test fun safetyInterruptsCompanionAndDiscardsQueuedStage() {
        val queue = VoiceQueue()
        queue.offer(request("chat", VoicePriority.COMPANION), 0)
        assertEquals("chat", queue.next(0)!!.line.id)
        queue.offer(request("stage", VoicePriority.STAGE), 0)
        assertEquals(VoiceQueue.Result.INTERRUPT, queue.offer(request("danger", VoicePriority.SAFETY), 1))
        assertEquals("danger", queue.next(1)!!.line.id)
        queue.complete(); assertNull(queue.next(2))
    }
    @Test fun companionsDoNotAccumulateAndExpiredReminderNeverPlays() {
        val queue = VoiceQueue()
        queue.offer(request("start", VoicePriority.CONTROL), 0); queue.next(0)
        assertEquals(VoiceQueue.Result.DROPPED, queue.offer(request("chat", VoicePriority.COMPANION), 0))
        queue.offer(request("water", VoicePriority.REMINDER, 5), 0)
        queue.complete(); assertNull(queue.next(6)); assertFalse(queue.busy)
    }
    @Test fun stageReplacementKeepsOpeningAndRemovesOldStage() {
        val queue = VoiceQueue()
        queue.offer(request("start", VoicePriority.CONTROL), 0); queue.next(0)
        queue.offer(request("warmup", VoicePriority.STAGE), 0)
        queue.offer(request("burn", VoicePriority.STAGE), 1)
        assertEquals("start", queue.active!!.line.id)
        queue.complete(); assertEquals("burn", queue.next(2)!!.line.id)
        queue.complete(); assertNull(queue.next(3))
    }
    @Test fun endingSequenceKeepsFifoAndRejectsDuplicates() {
        val queue = VoiceQueue()
        queue.offer(request("end", VoicePriority.CONTROL), 0); queue.next(0)
        assertEquals(VoiceQueue.Result.DROPPED, queue.offer(request("end", VoicePriority.CONTROL), 1))
        queue.offer(request("water", VoicePriority.REMINDER), 1)
        queue.offer(request("chat_end", VoicePriority.COMPANION, allowQueue = true), 1)
        queue.complete(); assertEquals("water", queue.next(2)!!.line.id)
        queue.complete(); assertEquals("chat_end", queue.next(3)!!.line.id)
        queue.clear(); assertFalse(queue.busy)
    }
}
