package com.luotianyi.running.nativeapp.domain
import com.luotianyi.running.nativeapp.data.AppSettings
import kotlin.math.roundToInt
enum class RunPhase { IDLE, PREPARING, RUNNING, PAUSED, FINISHED }
data class TrainingState(val phase:RunPhase=RunPhase.IDLE,val mode:RunMode=RunMode.GUIDE,val elapsedMs:Long=0,val totalMs:Long=0,val stageIndex:Int=0,val stageName:String="准备",val bpm:Int=100,val targetBpm:Int=100,val stageRemainingMs:Long=0)
class WorkoutMachine {
    var state=TrainingState(); private set
    private var settings=AppSettings(); private var accumulated=0L; private var segment=0L; private var previous=0L; private var stageStart=0L; private var pickedHr=false; private var bpmFloat=150.0;private var guideTotal=690_000L
    private var modeTransition=false
    private val scheduledBpms=listOf(150,160,170,175,150)
    fun prepare(mode:RunMode,settings:AppSettings,playlistMs:Long) {
        check(state.phase !in listOf(RunPhase.RUNNING,RunPhase.PAUSED,RunPhase.PREPARING))
        this.settings=settings; accumulated=0; stageStart=0; previous=0; pickedHr=false; modeTransition=false
        val initialBpm=if(mode==RunMode.CUSTOM)settings.customBpm else scheduledBpms.first()
        bpmFloat=initialBpm.toDouble()
        guideTotal=playlistMs.takeIf {it>0} ?: 690_000
        val total=when(mode) { RunMode.TIMED -> settings.timedMinutes*60_000L; RunMode.GUIDE -> guideTotal; else -> 0 }
        state=TrainingState(RunPhase.PREPARING,mode,totalMs=total,bpm=initialBpm,targetBpm=initialBpm)
    }
    fun start(now:Long) { if(state.phase!=RunPhase.PREPARING)return; segment=now; state=state.copy(phase=RunPhase.RUNNING); tick(now,null) }
    fun pause(now:Long) { if(state.phase!=RunPhase.RUNNING)return; tick(now,null,false); if(state.phase==RunPhase.FINISHED)return; accumulated=state.elapsedMs; state=state.copy(phase=RunPhase.PAUSED) }
    fun resume(now:Long) { if(state.phase!=RunPhase.PAUSED)return; segment=now; state=state.copy(phase=RunPhase.RUNNING) }
    fun finish(now:Long) { if(state.phase in listOf(RunPhase.FINISHED,RunPhase.IDLE))return; if(state.phase==RunPhase.RUNNING)tick(now,null,false); state=state.copy(phase=RunPhase.FINISHED) }
    fun updateSettings(s:AppSettings) { settings=s }
    fun updatePlaylistDuration(playlistMs:Long) {
        guideTotal=playlistMs.takeIf {it>0} ?: 690_000L
        if(state.mode==RunMode.GUIDE)state=state.copy(totalMs=guideTotal)
    }
    fun changeMode(mode:RunMode) {
        if(state.mode==mode || state.mode==RunMode.TIMED || mode==RunMode.TIMED || state.phase !in listOf(RunPhase.RUNNING,RunPhase.PAUSED))return
        bpmFloat=state.bpm.toDouble(); previous=state.elapsedMs; modeTransition=true
        state=state.copy(mode=mode,totalMs=if(mode==RunMode.GUIDE)guideTotal else 0); stageStart=state.elapsedMs; pickedHr=false
    }
    fun tick(now:Long,hr:Int?,updateStage:Boolean=true):TrainingState {
        if(state.phase!=RunPhase.RUNNING)return state
        val elapsed=accumulated+(now-segment).coerceAtLeast(0)
        val total=if(state.mode in listOf(RunMode.TIMED,RunMode.GUIDE))state.totalMs else 0L
        state=state.copy(elapsedMs=if(total>0)elapsed.coerceAtMost(total) else elapsed)
        if(total>0 && elapsed>=total) { state=state.copy(phase=RunPhase.FINISHED); return state }
        if(!updateStage)return state
        when(state.mode) {
            RunMode.CUSTOM -> {
                val bpm=if(modeTransition)glideTo(settings.customBpm,elapsed) else {
                    bpmFloat=settings.customBpm.toDouble();settings.customBpm
                }
                state=state.copy(stageIndex=0,stageName="自定义",bpm=bpm,targetBpm=settings.customBpm,stageRemainingMs=0)
            }
            RunMode.GUIDE,RunMode.TIMED -> {
                val weights=if(state.mode==RunMode.TIMED)listOf(120.0,180.0,240.0,150.0) else settings.ratios
                val ratios=weights.map { it/weights.sum() }; var before=0.0; var index=3
                for(i in 0..3) { if(elapsed<before+ratios[i]*total) { index=i; break }; before+=ratios[i]*total }
                val progress=((elapsed-before)/(ratios[index]*total).coerceAtLeast(1.0)).coerceIn(0.0,1.0)
                // Shared endpoints and smoothstep keep both tempo and its slope continuous at stage boundaries.
                val smoothProgress=progress*progress*(3-2*progress)
                val target=(scheduledBpms[index]+(scheduledBpms[index+1]-scheduledBpms[index])*smoothProgress).roundToInt()
                state=state.copy(stageIndex=index,stageName=listOf("热身","燃脂","冲刺","放松")[index],bpm=glideTo(target,elapsed),targetBpm=target,stageRemainingMs=(before+ratios[index]*total-elapsed).toLong().coerceAtLeast(0))
            }
            RunMode.FOLLOW -> {
                val percent=hr?.takeIf { settings.withWearable }?.let(settings::hrPercent); val mins=listOf(120_000L,180_000L,180_000L,120_000L)
                val desired=if(percent==null) { var remaining=elapsed%mins.sum();var i=0;while(i<3 && remaining>=mins[i])remaining-=mins[i++];i } else when { percent<60->0;percent<70->1;percent<80->2;else->3 }
                if(percent==null || !pickedHr || elapsed-stageStart>=mins[state.stageIndex]) {
                    if(desired!=state.stageIndex)stageStart=elapsed
                    state=state.copy(stageIndex=desired);if(percent!=null)pickedHr=true
                }
                val standard=listOf(150,162,172,182)[state.stageIndex]
                val mapped=hr?.takeIf { percent!=null }?.let { (150+((it-95)/85.0).coerceIn(0.0,1.0)*35).roundToInt() }
                val target=mapped?.coerceIn(standard-6,standard+6) ?: standard
                state=state.copy(stageName=listOf("热身","燃脂","有氧","强化")[state.stageIndex],bpm=glideTo(target,elapsed),targetBpm=target,stageRemainingMs=0)
            }
        }
        previous=elapsed;return state
    }
    private fun glideTo(target:Int,elapsed:Long):Int {
        val maxDelta=(elapsed-previous).coerceIn(0,2000)/1000.0*2.5
        bpmFloat+=(target-bpmFloat).coerceIn(-maxDelta,maxDelta)
        if(bpmFloat==target.toDouble())modeTransition=false
        return bpmFloat.roundToInt()
    }
}
