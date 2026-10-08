package com.luotianyi.running.nativeapp.domain
import com.luotianyi.running.nativeapp.data.AppSettings
import org.junit.Assert.*
import org.junit.Test
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
}
