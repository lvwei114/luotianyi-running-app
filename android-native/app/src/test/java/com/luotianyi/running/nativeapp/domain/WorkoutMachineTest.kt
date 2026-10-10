package com.luotianyi.running.nativeapp.domain
import com.luotianyi.running.nativeapp.data.AppSettings
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
class WorkoutMachineTest {
    @Test fun timedDeadlineStaysLockedAndLegacyGuideDurationSurvivesModeSwitch() {
        val timed=WorkoutMachine();timed.prepare(RunMode.TIMED,AppSettings(timedMinutes=5),500000);timed.start(1000)
        timed.updateSettings(AppSettings(timedMinutes=60,ratios=listOf(1.0,1.0,1.0,97.0)))
        timed.updatePlaylistDuration(10000)
        timed.changeMode(RunMode.CUSTOM);timed.tick(301001,null)
        assertEquals(RunMode.TIMED,timed.state.mode);assertEquals(300000L,timed.state.elapsedMs)
        val guide=WorkoutMachine();guide.prepare(RunMode.GUIDE,AppSettings(),480000);guide.start(1000)
        guide.changeMode(RunMode.FOLLOW);guide.tick(3000,null);guide.changeMode(RunMode.GUIDE)
        assertEquals(480000L,guide.state.totalMs);assertEquals(2000L,guide.state.elapsedMs)
        guide.updatePlaylistDuration(600000);assertEquals(600000L,guide.state.totalMs)
    }
    @Test fun timedExcludesWarmupAndPauseAndClampsDeadline() {
        val m=WorkoutMachine();m.prepare(RunMode.TIMED,AppSettings(timedMinutes=5),9_999_999)
        assertEquals(0L,m.tick(500_000,null).elapsedMs);m.start(500_000);m.pause(510_000);m.tick(800_000,null);assertEquals(10_000L,m.state.elapsedMs)
        m.resume(900_000);m.tick(1_195_000,null);assertEquals(RunPhase.FINISHED,m.state.phase);assertEquals(300_000L,m.state.elapsedMs)
        m.finish(2_000_000);assertEquals(300_000L,m.state.elapsedMs)
    }
    @Test fun legacyRulesAndTimedIsolation() {
        for(mode in listOf(RunMode.GUIDE,RunMode.FOLLOW,RunMode.CUSTOM)) {
            val m=WorkoutMachine();m.prepare(mode,AppSettings(timedMinutes=5,customBpm=175),400_000);m.start(1000);m.tick(302_000,null);assertEquals(RunPhase.RUNNING,m.state.phase)
            m.changeMode(RunMode.TIMED);assertEquals(mode,m.state.mode);m.tick(4_000_000,null)
            assertEquals(if(mode==RunMode.GUIDE)RunPhase.FINISHED else RunPhase.RUNNING,m.state.phase);if(mode==RunMode.CUSTOM)assertEquals(175,m.state.bpm)
        }
    }
    @Test fun followUsesFirstHeartRateThenHysteresisAndGlide() {
        val m=WorkoutMachine();m.prepare(RunMode.FOLLOW,AppSettings(),0);m.start(1000);m.tick(1250,170);assertEquals(3,m.state.stageIndex);assertTrue(m.state.bpm<=151)
        m.tick(1500,100);assertEquals(3,m.state.stageIndex);m.tick(122_000,100);assertEquals(0,m.state.stageIndex)
    }
    @Test fun scheduledModesStartAt150AndRemainContinuousForAllTimedDurations() {
        for(minutes in listOf(5,30,60))for(mode in listOf(RunMode.GUIDE,RunMode.TIMED)) {
            val total=minutes*60_000L
            val m=WorkoutMachine();m.prepare(mode,AppSettings(timedMinutes=minutes),total)
            assertEquals(150,m.state.bpm);m.start(1000)
            var previous=m.state;var boundaries=0;var peak=150
            for(elapsed in 250L..total step 250L) {
                val current=m.tick(1000+elapsed,null)
                assertTrue("$mode/$minutes jump at $elapsed",abs(current.bpm-previous.bpm)<=1)
                assertTrue(current.bpm in 150..175)
                if(current.stageIndex==0)assertTrue(current.targetBpm in 150..160)
                if(current.stageIndex!=previous.stageIndex) {
                    boundaries++
                    assertTrue("$mode/$minutes discontinuous target",abs(current.targetBpm-previous.targetBpm)<=1)
                }
                peak=maxOf(peak,current.bpm);previous=current
            }
            assertEquals(3,boundaries);assertEquals(175,peak)
            assertEquals(RunPhase.FINISHED,m.state.phase);assertEquals(total,m.state.elapsedMs)
            assertEquals(150,m.state.bpm)
        }
    }
    @Test fun scheduledCurveConnectsWarmupMainRunPeakAndCooldown() {
        val m=WorkoutMachine();m.prepare(RunMode.GUIDE,AppSettings(ratios=listOf(1.0,1.0,1.0,1.0)),240_000);m.start(1000)
        val checkpoints=mapOf(30_000L to 155,60_000L to 160,90_000L to 165,120_000L to 170,
            150_000L to 173,180_000L to 175,210_000L to 163,239_750L to 150)
        for(elapsed in 250L..239_750L step 250L) {
            val current=m.tick(1000+elapsed,null)
            checkpoints[elapsed]?.let {expected->assertEquals("BPM at $elapsed",expected,current.bpm)}
        }
    }
    @Test fun switchingBetweenGuideAndFollowGlidesFromCurrentBpm() {
        val m=WorkoutMachine();m.prepare(RunMode.GUIDE,AppSettings(ratios=listOf(1.0,1.0,1.0,1.0)),240_000);m.start(1000)
        for(elapsed in 250L..180_000L step 250L)m.tick(1000+elapsed,null)
        assertEquals(175,m.state.bpm)
        m.changeMode(RunMode.FOLLOW)
        assertEquals(175,m.state.bpm)
        var current=m.tick(181_250,170)
        assertEquals(176,current.bpm);assertEquals(181,current.targetBpm)
        for(elapsed in 180_500L..188_000L step 250L)m.tick(1000+elapsed,170)
        assertEquals(181,m.state.bpm)
        m.changeMode(RunMode.GUIDE);current=m.tick(189_250,null)
        assertEquals(180,current.bpm);assertTrue(current.targetBpm<current.bpm)
    }
    @Test fun customModeSwitchesGlideButManualFixedBpmChangesStillApplyDirectly() {
        val m=WorkoutMachine();m.prepare(RunMode.CUSTOM,AppSettings(customBpm=220),0);m.start(1000)
        assertEquals(220,m.state.bpm)
        m.changeMode(RunMode.FOLLOW);m.tick(1250,170)
        assertEquals(219,m.state.bpm);assertEquals(181,m.state.targetBpm)
        m.updateSettings(AppSettings(customBpm=170));m.changeMode(RunMode.CUSTOM);m.tick(1500,null)
        assertEquals(218,m.state.bpm);assertEquals(170,m.state.targetBpm)
        for(elapsed in 750L..21_000L step 250L)m.tick(1000+elapsed,null)
        assertEquals(170,m.state.bpm)
        m.updateSettings(AppSettings(customBpm=180));m.tick(22_250,null)
        assertEquals(180,m.state.bpm)
    }
    @Test fun pausedModeChangeDoesNotCountPauseAsTempoGlideTime() {
        val m=WorkoutMachine();m.prepare(RunMode.CUSTOM,AppSettings(customBpm=200),0);m.start(1000)
        m.pause(2000);m.changeMode(RunMode.FOLLOW);m.resume(500_000)
        val current=m.tick(500_250,170)
        assertEquals(1250L,current.elapsedMs);assertEquals(199,current.bpm)
    }
    @Test fun compressedGuideStagesAndLiveRatioChangesCannotJumpTheBeat() {
        val m=WorkoutMachine();m.prepare(RunMode.GUIDE,AppSettings(ratios=listOf(1.0,1.0,1.0,97.0)),300_000);m.start(1000)
        var previous=m.state.bpm
        for(elapsed in 250L..20_000L step 250L) {
            val current=m.tick(1000+elapsed,null)
            assertTrue(abs(current.bpm-previous)<=1);previous=current.bpm
        }
        m.updateSettings(AppSettings(ratios=listOf(97.0,1.0,1.0,1.0)))
        val current=m.tick(21_250,null)
        assertTrue(abs(current.bpm-previous)<=1);assertTrue(current.targetBpm<current.bpm)
    }
    @Test fun sensorTimeUpdatesDoNotConsumeTempoGlideTime() {
        val m=WorkoutMachine();m.prepare(RunMode.CUSTOM,AppSettings(customBpm=220),0);m.start(1000)
        m.changeMode(RunMode.FOLLOW);m.tick(2000,170,updateStage=false)
        assertEquals(220,m.state.bpm)
        val current=m.tick(2250,170)
        assertEquals(217,current.bpm)
    }
}
