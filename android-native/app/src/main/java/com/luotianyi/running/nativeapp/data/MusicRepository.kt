package com.luotianyi.running.nativeapp.data
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.luotianyi.running.nativeapp.audio.BpmAnalyzer
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

data class ImportProgress(val running:Boolean=false,val done:Int=0,val total:Int=0,val successful:Int=0,val failures:List<String> = emptyList(),val message:String="")
class MusicRepository(private val context:Context,private val db:AppDatabase) {
    private val detectionMutex=Mutex()
    val songs=db.music().observeSongs();val playlists=db.music().observePlaylists()
    private val catalog by lazy {
        val rows=JSONObject(context.assets.open("bpm_library.json").bufferedReader().use {it.readText()}).getJSONArray("songs")
        val map=HashMap<String,MutableList<Pair<Int,Int>>>()
        for(i in 0 until rows.length()) {val r=rows.getJSONArray(i);val title=r.getString(0);val artist=r.getString(1);val value=r.getInt(2) to r.getInt(3)
            for(name in listOf(title,"$artist - $title","$title - $artist"))map.getOrPut(normalize(name)) {mutableListOf()}.add(value)
        };map
    }
    private fun normalize(s:String)=s.substringBeforeLast('.',s).lowercase().replace(Regex("[\\s\\p{Punct}（）【】《》]"),"")
    private fun lookup(title:String,duration:Long):Int?=catalog[normalize(title)]?.minByOrNull { kotlin.math.abs(it.first-duration/1000) }?.second
    suspend fun import(uris:List<Uri>,progress:(ImportProgress)->Unit):List<SongEntity> = withContext(Dispatchers.IO) {
        val result=mutableListOf<SongEntity>();val failures=mutableListOf<String>();val directory=File(context.filesDir,"music").apply {mkdirs()}
        uris.forEachIndexed {i,uri->
            var name="文件 ${i+1}";val temporary=File(directory,"${UUID.randomUUID()}.part")
            try {
                context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use {cursor->if(cursor.moveToFirst())name=cursor.getString(0)}
                val digest=MessageDigest.getInstance("SHA-256")
                context.contentResolver.openInputStream(uri)?.use {input->temporary.outputStream().use {output->
                    val bytes=ByteArray(64*1024);var read=input.read(bytes)
                    while(read>=0) {currentCoroutineContext().ensureActive();output.write(bytes,0,read);digest.update(bytes,0,read);read=input.read(bytes)}
                }} ?: error("无法读取文件")
                val retriever=MediaMetadataRetriever()
                val metadata=try {retriever.setDataSource(temporary.absolutePath);Triple(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: name.substringBeforeLast('.',name),retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "",retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0)} finally {retriever.release()}
                require(metadata.third>0) {"文件不是可播放音频或时长无法读取"}
                val id=digest.digest().joinToString("") {"%02x".format(it)}
                val file=File(directory,id)
                val exists=db.music().songs().firstOrNull {it.id==id}
                if(file.exists())temporary.delete() else check(temporary.renameTo(file)) {"音频保存失败"}
                val song=exists?.copy(removedFromQueue=false) ?: SongEntity(id,metadata.first,metadata.second,file.absolutePath,metadata.third,lookup(name,metadata.third) ?: lookup(metadata.first,metadata.third))
                try {db.music().upsertSong(song)} catch(e:Exception) {if(exists==null)file.delete();throw e}
                result.add(song)
            } catch(e:kotlinx.coroutines.CancellationException) {throw e}
            catch(e:Exception) {failures.add("$name：${e.message}")}
            finally {temporary.delete()}
            progress(ImportProgress(true,i+1,uris.size,result.size,failures.toList()))
        };result.distinctBy {it.id}
    }
    suspend fun playlist(id:String)=db.music().playlistSongs(id)
    suspend fun savePlaylist(name:String,songs:List<SongEntity>,target:String?=null):String {
        require(name.isNotBlank() || target!=null)
        val id=target ?: UUID.randomUUID().toString()
        db.withTransaction {
            if(target==null)db.music().insertPlaylist(PlaylistEntity(id,name.trim(),System.currentTimeMillis()))
            val existing=db.music().playlistSongs(id).map {it.id}.toSet();var position=db.music().lastPosition(id)+1
            db.music().insertLinks(songs.distinctBy {it.id}.filter {it.id !in existing}.map {PlaylistSongEntity(id,it.id,position++)})
        };return id
    }
    suspend fun detect(song:SongEntity):Int?=detectionMutex.withLock {withContext(Dispatchers.IO) {
        val bpm=lookup(song.title,song.durationMillis) ?: try {BpmAnalyzer.analyze(song.localPath)} catch(e:kotlinx.coroutines.CancellationException) {throw e} catch(_:Exception) {null}
        db.music().setBpm(song.id,bpm);bpm
    }}
}
