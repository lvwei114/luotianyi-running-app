package com.luotianyi.running.nativeapp.data
import android.net.Uri
import androidx.room.withTransaction
import com.luotianyi.running.nativeapp.RunningApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Metadata backup excludes audio/image bytes; files can be reselected and matched by hash/title. */
class DataTransfer(private val app:RunningApplication) {
    suspend fun export(uri:Uri)=withContext(Dispatchers.IO) {
        val records=JSONArray()
        for(w in app.workoutRepository.workouts.first()) {
            val hr=JSONArray();for(p in app.database.workouts().heartRates(w.id))hr.put(JSONObject().put("t",p.elapsedMillis/1000.0).put("hr",p.bpm))
            records.put(JSONObject().put("id",w.id).put("startedAt",w.startedAt).put("endedAt",w.endedAt).put("runMode",w.mode.lowercase()).put("timedMinutes",w.timedMinutes ?: JSONObject.NULL).put("elapsed",w.elapsedMillis/1000.0).put("distanceSource","gps").put("distanceMeters",w.gpsMeters).put("steps",w.totalSteps).put("gpsSteps",w.gpsSteps).put("gpsStepMeters",w.gpsStepMeters).put("hrSeries",hr))
        }
        val playlists=JSONArray()
        for(p in app.database.music().playlists()) {
            val songs=JSONArray();for(s in app.musicRepository.playlist(p.id))songs.put(JSONObject().put("name",s.title).put("id",s.id).put("liked",s.liked))
            playlists.put(JSONObject().put("id",p.id).put("name",p.name).put("songs",songs))
        }
        val root=JSONObject().put("format","luotianyi-native-export").put("version",1).put("records",records).put("playlists",playlists).put("settings",JSONObject(app.settingsRepository.exportJson()))
        app.contentResolver.openOutputStream(uri,"wt")?.bufferedWriter(Charsets.UTF_8)?.use {it.write(root.toString(2))} ?: error("无法写入备份")
    }
    suspend fun import(uri:Uri):Int=withContext(Dispatchers.IO) {
        val bytes=app.contentResolver.openInputStream(uri)?.use {input->
            val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);var size=input.read(buffer)
            while(size>=0) {require(output.size()+size<=32*1024*1024) {"备份过大"};output.write(buffer,0,size);size=input.read(buffer)};output.toByteArray()
        } ?: error("无法读取备份")
        val root=JSONObject(String(bytes,Charsets.UTF_8));val native=root.optString("format")=="luotianyi-native-export"
        if(native)root.put("format","luotianyi-web-export")
        val count=app.database.withTransaction {LegacyImport(app).import(root.toString(),native)}
        if(native && root.has("settings"))app.settingsRepository.restoreJson(root.getJSONObject("settings").toString())
        count
    }
}
