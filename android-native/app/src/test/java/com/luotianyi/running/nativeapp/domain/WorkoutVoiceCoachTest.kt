package com.luotianyi.running.nativeapp.domain

import com.luotianyi.running.nativeapp.data.AppSettings
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class WorkoutVoiceCoachTest {
    private val running = TrainingState(RunPhase.RUNNING, RunMode.CUSTOM, stageName = "自定义")
    private val quiet = AppSettings(chatFrequency = "off", hydrationMinutes = 0)

    @Test fun existingFrequencyRangesAndOffArePreserved() {
        for ((frequency, range) in listOf("dense" to 18..30, "standard" to 35..55, "sparse" to 90..140)) {
            repeat(10) {seed->
                val coach = WorkoutVoiceCoach(Random(seed)); val settings = quiet.copy(chatFrequency = frequency)
                coach.reset(running, settings); coach.next(running, settings, null, false)
                val second = (1..150).first {coach.next(running.copy(elapsedMs = it * 1000L), settings, null, false) != null}
                assertTrue("$frequency emitted at $second", second in range)
            }
        }
        val coach = WorkoutVoiceCoach(Random(1)); coach.reset(running, quiet); coach.next(running, quiet, null, false)
        for (second in 1..600) assertNull(coach.next(running.copy(elapsedMs = second * 1000L), quiet, null, false))
    }
    @Test fun busySpeechDelaysRandomSpeechAndPauseDoesNotProduceReminders() {
        val settings = quiet.copy(chatFrequency = "dense")
        val coach = WorkoutVoiceCoach(Random(2)); coach.reset(running, settings); coach.next(running, settings, null, false)
        assertNull(coach.next(running.copy(elapsedMs = 30_000), settings, null, true))
        assertNull(coach.next(running.copy(elapsedMs = 41_000), settings, null, false))
        assertNotNull(coach.next(running.copy(elapsedMs = 42_000), settings, null, false))
        assertNull(coach.next(running.copy(phase = RunPhase.PAUSED, elapsedMs = 3_600_000), settings, null, false))
    }
    @Test fun earlySpeechIsOpeningChatAndEndSpeechEncouragesWithoutCasualChat() {
        val settings = quiet.copy(chatFrequency = "dense")
        val coach = WorkoutVoiceCoach(Random(3))
        val timed = running.copy(mode = RunMode.TIMED, totalMs = 600_000, stageName = "热身")
        coach.reset(timed, settings); coach.next(timed, settings, null, false)
        assertEquals(VoiceCategory.CHAT_START, coach.next(timed.copy(elapsedMs = 60_000), settings, null, false)!!.line.category)
        val end = coach.next(timed.copy(elapsedMs = 500_000), settings, null, false)!!
        assertTrue(end.line.id in listOf("jianchi", "zaiyici", "chongya", "huxi", "songjian"))
        assertEquals(VoicePriority.COMPANION, end.priority)
    }
    @Test fun longRunTimeNoticesAreOnceAndDoNotInventElapsedTime() {
        val coach = WorkoutVoiceCoach(Random(4)); coach.reset(running, quiet); coach.next(running, quiet, null, false)
        fun at(minutes: Int) = coach.next(running.copy(elapsedMs = minutes * 60_000L), quiet, null, false)
        assertEquals(VoiceCategory.TIME_60, at(60)!!.line.category)
        assertNull(at(61))
        assertEquals(VoiceCategory.TIME_90, at(90)!!.line.category)
        assertEquals(VoiceCategory.TIME_LONG, at(120)!!.line.category)
        assertEquals("time_long_a", at(150)!!.line.id)
        val late = WorkoutVoiceCoach(Random(1)); late.reset(running, quiet); late.next(running, quiet, null, false)
        assertNull(late.next(running.copy(elapsedMs = 65 * 60_000L), quiet, null, false))
    }
    @Test fun defaultFinalEffortUsesPersistenceVoicesAndCooldownNeverUrgesSprint() {
        val selected=mutableSetOf<String>()
        val settings=quiet.copy(chatFrequency="dense")
        repeat(100) {seed->
            val coach=WorkoutVoiceCoach(Random(seed))
            val effort=running.copy(mode=RunMode.TIMED,totalMs=600_000,stageIndex=2,stageName="冲刺",elapsedMs=400_000,stageRemainingMs=120_000)
            coach.reset(effort,settings);coach.next(effort,settings,null,false)
            selected.add(coach.next(effort.copy(elapsedMs=450_000,stageRemainingMs=70_000),settings,null,false)!!.line.id)
            val cooldown=effort.copy(stageIndex=3,stageName="放松",elapsedMs=500_000)
            coach.next(cooldown,settings,null,false)
            val calm=coach.next(cooldown.copy(elapsedMs=550_000),settings,null,false)!!
            assertTrue(calm.line.id in listOf("huxi","songjian","henbang","chat_end_a"))
        }
        assertTrue(selected.containsAll(listOf("jianchi","zaiyici","chongya")))
    }
    @Test fun hydrationRespectsExistingIntervalAndHighTemperature() {
        val settings = quiet.copy(hydrationMinutes = 20, highTemp = true)
        val coach = WorkoutVoiceCoach(Random(5)); coach.reset(running, settings); coach.next(running, settings, null, false)
        assertNull(coach.next(running.copy(elapsedMs = 899_000), settings, null, false))
        assertEquals(VoiceCategory.WATER_DURING, coach.next(running.copy(elapsedMs = 900_000), settings, null, false)!!.line.category)
        val changed = settings.copy(hydrationMinutes = 15, highTemp = false)
        assertNull(coach.next(running.copy(elapsedMs = 901_000), changed, null, false))
        assertEquals(VoiceCategory.WATER_DURING, coach.next(running.copy(elapsedMs = 1_801_000), changed, null, false)!!.line.category)
    }
    @Test fun heartVoicesMatchWarningActualPauseAndVerifiedRecovery() {
        val settings = quiet
        val coach = WorkoutVoiceCoach(Random(6)); coach.reset(running, settings)
        assertEquals(VoiceCategory.HR_LOW, coach.next(running, settings, 95.0, false, 10_000)!!.line.category)
        assertNull(coach.next(running.copy(elapsedMs = 1000), settings, 95.0, false, 11_000))
        assertEquals(VoiceCategory.HR_MEDIUM, coach.safetyStop(105.0, 20_000).line.category)
        assertEquals(VoiceCategory.HR_HIGH, coach.safetyStop(115.0, 30_000).line.category)
        assertNull(coach.recovery(40_000, settings, 80.0, false))
        assertNull(coach.recovery(54_000, settings, 80.0, false))
        assertNull(coach.recovery(55_000, settings, null, false))
        assertNull(coach.recovery(56_000, settings, 80.0, false))
        assertEquals(VoiceCategory.HR_RECOVERED, coach.recovery(71_000, settings, 80.0, false)!!.line.category)
        assertNull(coach.recovery(90_000, settings, 80.0, false))
    }
    @Test fun noHeartDataAndDisabledSafetyCannotGenerateHeartWarnings() {
        val coach = WorkoutVoiceCoach(Random(7)); coach.reset(running, quiet)
        coach.next(running, quiet, null, false)
        assertNull(coach.next(running.copy(elapsedMs = 1000), quiet, null, false))
        assertNull(coach.next(running.copy(elapsedMs = 2000), quiet.copy(safetyEnabled = false), 120.0, false))
        assertNull(coach.next(running.copy(elapsedMs = 3000), quiet.copy(withWearable = false), 120.0, false))
    }
    @Test fun frequencyChangesTakeEffectWithoutOverdueBurstAndEndingHonorsSwitches() {
        val coach = WorkoutVoiceCoach(Random(8)); coach.reset(running, quiet); coach.next(running, quiet, null, false)
        val dense = quiet.copy(chatFrequency = "dense")
        assertNull(coach.next(running.copy(elapsedMs = 300_000), dense, null, false))
        assertNull(coach.next(running.copy(elapsedMs = 317_000), dense, null, false))
        assertNotNull(coach.next(running.copy(elapsedMs = 330_000), dense, null, false))
        assertEquals(listOf(VoiceCategory.FINISH), coach.finish(quiet).map {it.line.category})
        assertEquals(listOf(VoiceCategory.FINISH, VoiceCategory.WATER_AFTER, VoiceCategory.CHAT_END), coach.finish(AppSettings()).map {it.line.category})
        assertTrue(coach.endHint().line.category in listOf(VoiceCategory.SKIP_COOLDOWN,VoiceCategory.CHAT_END))
    }
}
