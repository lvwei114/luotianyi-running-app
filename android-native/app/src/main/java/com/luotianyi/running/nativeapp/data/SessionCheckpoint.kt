package com.luotianyi.running.nativeapp.data
import android.content.Context
import android.util.AtomicFile
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
class SessionCheckpoint(context:Context) {
    private val file=AtomicFile(File(context.filesDir,"active_workout.json"))
    @Synchronized fun write(w:WorkoutEntity,samples:List<HeartRateSampleEntity>) {
        val j=JSONObject().put("id",w.id).put("startedAt",w.startedAt).put("endedAt",w.endedAt).put("mode",w.mode).put("timedMinutes",w.timedMinutes ?: JSONObject.NULL).put("elapsedMillis",w.elapsedMillis).put("gpsMeters",w.gpsMeters).put("totalSteps",w.totalSteps).put("gpsStepMeters",w.gpsStepMeters).put("gpsSteps",w.gpsSteps)
        j.put("heartRates",JSONArray().also {a->samples.forEach {a.put(JSONArray(listOf(it.elapsedMillis,it.bpm)))}})
        val stream=file.startWrite();try {stream.write(j.toString().toByteArray(Charsets.UTF_8));file.finishWrite(stream)} catch(e:Exception) {file.failWrite(stream);throw e}
    }
    @Synchronized fun read():Pair<WorkoutEntity,List<HeartRateSampleEntity>>? {
        if(!file.baseFile.exists())return null
        val j=JSONObject(file.openRead().bufferedReader().use {it.readText()})
        val w=WorkoutEntity(j.getString("id"),j.getLong("startedAt"),j.getLong("endedAt"),j.getString("mode"),if(j.isNull("timedMinutes"))null else j.getInt("timedMinutes"),j.getLong("elapsedMillis"),j.getDouble("gpsMeters"),j.getLong("totalSteps"),j.getDouble("gpsStepMeters"),j.getLong("gpsSteps"))
        val a=j.getJSONArray("heartRates");return w to List(a.length()) {val p=a.getJSONArray(it);HeartRateSampleEntity(w.id,p.getLong(0),p.getInt(1))}
    }
    @Synchronized fun clear() {file.delete();check(!file.baseFile.exists()) {"检查点文件无法删除，请检查存储空间"}}
}
