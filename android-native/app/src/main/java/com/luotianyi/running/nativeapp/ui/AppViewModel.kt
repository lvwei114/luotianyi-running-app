package com.luotianyi.running.nativeapp.ui
import android.net.Uri
import androidx.lifecycle.*
import com.luotianyi.running.nativeapp.RunningApplication
import com.luotianyi.running.nativeapp.audio.MusicState
import com.luotianyi.running.nativeapp.audio.NativeMetronome
import com.luotianyi.running.nativeapp.data.*
import com.luotianyi.running.nativeapp.device.HrState
import com.luotianyi.running.nativeapp.domain.*
import com.luotianyi.running.nativeapp.service.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.channels.Channel
import org.json.JSONArray
import java.time.LocalDate

data class AlbumSong(val title:String,val bpm:Int,val durationMs:Long)
data class Album(val name:String,val songs:List<AlbumSong>)
data class WorkoutDetail(val record:WorkoutEntity,val samples:List<HeartRateSampleEntity>)
data class PlaylistDetail(val playlist:PlaylistEntity,val songs:List<SongEntity>)
data class AppUiState(
    val settings:AppSettings=AppSettings(),val settingsLoaded:Boolean=false,val workoutsLoaded:Boolean=false,
    val workouts:List<WorkoutEntity> = emptyList(),val songs:List<SongEntity> = emptyList(),val playlists:List<PlaylistEntity> = emptyList(),
    val run:RunState=RunState(),val music:MusicState=MusicState(),val hr:HrState=HrState(),val subtitle:String="",val speakingVoiceId:String="",
    val backgrounds:List<BackgroundEntry> = emptyList(),val albums:List<Album> = emptyList(),
    val error:String?=null,val recovering:Boolean=true,val importProgress:ImportProgress=ImportProgress(),
    val detecting:Set<String> = emptySet(),val detail:WorkoutDetail?=null,val playlistDetail:PlaylistDetail?=null,val today:LocalDate=LocalDate.now(),
    val streakMilestone:StreakMilestone?=null,
) {
    val busy get()=run.training.phase in listOf(RunPhase.RUNNING,RunPhase.PAUSED,RunPhase.PREPARING) || run.saving
}
class AppViewModel(private val app:RunningApplication):ViewModel() {
    private val mutable=MutableStateFlow(AppUiState());val state=mutable.asStateFlow()
    private val messageQueue=Channel<String>(Channel.UNLIMITED);val messages=messageQueue.receiveAsFlow()
    private val backgrounds=BackgroundRepository(app);private var lastImported:List<SongEntity> = emptyList();private var importTarget:String?=null;private var importName=""
    private var preview:NativeMetronome?=null;private var previewJob:Job?=null;private var previewGeneration=0L
    private var auditioning=false
    init {
        launchHandled {app.settingsRepository.settings.collect {s->mutable.update {it.copy(settings=s,settingsLoaded=true)}}}
        launchHandled {combine(app.workoutRepository.workouts,app.settingsRepository.seenStreakMilestones,state.map {it.today}.distinctUntilChanged()) {w,seen,today->
            w to RunStreaks.calculate(w,today).milestone?.takeUnless {it.key in seen}
        }.collect {(w,milestone)->mutable.update {it.copy(workouts=w,workoutsLoaded=true,streakMilestone=milestone)}}}
        launchHandled {app.database.music().observeSongs().collect {songs->mutable.update {it.copy(songs=songs)}}}
        launchHandled {app.database.music().observePlaylists().collect {p->mutable.update {it.copy(playlists=p)}}}
        launchHandled {app.runState.collect {run->mutable.update {it.copy(run=run)}}}
        launchHandled {app.music.state.collect {music->
            mutable.update {it.copy(music=music)}
            music.queue.getOrNull(music.index)?.takeIf {it.bpm==null && !it.bpmTried && music.playing}?.let(::detect)
        }}
        launchHandled {while(currentCoroutineContext().isActive) {val hr=app.heartRate.state.value;mutable.update {it.copy(hr=hr.copy(bpm=app.heartRate.freshBpm()))};delay(1000)}}
        launchHandled {app.voice.subtitle.collect {text->mutable.update {it.copy(subtitle=text)}}}
        launchHandled {app.voice.playingId.collect {id->mutable.update {it.copy(speakingVoiceId=id)}}}
        launchHandled {app.message.collect {text->if(text.isNotEmpty())message(text)}}
        launchHandled {app.recovering.collect {value->mutable.update {it.copy(recovering=value)}}}
        refreshBackgrounds()
        launchHandled {val albums=withContext(Dispatchers.IO) {val a=JSONArray(app.assets.open("music_meta.json").bufferedReader().use {it.readText()});List(a.length()) {index->val j=a.getJSONObject(index);val songs=j.getJSONArray("songs");Album(j.getString("album"),List(songs.length()) {i->val s=songs.getJSONObject(i);AlbumSong(s.getString("title"),s.optInt("bpm"),s.optLong("duration")*1000)})}};mutable.update {it.copy(albums=albums)}}
        launchHandled {while(currentCoroutineContext().isActive) {delay(30000);mutable.update {it.copy(today=LocalDate.now())}}}
    }
    fun settings(change:(AppSettings)->AppSettings)=launchHandled {app.settingsRepository.update(change)}
    fun dismissStreakMilestone()=launchHandled {
        val milestone=state.value.streakMilestone ?: return@launchHandled
        app.settingsRepository.acknowledgeStreakMilestone(milestone.key)
        mutable.update {it.copy(streakMilestone=null)}
    }
    fun setTimedMinutes(value:Int) {if(!state.value.busy)settings {it.copy(timedMinutes=value.coerceIn(5,60))}}
    fun setLegacyMode(mode:RunMode) {
        if(state.value.busy) {if(state.value.run.training.mode==RunMode.TIMED || mode==RunMode.TIMED) {message("本次定时跑不能切换跑法");return};RunService.command(app,"MODE",mode)}
        settings {it.copy(legacyMode=mode)}
    }
    fun setDisplayTier(tier:DisplayTier)=settings {it.copy(displayTier=tier)}
    fun start(mode:RunMode) {
        if(state.value.busy || state.value.recovering)return
        if(state.value.run.recoveryError.isNotEmpty()) {message("请先恢复上次训练记录");return}
        if(state.value.run.saveError.isNotEmpty()) {message("请先重试保存上一条记录");return}
        stopPreview();stopVoicePreview();RunService.command(app,"START",mode)
    }
    fun runCommand(command:String)=RunService.command(app,command)
    fun clearAlert() {app.runState.value=app.runState.value.copy(alert="")}
    fun retryRecovery()=app.retryRecovery()
    fun discardRecovery()=app.discardRecovery()
    fun retrySave()=launchHandled {
        val run=app.runState.value;val record=run.completed ?: return@launchHandled
        app.runState.value=run.copy(saving=true)
        try {app.workoutRepository.save(record,run.samples);withContext(Dispatchers.IO) {app.checkpoint.clear()};app.runState.value=app.runState.value.copy(saveError="",saving=false)}
        catch(e:Exception) {app.runState.value=app.runState.value.copy(saveError="保存失败：${e.message}",saving=false)}
    }
    fun dismissSummary() {if(!app.runState.value.saving && app.runState.value.saveError.isEmpty())app.runState.value=app.runState.value.copy(completed=null)}
    fun detail(w:WorkoutEntity)=launchHandled {val samples=app.database.workouts().heartRates(w.id);mutable.update {it.copy(detail=WorkoutDetail(w,samples))}}
    fun closeDetail()=mutable.update {it.copy(detail=null)}
    fun deleteRecord(id:String)=launchHandled {app.database.workouts().delete(id);closeDetail()}
    fun openPlaylist(p:PlaylistEntity)=launchHandled {val songs=app.musicRepository.playlist(p.id);mutable.update {it.copy(playlistDetail=PlaylistDetail(p,songs))}}
    fun closePlaylist()=mutable.update {it.copy(playlistDetail=null)}
    fun deletePlaylist(id:String)=launchHandled {app.database.music().deletePlaylist(id);closePlaylist()}
    fun renamePlaylist(id:String,name:String)=launchHandled {require(name.isNotBlank());app.database.music().renamePlaylist(id,name.trim());closePlaylist()}
    fun usePlaylist(songs:List<SongEntity>,append:Boolean=false,play:Boolean=false) {if(append)app.music.add(songs) else app.music.setQueue(songs,play);closePlaylist()}
    fun toggleMusic()=app.music.toggle()
    fun next()=app.music.next()
    fun previous()=app.music.previous()
    fun seek(position:Long)=app.music.seek(position)
    fun playSong(id:String) {val current=state.value.music.queue.indexOfFirst {it.id==id};if(current>=0)app.music.playAt(current) else state.value.songs.find {it.id==id}?.let {app.music.add(listOf(it));app.music.playAt(app.music.state.value.queue.indexOfFirst {s->s.id==id})}}
    fun like(id:String)=launchHandled {app.database.music().toggleLike(id)}
    fun remove(id:String)=launchHandled {app.database.music().setRemoved(id,true);app.music.remove(id)}
    fun restore(song:SongEntity)=launchHandled {app.database.music().setRemoved(song.id,false);app.music.add(listOf(song))}
    fun musicMode(mode:String)=settings {it.copy(musicMode=mode)}
    fun scan()=app.heartRate.scan()
    fun connect(address:String)=app.heartRate.connect(address)
    fun disconnect()=app.heartRate.disconnect()
    fun importMusic(uris:List<Uri>,name:String,target:String?) {
        if(state.value.importProgress.running)return
        importName=name;importTarget=target;lastImported=emptyList()
        launchHandled {
            mutable.update {it.copy(importProgress=ImportProgress(true,total=uris.size))}
            try {
                lastImported=app.musicRepository.import(uris) {progress->mutable.update {it.copy(importProgress=progress)}}
                app.music.add(lastImported)
                if(lastImported.isNotEmpty())app.musicRepository.savePlaylist(name,lastImported,target)
                mutable.update {it.copy(importProgress=it.importProgress.copy(running=false,message="导入完成，歌单已保存"))}
                lastImported.filter {it.bpm==null && !it.bpmTried}.take(3).forEach(::detect)
            } catch(e:CancellationException) {mutable.update {it.copy(importProgress=it.importProgress.copy(running=false,message="导入已停止，已完成的音频保留在本机"))};throw e}
            catch(e:Exception) {mutable.update {it.copy(importProgress=it.importProgress.copy(running=false,message="${e.message}；音频已保存，可重试保存歌单"))}}
        }
    }
    fun retryPlaylistSave()=launchHandled {require(lastImported.isNotEmpty());app.musicRepository.savePlaylist(importName,lastImported,importTarget);mutable.update {it.copy(importProgress=it.importProgress.copy(message="歌单已保存"))}}
    fun detect(song:SongEntity) {
        if(song.id in state.value.detecting)return
        mutable.update {it.copy(detecting=it.detecting+song.id)}
        launchHandled {try {val bpm=app.musicRepository.detect(song);message("${song.title}：${bpm?.let {"$it BPM"} ?: "未识别到 BPM"}")} finally {mutable.update {it.copy(detecting=it.detecting-song.id)}}}
    }
    fun detectAll() {state.value.music.queue.filter {it.bpm==null}.forEach(::detect)}
    fun savePlayed(name:String)=launchHandled {val songs=app.music.playedIds.mapNotNull {id->state.value.songs.find {it.id==id}};require(songs.isNotEmpty()) {"本次没有播放歌曲"};app.musicRepository.savePlaylist(name,songs);app.runState.value=app.runState.value.copy(playedPlaylistSaved=true);message("本次歌单已保存")}
    fun refreshBackgrounds()=launchHandled {val entries=withContext(Dispatchers.IO) {backgrounds.list()};mutable.update {it.copy(backgrounds=entries)}}
    fun addBackgrounds(uris:List<Uri>)=launchHandled {
        val errors=mutableListOf<String>();var added=0
        for(uri in uris)try {val path=backgrounds.add(uri);app.settingsRepository.update {it.copy(background="custom:"+java.io.File(path).name)};added++} catch(e:CancellationException) {throw e} catch(e:Exception) {errors.add(e.message ?: "图片失败")}
        refreshBackgrounds();message("图片添加 $added 张${if(errors.isNotEmpty())"，失败 ${errors.size} 张：${errors.first()}" else ""}")
    }
    fun deleteBackground(entry:BackgroundEntry)=launchHandled {backgrounds.delete(entry);app.settingsRepository.update {it.copy(background="builtin:bg1")};refreshBackgrounds()}
    fun avatar(uri:Uri)=launchHandled {val path=backgrounds.add(uri,"avatars");app.settingsRepository.update {it.copy(avatar=path)}}
    fun previewTone(tone:String) {
        settings {it.copy(beatTone=tone)};if(state.value.busy)return
        stopVoicePreview()
        stopPreview()
        if(!app.audioFocus.acquire()) {message("暂时无法试听，请稍后重试");return}
        val generation=++previewGeneration
        app.audioFocus.previewActive=true;app.music.duckPreview(true)
        preview=NativeMetronome(::message).also {it.bpm=120;it.tone=tone;it.volume=state.value.settings.masterVolume/100f*state.value.settings.beatVolume/100f;it.start()}
        previewJob=launchHandled {
            try {repeat(19) {delay(50);if(!app.audioFocus.previewActive)return@launchHandled}}
            finally {if(generation==previewGeneration)stopPreview()}
        }
    }
    fun previewVoice(line:VoiceLine) {
        if(state.value.busy) {message("训练中请使用自动播报，结束后可试听语音");return}
        stopPreview()
        if(!app.audioFocus.acquire()) {message("暂时无法试听，请稍后重试");return}
        auditioning=true;app.voice.preview(line)
    }
    fun stopVoicePreview() {if(auditioning) {auditioning=false;app.voice.cancel()}}
    private fun stopPreview() {previewGeneration++;previewJob?.cancel();previewJob=null;preview?.stop();preview=null;app.audioFocus.previewActive=false;app.music.duckPreview(false);app.audioFocus.releaseIfIdle()}
    fun export(uri:Uri)=launchHandled {DataTransfer(app).export(uri);message("记录、歌单和设置已导出；音频和图片请另外保留")}
    fun importBackup(uri:Uri)=launchHandled {check(!state.value.busy && !state.value.importProgress.running) {"请结束训练和文件导入后再恢复备份"};val count=DataTransfer(app).import(uri);message("已导入 $count 条 GPS 记录；歌曲需先导入，歌单按文件标识或唯一名称匹配");refreshBackgrounds()}
    fun dismissError()=mutable.update {it.copy(error=null)}
    fun message(text:String) {if(text.isNotEmpty())messageQueue.trySend(text)}
    private fun launchHandled(block:suspend ()->Unit)=viewModelScope.launch {try {block()} catch(e:CancellationException) {throw e} catch(e:Exception) {mutable.update {it.copy(error=e.message ?: "操作失败，请重试")}}}
    override fun onCleared() {stopPreview();stopVoicePreview();messageQueue.close();super.onCleared()}
    class Factory(private val app:RunningApplication):ViewModelProvider.Factory {
        override fun <T:ViewModel> create(modelClass:Class<T>):T {require(modelClass.isAssignableFrom(AppViewModel::class.java));@Suppress("UNCHECKED_CAST") return AppViewModel(app) as T}
    }
}
