package com.luotianyi.running.nativeapp.domain

import com.luotianyi.running.nativeapp.data.AppSettings
import kotlin.random.Random

data class CoachEmission(val line: VoiceLine, val priority: VoicePriority = VoiceCatalog.priority(line.category),
    val lifetimeMs: Long = 30_000, val allowQueue: Boolean = false)

/** Selects speech using effective workout time. No GPS or workout transitions are changed here. */
class WorkoutVoiceCoach(private val random: Random = Random.Default) {
    private var nextCompanion = 0L
    private var nextWater = 0L
    private var quietUntil = 0L
    private var lastMode: RunMode? = null
    private var lastStage = -1
    private var frequency = "standard"
    private var hydration = 20
    private var highTemp = false
    private var lastHeartNotice = -60_000L
    private var warned = false
    private var recoverySince: Long? = null
    private val timeNotices = mutableSetOf<Int>()
    private var nextLongNotice = 120 * 60_000L
    private var startChatPlayed = false
    private val recent = ArrayDeque<String>()
    private val usage = mutableMapOf<String, Int>()

    fun reset(state: TrainingState, settings: AppSettings) {
        recent.clear(); usage.clear(); timeNotices.clear()
        lastMode = null; lastStage = -1; startChatPlayed = false
        warned = false; recoverySince = null
        lastHeartNotice = -60_000L; nextLongNotice = 120 * 60_000L
        frequency = settings.chatFrequency; hydration = settings.hydrationMinutes; highTemp = settings.highTemp
        nextWater = state.elapsedMs + waterInterval(settings)
        quietUntil = state.elapsedMs + 12_000
        scheduleCompanion(state.elapsedMs)
    }

    fun opening() = CoachEmission(pick(VoiceCategory.OPENING), VoicePriority.CONTROL)
    fun beforeWater() = CoachEmission(pick(VoiceCategory.WATER_BEFORE), VoicePriority.REMINDER)
    fun paused() = CoachEmission(VoiceCatalog.get("zanting"), VoicePriority.CONTROL)
    fun resumed(elapsed: Long): CoachEmission {
        quietUntil = elapsed + 12_000; scheduleCompanion(elapsed)
        recoverySince = null
        return CoachEmission(VoiceCatalog.get("kaishi"), VoicePriority.CONTROL)
    }

    fun safetyStop(percent: Double, now: Long): CoachEmission {
        warned = true; recoverySince = null; lastHeartNotice = now
        return CoachEmission(pick(if (percent >= 110) VoiceCategory.HR_HIGH else VoiceCategory.HR_MEDIUM), VoicePriority.SAFETY)
    }

    fun recovery(now: Long, settings: AppSettings, percent: Double?, voiceBusy: Boolean): CoachEmission? {
        if (!settings.safetyEnabled || !settings.withWearable || percent == null) {
            recoverySince = null
            return null
        }
        if (!warned || percent >= 85) { recoverySince = null; return null }
        if (recoverySince == null) recoverySince = now
        if (now - recoverySince!! < 15_000 || voiceBusy) return null
        warned = false; recoverySince = null
        return CoachEmission(pick(VoiceCategory.HR_RECOVERED), VoicePriority.REMINDER)
    }

    fun endHint() = CoachEmission(pickLines(VoiceCatalog.inCategory(VoiceCategory.SKIP_COOLDOWN) + VoiceCatalog.get("chat_end_a")), VoicePriority.CONTROL)

    fun finish(settings: AppSettings): List<CoachEmission> = buildList {
        add(CoachEmission(VoiceCatalog.get("jieshu"), VoicePriority.CONTROL))
        if (settings.hydrationMinutes > 0) add(CoachEmission(pick(VoiceCategory.WATER_AFTER), VoicePriority.REMINDER, 90_000, true))
        if (settings.chatFrequency != "off") add(CoachEmission(pickIds(listOf("chat_end_b", "chat_end_c")), VoicePriority.COMPANION, 90_000, true))
    }

    fun next(state: TrainingState, settings: AppSettings, hrPercent: Double?, voiceBusy: Boolean, now: Long = state.elapsedMs): CoachEmission? {
        if (state.phase != RunPhase.RUNNING) return null
        val elapsed = state.elapsedMs
        if (settings.chatFrequency != frequency) {
            frequency = settings.chatFrequency; scheduleCompanion(elapsed)
        }
        if (hydration != settings.hydrationMinutes || highTemp != settings.highTemp) {
            hydration = settings.hydrationMinutes; highTemp = settings.highTemp
            nextWater = elapsed + waterInterval(settings)
        }
        if (voiceBusy) quietUntil = elapsed + 12_000
        recovery(now, settings, hrPercent, voiceBusy)?.let { return important(it, elapsed) }
        heartNotice(now, if (settings.safetyEnabled && settings.withWearable) hrPercent else null)?.let {
            return important(it, elapsed)
        }
        if (lastMode != state.mode || lastStage != state.stageIndex) {
            lastMode = state.mode; lastStage = state.stageIndex
            // Do not urge acceleration during a heart-rate warning.
            if (hrPercent == null || hrPercent < 90 || !settings.safetyEnabled) {
                val line = when (state.mode) {
                    RunMode.CUSTOM -> VoiceCatalog.get("maikaibu")
                    RunMode.FOLLOW -> when (state.stageIndex) {
                        0 -> pick(VoiceCategory.WARMUP)
                        1 -> pick(VoiceCategory.BURN)
                        else -> VoiceCatalog.get("maikaibu")
                    }
                    else -> pick(listOf(VoiceCategory.WARMUP, VoiceCategory.BURN, VoiceCategory.SPRINT, VoiceCategory.COOLDOWN)[state.stageIndex])
                }
                return important(CoachEmission(line, VoicePriority.STAGE), elapsed)
            }
        }
        if (voiceBusy || elapsed < quietUntil) return null
        if (settings.hydrationMinutes > 0 && elapsed >= nextWater) {
            nextWater = elapsed + waterInterval(settings)
            return important(CoachEmission(pick(VoiceCategory.WATER_DURING)), elapsed)
        }
        for (minutes in listOf(60, 90)) {
            val threshold = minutes * 60_000L
            if (elapsed >= threshold && timeNotices.add(minutes)) {
                if (elapsed - threshold <= 120_000) {
                    return important(CoachEmission(pick(if (minutes == 60) VoiceCategory.TIME_60 else VoiceCategory.TIME_90)), elapsed)
                }
            }
        }
        if (elapsed >= nextLongNotice) {
            val due = nextLongNotice
            nextLongNotice = (elapsed / (30 * 60_000L) + 1) * 30 * 60_000L
            if (elapsed - due <= 120_000) return important(CoachEmission(
                if (due == 120 * 60_000L) pick(VoiceCategory.TIME_LONG) else VoiceCatalog.get("time_long_a")), elapsed)
        }
        if (frequency == "off" || elapsed < nextCompanion || (settings.safetyEnabled && hrPercent != null && hrPercent >= 90)) return null
        scheduleCompanion(elapsed)
        val progress = if (state.totalMs > 0) elapsed.toDouble() / state.totalMs else null
        val lastEffortEnding = state.mode in listOf(RunMode.GUIDE, RunMode.TIMED) && state.stageIndex == 2 &&
            state.stageRemainingMs > 0 && state.stageRemainingMs <= minOf(90_000, (state.totalMs * .15).toLong())
        val nearEnd = (progress != null && progress >= .8 && state.totalMs - elapsed <= 300_000) || lastEffortEnding
        val intense = (state.mode in listOf(RunMode.GUIDE, RunMode.TIMED) && state.stageIndex == 2) ||
            (state.mode == RunMode.FOLLOW && state.stageIndex == 3)
        val early = if (progress != null) progress < .25 else elapsed < 180_000
        val line = when {
            intense -> pickIds(if (nearEnd) listOf("jiayou", "henbang", "chongya", "zaiyici", "jianchi") else listOf("jiayou", "henbang", "maikaibu"))
            state.stageName == "放松" -> pickIds(listOf("huxi", "songjian", "henbang", "chat_end_a"))
            nearEnd -> pickIds(listOf("jianchi", "zaiyici", "chongya", "huxi", "songjian"))
            early && !startChatPlayed -> { startChatPlayed = true; pick(VoiceCategory.CHAT_START) }
            early -> pickIds(listOf("jiayou", "huxi", "henbang", "songjian", "maikaibu"))
            else -> when (random.nextInt(4)) {
                0 -> pick(VoiceCategory.CHAT_CASUAL)
                1, 2 -> pick(VoiceCategory.CHAT_MID)
                else -> pickIds(listOf("jiayou", "huxi", "henbang", "songjian", "maikaibu"))
            }
        }
        return CoachEmission(line, VoicePriority.COMPANION, 12_000)
    }

    private fun heartNotice(now: Long, percent: Double?): CoachEmission? {
        if (percent != null && percent >= 90 && now - lastHeartNotice >= 60_000) {
            warned = true; lastHeartNotice = now; recoverySince = null
            return CoachEmission(pick(VoiceCategory.HR_LOW), VoicePriority.SAFETY)
        }
        return null
    }

    private fun important(emission: CoachEmission, elapsed: Long): CoachEmission {
        quietUntil = elapsed + 12_000; scheduleCompanion(elapsed)
        return emission
    }
    private fun waterInterval(settings: AppSettings) =
        (settings.hydrationMinutes * 60_000L * if (settings.highTemp) .75 else 1.0).toLong()
    private fun scheduleCompanion(elapsed: Long) {
        val seconds = when (frequency) { "sparse" -> random.nextInt(90, 141); "dense" -> random.nextInt(18, 31); else -> random.nextInt(35, 56) }
        nextCompanion = elapsed + seconds * 1000L
    }
    private fun pick(category: VoiceCategory) = pickLines(VoiceCatalog.inCategory(category))
    private fun pickIds(ids: List<String>) = pickLines(ids.map(VoiceCatalog::get))
    private fun pickLines(lines: List<VoiceLine>): VoiceLine {
        val candidates = lines.filter { it.id !in recent }.ifEmpty { lines.filter { it.id != recent.lastOrNull() }.ifEmpty { lines } }
        val least = candidates.minOf { usage[it.id] ?: 0 }
        val chosen = candidates.filter { (usage[it.id] ?: 0) == least }.random(random)
        usage[chosen.id] = (usage[chosen.id] ?: 0) + 1
        recent.addLast(chosen.id); if (recent.size > 6) recent.removeFirst()
        return chosen
    }
}
