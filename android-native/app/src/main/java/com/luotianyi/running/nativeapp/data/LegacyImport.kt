package com.luotianyi.running.nativeapp.data
import com.luotianyi.running.nativeapp.RunningApplication
import com.luotianyi.running.nativeapp.domain.RunMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
/** Old WebView metadata imports GPS records only; audio must be reselected in the new sandbox. */
class LegacyImport(private val app:RunningApplication) {
    suspend fun import(json:String,preservePlaylistIds:Boolean=false):Int=withContext(Dispatchers.IO) {
        val envelope=JSONObject(json);require(envelope.optString("format")=="luotianyi-web-export" && envelope.optInt("version")==1) {"不支持的旧版导出格式"}
        val array=envelope.getJSONArray("records");var count=0
        for(i in 0 until array.length()) {
            val j=array.getJSONObject(i);require(j.optString("distanceSource")=="gps") {"旧记录距离来源不是 GPS，已停止导入"}
            val mode=when(j.getString("runMode")) {"follow"->RunMode.FOLLOW;"custom"->RunMode.CUSTOM;"timed"->RunMode.TIMED;else->RunMode.GUIDE}
            val recordId=j.getString("id");val w=WorkoutEntity(recordId,j.getLong("startedAt"),j.getLong("endedAt"),mode.name,if(mode==RunMode.TIMED)j.getInt("timedMinutes") else null,(j.getDouble("elapsed")*1000).toLong(),j.getDouble("distanceMeters"),j.getLong("steps"),j.getDouble("gpsStepMeters"),j.getLong("gpsSteps"))
            val hr=j.optJSONArray("hrSeries");val samples=if(hr!=null)List(hr.length()) {val p=hr.getJSONObject(it);HeartRateSampleEntity(w.id,(p.getDouble("t")*1000).toLong(),p.getInt("hr"))} else emptyList()
            app.workoutRepository.save(w,samples);count++
        }
        val playlists=envelope.optJSONArray("playlists");val songs=app.database.music().songs()
        if(playlists!=null)for(i in 0 until playlists.length()) {
            val p=playlists.getJSONObject(i);val oldSongs=p.getJSONArray("songs")
            val mapped=(0 until oldSongs.length()).mapNotNull {index->val t=oldSongs.getJSONObject(index);val name=t.optString("name");songs.find {it.id==t.optString("id")} ?: songs.singleOrNull {it.title==name || it.title==name.substringBeforeLast('.',name)}}
            val originalId=p.optString("id",p.getString("name"));val id=if(preservePlaylistIds || originalId.startsWith("import:"))originalId else "import:"+originalId;val existing=app.database.music().playlists().any {it.id==id}
            if(!existing)app.database.music().insertPlaylist(PlaylistEntity(id,p.getString("name"),p.optLong("createdAt",System.currentTimeMillis())))
            if(mapped.isNotEmpty())app.musicRepository.savePlaylist(p.getString("name"),mapped,id)
        }
        count
    }
}
