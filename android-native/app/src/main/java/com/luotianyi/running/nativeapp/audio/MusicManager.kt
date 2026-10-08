package com.luotianyi.running.nativeapp.audio
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.luotianyi.running.nativeapp.RunningApplication
import com.luotianyi.running.nativeapp.data.AppSettings
import com.luotianyi.running.nativeapp.data.SongEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import kotlin.math.abs

data class MusicState(val queue:List<SongEntity> = emptyList(),val index:Int=-1,val playing:Boolean=false,val error:String="",val mode:String="order",val position:Long=0)
class MusicManager(private val app:RunningApplication) {
    val player=ExoPlayer.Builder(app).build().apply {setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),false);setHandleAudioBecomingNoisy(true)}
    private val mutable=MutableStateFlow(MusicState());val state=mutable.asStateFlow()
    private var settings=AppSettings();private var ducked=false;private var previewDucked=false;var repeatWorkout=false;var heartRate:Int?=null
    val playedIds=linkedSetOf<String>();private val smartPlayed=mutableSetOf<String>()
    internal val playbackListener=object:Player.Listener {
        override fun onIsPlayingChanged(isPlaying:Boolean) {mutable.value=mutable.value.copy(playing=isPlaying,error=if(isPlaying)"" else mutable.value.error);app.audioFocus.musicActive=isPlaying;app.audioFocus.releaseIfIdle()}
        override fun onMediaItemTransition(item:MediaItem?,reason:Int) {
            if(reason==Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && mutable.value.mode in listOf("smart","shuffle")) {playAt(selectionIndex());return}
            sync();mutable.value=mutable.value.copy(error="");item?.mediaId?.let {playedIds.add(it);smartPlayed.add(it)}
        }
        override fun onPlaybackStateChanged(playbackState:Int) {if(playbackState==Player.STATE_ENDED && (mutable.value.mode in listOf("smart","shuffle") || repeatWorkout))next()}
        override fun onPlayerError(error:PlaybackException) {mutable.value=mutable.value.copy(error="播放失败：${error.message}",playing=false)}
    }
    init {player.addListener(playbackListener)}
    fun updateSettings(s:AppSettings) {settings=s;player.volume=s.masterVolume/100f*s.musicVolume/100f*(if(ducked || previewDucked).3f else 1f);if(mutable.value.mode!=s.musicMode)setMode(s.musicMode)}
    fun duck(value:Boolean) {ducked=value;updateSettings(settings)}
    fun duckPreview(value:Boolean) {previewDucked=value;updateSettings(settings)}
    fun setQueue(songs:List<SongEntity>,play:Boolean=false) {
        val queue=songs.distinctBy {it.id}.filter {File(it.localPath).isFile}
        mutable.value=mutable.value.copy(queue=queue,index=if(queue.isEmpty())-1 else 0,error=if(queue.size<songs.size)"部分音频缺失，请重新导入" else "")
        smartPlayed.clear();player.setMediaItems(queue.map {MediaItem.Builder().setMediaId(it.id).setUri(Uri.fromFile(File(it.localPath))).setMediaMetadata(MediaMetadata.Builder().setTitle(it.title).setArtist(it.artist).build()).build()})
        player.prepare();setMode(settings.musicMode);if(play)play()
    }
    fun add(songs:List<SongEntity>) {
        val new=songs.filter {candidate -> mutable.value.queue.none {it.id==candidate.id}}
        player.addMediaItems(new.map {MediaItem.Builder().setMediaId(it.id).setUri(Uri.fromFile(File(it.localPath))).setMediaMetadata(MediaMetadata.Builder().setTitle(it.title).setArtist(it.artist).build()).build()})
        mutable.value=mutable.value.copy(queue=mutable.value.queue+new);player.prepare();sync()
    }
    fun refreshMetadata(songs:List<SongEntity>) {val latest=songs.associateBy {it.id};mutable.value=mutable.value.copy(queue=mutable.value.queue.map {latest[it.id] ?: it})}
    fun remove(id:String) {val i=mutable.value.queue.indexOfFirst {it.id==id};if(i<0)return;player.removeMediaItem(i);mutable.value=mutable.value.copy(queue=mutable.value.queue.filter {it.id!=id});sync()}
    fun setMode(mode:String) {mutable.value=mutable.value.copy(mode=mode);player.shuffleModeEnabled=false;player.repeatMode=Player.REPEAT_MODE_OFF}
    fun play() {
        if(player.mediaItemCount==0)return
        if(!app.audioFocus.acquire()) {mutable.value=mutable.value.copy(error="无法获得音频播放权限，请稍后重试");return}
        ContextCompat.startForegroundService(app,Intent(app,MusicPlaybackService::class.java))
        if(player.playbackState==Player.STATE_ENDED)player.seekToDefaultPosition(if(mutable.value.mode=="smart")smartIndex() else 0)
        player.currentMediaItem?.mediaId?.let {playedIds.add(it);smartPlayed.add(it)};mutable.value=mutable.value.copy(error="");player.play()
    }
    fun toggle() {if(player.isPlaying)player.pause() else play()}
    fun playAt(i:Int) {if(i !in mutable.value.queue.indices)return;player.seekToDefaultPosition(i);sync();playedIds.add(mutable.value.queue[i].id);smartPlayed.add(mutable.value.queue[i].id);play()}
    fun next() {
        if(mutable.value.queue.isEmpty())return
        if(mutable.value.mode in listOf("smart","shuffle"))playAt(selectionIndex())
        else if(player.hasNextMediaItem()) {player.seekToNextMediaItem();play()}
        else if(repeatWorkout)playAt(0) else player.pause()
    }
    fun previous() {if(player.hasPreviousMediaItem())player.seekToPreviousMediaItem() else player.seekTo(0);play()}
    fun seek(position:Long) {player.seekTo(position.coerceIn(0,player.duration.coerceAtLeast(0)))}
    fun poll() {mutable.value=mutable.value.copy(position=player.currentPosition)}
    private fun selectionIndex():Int=if(mutable.value.mode=="shuffle") {
        val queue=mutable.value.queue;if(smartPlayed.size>=queue.size)smartPlayed.clear()
        val choices=queue.indices.filter {queue[it].id !in smartPlayed}
        val weighted=choices.flatMap {i->List(if(queue[i].liked)3 else 1) {i}}
        weighted.randomOrNull() ?: 0
    } else smartIndex()
    private fun smartIndex():Int {
        val queue=mutable.value.queue;if(smartPlayed.size>=queue.size)smartPlayed.clear()
        val target=heartRate?.let {150+((it-95)/85.0).coerceIn(0.0,1.0)*35} ?: 165.0
        return queue.indices.filter {queue[it].id !in smartPlayed}.minByOrNull {abs((queue[it].bpm ?: 120)-target)-(if(queue[it].liked)3 else 0)} ?: 0
    }
    private fun sync() {mutable.value=mutable.value.copy(index=player.currentMediaItemIndex,position=player.currentPosition)}
}
