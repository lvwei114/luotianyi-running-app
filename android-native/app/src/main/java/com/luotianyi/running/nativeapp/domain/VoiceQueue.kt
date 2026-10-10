package com.luotianyi.running.nativeapp.domain

/** Monotonic wall time prevents a paused workout from retaining stale speech. */
class VoiceQueue {
    enum class Result { DROPPED, QUEUED, INTERRUPT }
    private val pending = mutableListOf<VoiceRequest>()
    var active: VoiceRequest? = null
        private set
    val busy get() = active != null || pending.isNotEmpty()

    fun offer(request: VoiceRequest, now: Long): Result {
        pending.removeAll { it.expiresAt <= now }
        if (request.expiresAt <= now) return Result.DROPPED
        if (active?.line?.id == request.line.id || pending.any { it.line.id == request.line.id }) return Result.DROPPED
        if (request.priority == VoicePriority.COMPANION && !request.allowQueue && busy) return Result.DROPPED
        val interrupt = active?.let {
            request.priority >= VoicePriority.CONTROL && request.priority > it.priority
        } ?: false
        if (request.priority == VoicePriority.SAFETY) {
            pending.removeAll { it.priority < VoicePriority.SAFETY }
        } else if (request.priority == VoicePriority.STAGE) {
            pending.removeAll { it.priority == VoicePriority.STAGE || it.priority == VoicePriority.COMPANION }
        }
        if (pending.size >= 6) {
            val lowest = pending.minBy { it.priority.ordinal }
            if (lowest.priority >= request.priority) return Result.DROPPED
            pending.remove(lowest)
        }
        if (interrupt) active = null
        pending.add(request)
        return if (interrupt) Result.INTERRUPT else Result.QUEUED
    }

    fun next(now: Long): VoiceRequest? {
        if (active != null) return null
        pending.removeAll { it.expiresAt <= now }
        val request = pending.maxByOrNull { it.priority.ordinal } ?: return null
        pending.remove(request); active = request
        return request
    }
    fun complete() { active = null }
    fun clear() { active = null; pending.clear() }
}
