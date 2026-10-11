package com.luotianyi.running.nativeapp.data
import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.preferencesDataStore
import com.luotianyi.running.nativeapp.domain.RunMode
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import org.json.JSONObject
private val Context.appPreferences by preferencesDataStore(name = "settings")
enum class DisplayTier(val label: String, val description: String) {
    HIGH("完整", "动画与心率环"), MEDIUM("简洁", "减少装饰与动画"), LOW("省电", "静态指标与心率条");
}
data class AppSettings(
    val timedMinutes: Int = 30, val legacyMode: RunMode = RunMode.GUIDE, val displayTier: DisplayTier = DisplayTier.HIGH,
    val nickname: String = "跑步的你", val avatar: String = "", val age: Int = 25, val height: Int = 170, val weight: Int = 60, val gender: String = "female",
    val customBpm: Int = 170, val ratios: List<Double> = listOf(120.0,180.0,240.0,150.0),
    val withWearable: Boolean = true, val hrFormula: String = "tanaka", val customMaxHr: Int = 190, val restingHr: Int = 0,
    val chatFrequency: String = "standard", val hydrationMinutes: Int = 20, val highTemp: Boolean = false, val safetyEnabled: Boolean = true,
    val masterVolume: Int = 80, val musicVolume: Int = 65, val voiceVolume: Int = 90, val beatVolume: Int = 50,
    val beatTone: String = "wood", val background: String = "builtin:bg1", val musicMode: String = "order",
    val lastQueue: List<String> = emptyList(), val disclaimerAccepted: Boolean = false,
) {
    fun maxHr(): Double = when(hrFormula) { "classic" -> 220.0-age; "custom" -> customMaxHr.toDouble(); else -> 208.0-.7*age }
    fun hrPercent(hr: Int): Double = if(restingHr in 35..120 && maxHr()>restingHr) (hr-restingHr)*100/(maxHr()-restingHr) else hr*100/maxHr()
    val estimatedStride get() = (height*(if(gender=="male") .415 else .413)/100).coerceIn(.3,1.5)
}
class SettingsRepository(context: Context) {
    private val store=context.applicationContext.appPreferences
    private val key=stringPreferencesKey("nativeSettings.v1")
    private val milestoneKey=stringPreferencesKey("streakMilestones.v1")
    val seenStreakMilestones=store.data.map {prefs->
        prefs[milestoneKey]?.split('\n')?.filter {it.isNotBlank()}?.toSet() ?: emptySet()
    }
    suspend fun acknowledgeStreakMilestone(value:String) {store.edit {prefs->
        val seen=prefs[milestoneKey]?.split('\n')?.filter {it.isNotBlank()}.orEmpty()
        prefs[milestoneKey]=(seen+value).distinct().joinToString("\n")
    }}
    val settings=store.data.map(::read)
    private fun read(values: Preferences): AppSettings {
        val raw=values[key] ?: return AppSettings(); val j=JSONObject(raw); val defaults=AppSettings()
        fun int(k:String,d:Int,min:Int,max:Int)=j.optInt(k,d).coerceIn(min,max)
        val ratios=j.optJSONArray("ratios")?.let { a -> List(a.length()) { a.optDouble(it,0.0).takeIf { n -> n.isFinite() && n>=0 } ?: 0.0 } }
        return AppSettings(
            timedMinutes=int("timedMinutes",30,5,60),legacyMode=RunMode.fromStored(j.optString("legacyMode")).takeUnless { it==RunMode.TIMED } ?: RunMode.GUIDE,
            displayTier=DisplayTier.entries.firstOrNull { it.name==j.optString("displayTier") } ?: DisplayTier.HIGH,
            nickname=j.optString("nickname",defaults.nickname),avatar=j.optString("avatar"),age=int("age",25,4,100),height=int("height",170,80,230),weight=int("weight",60,20,250),gender=j.optString("gender","female"),
            customBpm=int("customBpm",170,60,220),ratios=ratios?.takeIf { it.size==4 && it.sum()>0 } ?: defaults.ratios,
            withWearable=j.optBoolean("withWearable",true),hrFormula=j.optString("hrFormula","tanaka"),customMaxHr=int("customMaxHr",190,100,230),restingHr=j.optInt("restingHr",0).takeIf { it in 35..120 } ?: 0,
            chatFrequency=j.optString("chatFrequency","standard"),hydrationMinutes=j.optInt("hydrationMinutes",20).takeIf { it in listOf(0,15,20,30) } ?: 20,
            highTemp=j.optBoolean("highTemp",false),safetyEnabled=j.optBoolean("safetyEnabled",true),masterVolume=int("masterVolume",80,0,100),musicVolume=int("musicVolume",65,0,100),voiceVolume=int("voiceVolume",90,0,100),beatVolume=int("beatVolume",50,0,100),
            beatTone=j.optString("beatTone","wood"),background=j.optString("background","builtin:bg1"),musicMode=j.optString("musicMode","order"),
            lastQueue=j.optJSONArray("lastQueue")?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList(),disclaimerAccepted=j.optBoolean("disclaimerAccepted",false),
        )
    }
    suspend fun update(change:(AppSettings)->AppSettings) { store.edit { prefs ->
        val s=change(read(prefs)); val j=JSONObject()
        mapOf("timedMinutes" to s.timedMinutes,"legacyMode" to s.legacyMode.name,"displayTier" to s.displayTier.name,"nickname" to s.nickname,"avatar" to s.avatar,"age" to s.age,"height" to s.height,"weight" to s.weight,"gender" to s.gender,"customBpm" to s.customBpm,"ratios" to org.json.JSONArray(s.ratios),"withWearable" to s.withWearable,"hrFormula" to s.hrFormula,"customMaxHr" to s.customMaxHr,"restingHr" to s.restingHr,"chatFrequency" to s.chatFrequency,"hydrationMinutes" to s.hydrationMinutes,"highTemp" to s.highTemp,"safetyEnabled" to s.safetyEnabled,"masterVolume" to s.masterVolume,"musicVolume" to s.musicVolume,"voiceVolume" to s.voiceVolume,"beatVolume" to s.beatVolume,"beatTone" to s.beatTone,"background" to s.background,"musicMode" to s.musicMode,"lastQueue" to org.json.JSONArray(s.lastQueue),"disclaimerAccepted" to s.disclaimerAccepted).forEach { (k,v)->j.put(k,v) }
        prefs[key]=j.toString()
    } }
    suspend fun setTimedMinutes(minutes:Int) { require(minutes in 5..60); update { it.copy(timedMinutes=minutes) } }
    suspend fun setLegacyMode(mode:RunMode) { require(mode!=RunMode.TIMED); update { it.copy(legacyMode=mode) } }
    suspend fun setDisplayTier(tier:DisplayTier)=update { it.copy(displayTier=tier) }
    suspend fun exportJson()=store.data.first()[key] ?: "{}"
    suspend fun restoreJson(raw:String) {
        val decoded=read(preferencesOf(key to raw))
        update {decoded.copy(avatar="",background=decoded.background.takeIf {it in listOf("off","builtin:bg1")} ?: "builtin:bg1",lastQueue=it.lastQueue,disclaimerAccepted=it.disclaimerAccepted)}
    }
}
