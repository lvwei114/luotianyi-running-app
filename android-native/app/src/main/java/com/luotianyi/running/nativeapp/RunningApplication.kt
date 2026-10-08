package com.luotianyi.running.nativeapp

import android.app.Application
import androidx.room.Room
import com.luotianyi.running.nativeapp.data.AppDatabase
import com.luotianyi.running.nativeapp.data.SettingsRepository
import com.luotianyi.running.nativeapp.data.WorkoutRepository
import com.luotianyi.running.nativeapp.data.MusicRepository
import com.luotianyi.running.nativeapp.data.SessionCheckpoint
import com.luotianyi.running.nativeapp.data.AppSettings
import com.luotianyi.running.nativeapp.audio.AudioFocusOwner
import com.luotianyi.running.nativeapp.audio.MusicManager
import com.luotianyi.running.nativeapp.audio.VoicePlayer
import com.luotianyi.running.nativeapp.device.HeartRateClient
import com.luotianyi.running.nativeapp.service.RunState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class RunningApplication : Application() {
    val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "luotianyi.db").build()
    }
    val settingsRepository by lazy { SettingsRepository(this) }
    val workoutRepository by lazy { WorkoutRepository(database) }
    val musicRepository by lazy { MusicRepository(this,database) }
    val audioFocus by lazy { AudioFocusOwner(this) }
    val music by lazy { MusicManager(this) }
    val voice by lazy { VoicePlayer(this) {playing -> music.duck(playing);audioFocus.voiceActive=playing;if(playing)audioFocus.acquire() else audioFocus.releaseIfIdle()} }
    val heartRate by lazy { HeartRateClient(this) }
    val checkpoint by lazy { SessionCheckpoint(this) }
    val runState=MutableStateFlow(RunState())
    val message=MutableSharedFlow<String>(replay=1,extraBufferCapacity=8)
    val recovering=MutableStateFlow(true)
    var latestSettings=AppSettings();private set
    fun postMessage(text:String) { if(text.isNotEmpty()) message.tryEmit(text) }
    fun discardRecovery() {
        if(recovering.value || runState.value.recoveryError.isEmpty())return
        recovering.value=true
        scope.launch {
            try {withContext(Dispatchers.IO) {checkpoint.clear()};runState.value=runState.value.copy(recoveryError="");postMessage("已放弃上次损坏的训练记录，可重新开始训练")}
            catch(e:Exception) {postMessage("记录清除失败：${e.message}")}
            finally {recovering.value=false}
        }
    }
    fun retryRecovery() {
        if(recovering.value)return
        recovering.value=true
        recoverCheckpoint()
    }
    private fun recoverCheckpoint()=scope.launch {
        try {
            val recovered=withContext(Dispatchers.IO) {checkpoint.read()}
            if(recovered!=null) {
                workoutRepository.save(recovered.first,recovered.second)
                withContext(Dispatchers.IO) {checkpoint.clear()}
                postMessage("上次训练意外中断，已恢复最近保存的记录；中断期间未补算时间和距离")
            }
            runState.value=runState.value.copy(recoveryError="")
        } catch(e:Exception) {
            runState.value=runState.value.copy(recoveryError="上次记录恢复失败，请重试：${e.message}")
        } finally {recovering.value=false}
    }
    override fun onCreate() {
        super.onCreate()
        scope.launch {
            try {settingsRepository.settings.collect {s -> latestSettings=s;music.updateSettings(s);voice.volume=s.masterVolume/100f*s.voiceVolume/100f}}
            catch(e:Exception) {postMessage("设置读取失败：${e.message}")}
        }
        recoverCheckpoint()
        scope.launch {
            try {
                val s=settingsRepository.settings.first();val songs=database.music().songs()
                val queue=if(s.lastQueue.isEmpty())songs.filter {!it.removedFromQueue} else s.lastQueue.mapNotNull {id->songs.find {it.id==id && !it.removedFromQueue}}
                music.setQueue(queue)
                music.state.map {it.queue.map {song->song.id}}.distinctUntilChanged().drop(1).collect {ids->settingsRepository.update {it.copy(lastQueue=ids)}}
            } catch(e:Exception) {postMessage("歌曲恢复失败：${e.message}")}
        }
        scope.launch {database.music().observeSongs().collect {music.refreshMetadata(it)}}
        scope.launch {while(isActive) {music.poll();delay(500)}}
    }
}
