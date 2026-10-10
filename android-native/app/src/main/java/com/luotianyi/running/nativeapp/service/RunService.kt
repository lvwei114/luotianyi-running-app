package com.luotianyi.running.nativeapp.service
import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.*
import android.location.*
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.luotianyi.running.nativeapp.*
import com.luotianyi.running.nativeapp.audio.NativeMetronome
import com.luotianyi.running.nativeapp.data.*
import com.luotianyi.running.nativeapp.domain.*
import kotlinx.coroutines.*
import java.util.UUID
import kotlin.math.sqrt

/** Sole owner of workout transitions and measurement. UI never advances workout time. */
class RunService:Service(),LocationListener,SensorEventListener {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val app get()=application as RunningApplication
    private val machine=WorkoutMachine()
    private var gps=RunDistanceTracker(0,false);private var live=RunDistanceTracker(0,true)
    private val livePace=GpsPaceTracker()
    private var locations:LocationManager?=null;private var sensors:SensorManager?=null
    private var stepSensor=false;private var above=false;private var lastStep=0L;private var confirmation=0
    private var wakeLock:PowerManager.WakeLock?=null
    private val beat by lazy {NativeMetronome {error->app.postMessage(error)}}
    private var startedAt=0L;private var id="";private var settings=AppSettings()
    private val samples=mutableListOf<HeartRateSampleEntity>();private var lastHrSample=-5000L;private var lastCheckpoint=0L
    private var preparingAt=0L;private var preparationJob:Job?=null;private var pulseJob:Job?=null;private var checkpointJob:Job?=null
    private var dangerAt=0L
    private val voiceCoach=WorkoutVoiceCoach()
    private var previousMusic=false;private var finalizing=false
    override fun onBind(intent:Intent?):IBinder?=null
    override fun onCreate() {
        super.onCreate();locations=getSystemService(LocationManager::class.java);sensors=getSystemService(SensorManager::class.java)
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("workout","跑步训练",NotificationManager.IMPORTANCE_LOW).apply {setSound(null,null);enableVibration(false)})
        scope.launch {app.music.state.collect {music->machine.updatePlaylistDuration(music.queue.sumOf {it.durationMillis})}}
        scope.launch {app.settingsRepository.settings.collect {s->
            settings=s;machine.updateSettings(s);live.strideLength=s.estimatedStride
            beat.tone=s.beatTone;beat.volume=s.masterVolume/100f*s.beatVolume/100f
        }}
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent==null)return START_NOT_STICKY
        val action=intent.action ?: "START"
        if(action!="START" && intent.getStringExtra("sessionId")!=id)return START_NOT_STICKY
        try {
            when(action) {
                "START" -> {
                    if(machine.state.phase in listOf(RunPhase.PREPARING,RunPhase.RUNNING,RunPhase.PAUSED) || finalizing)return START_NOT_STICKY
                    if(app.recovering.value || app.runState.value.recoveryError.isNotEmpty() || app.runState.value.saveError.isNotEmpty()) {stopSelf();return START_NOT_STICKY}
                    settings=app.latestSettings
                    val mode=RunMode.fromStored(intent.getStringExtra("mode"));id=UUID.randomUUID().toString()
                    val now=SystemClock.elapsedRealtime();gps=RunDistanceTracker(now,false);live=RunDistanceTracker(now,true);live.strideLength=settings.estimatedStride;livePace.reset()
                    samples.clear();lastHrSample=-5000;startedAt=0;finalizing=false;dangerAt=0;lastCheckpoint=0;previousMusic=false
                    machine.prepare(mode,settings,app.music.state.value.queue.sumOf {it.durationMillis});app.runState.value=RunState(sessionId=id,training=machine.state,status="GPS 预热中，可跳过等待")
                    voiceCoach.reset(machine.state,settings);app.voice.cancel()
                    foreground();startMeasurements();preparingAt=now
                    if(settings.hydrationMinutes>0)say(voiceCoach.beforeWater())
                    preparationJob=scope.launch {while(machine.state.phase==RunPhase.PREPARING) {
                        delay(500)
                        if(SystemClock.elapsedRealtime()-preparingAt>=20000 || (gps.accuracy!=null && gps.accuracy<=20 && SystemClock.elapsedRealtime()-preparingAt>3000))begin()
                    }}
                }
                "SKIP" -> begin()
                "PAUSE" -> pause()
                "PAUSE_SILENT" -> pause(false)
                "RESUME" -> resume()
                "TOGGLE" -> if(machine.state.phase==RunPhase.RUNNING)pause() else resume()
                "FINISH" -> finishWorkout()
                "END_HINT" -> if(machine.state.phase==RunPhase.RUNNING &&
                    (app.heartRate.freshBpm()?.takeIf {settings.withWearable}?.let(settings::hrPercent)?.let {it<90} != false))say(voiceCoach.endHint())
                "MODE" -> {machine.changeMode(RunMode.fromStored(intent.getStringExtra("mode")));publish()}
                "MUSIC_TOGGLE" -> app.music.toggle()
                "MUSIC_NEXT" -> app.music.next()
                "MUSIC_PREVIOUS" -> app.music.previous()
            }
        } catch(e:Exception) {
            app.postMessage("运动操作失败：${e.message}")
            if(machine.state.phase==RunPhase.RUNNING)pause()
            else if(machine.state.phase==RunPhase.PREPARING) {cleanup();app.runState.value=RunState(status="运动操作失败：${e.message}");stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()}
        }
        return START_NOT_STICKY
    }
    private fun begin() {
        if(machine.state.phase!=RunPhase.PREPARING)return
        if(!app.audioFocus.acquire()) {app.postMessage("暂时无法播放训练音频");return}
        app.runState.value=app.runState.value.copy(status=if(gps.fresh(SystemClock.elapsedRealtime()))"" else "等待 GPS，缺失路段不会计入记录")
        preparingAt=0;preparationJob?.cancel();startedAt=System.currentTimeMillis()
        val now=SystemClock.elapsedRealtime();machine.start(now);gps.setRunning(true,now);live.setRunning(true,now);livePace.reset();confirmation=0;above=false
        app.audioFocus.workoutActive=true;acquireWakeLock();beat.bpm=machine.state.bpm;beat.volume=settings.masterVolume/100f*settings.beatVolume/100f;beat.tone=settings.beatTone;beat.start()
        app.music.repeatWorkout=machine.state.mode==RunMode.TIMED;app.music.playedIds.clear()
        if(machine.state.mode==RunMode.FOLLOW) {app.music.setMode("smart");scope.launch {app.settingsRepository.update {it.copy(musicMode="smart")}}}
        app.music.play();app.voice.cancel();say(voiceCoach.opening());pulseJob?.cancel()
        pulseJob=scope.launch {while(isActive && machine.state.phase!=RunPhase.FINISHED) {tick();delay(250)}}
        publish()
    }
    private fun pause(announce:Boolean=true) {
        if(machine.state.phase!=RunPhase.RUNNING)return
        val now=SystemClock.elapsedRealtime();machine.pause(now)
        if(machine.state.phase==RunPhase.FINISHED) {finishWorkout();return}
        gps.setRunning(false,now);live.setRunning(false,now);livePace.reset();beat.stop();releaseWakeLock()
        previousMusic=app.music.player.isPlaying;app.music.player.pause();app.audioFocus.workoutActive=false;app.audioFocus.releaseIfIdle()
        app.voice.cancel();if(announce)say(voiceCoach.paused());checkpoint();publish()
    }
    private fun resume() {
        if(machine.state.phase!=RunPhase.PAUSED)return
        if(!app.audioFocus.acquire())return
        val now=SystemClock.elapsedRealtime();machine.resume(now);gps.setRunning(true,now);live.setRunning(true,now);livePace.reset();above=false;confirmation=0;dangerAt=0
        app.audioFocus.workoutActive=true;acquireWakeLock();beat.start();if(previousMusic)app.music.play();app.voice.cancel();say(voiceCoach.resumed(machine.state.elapsedMs));publish()
    }
    private fun tick() {
        val now=SystemClock.elapsedRealtime();val hr=app.heartRate.freshBpm(now).takeIf {settings.withWearable}
        if(machine.state.phase==RunPhase.PAUSED) {
            voiceCoach.recovery(now,settings,hr?.let(settings::hrPercent),app.voice.isBusy)?.let(::say)
            publish();return
        }
        if(machine.state.phase!=RunPhase.RUNNING)return
        machine.tick(now,hr);live.update(now)
        if(machine.state.phase==RunPhase.FINISHED) {finishWorkout();return}
        acquireWakeLock();beat.bpm=machine.state.bpm;app.music.heartRate=hr
        if(hr!=null && machine.state.elapsedMs-lastHrSample>=5000) {samples.add(HeartRateSampleEntity(id,machine.state.elapsedMs,hr));lastHrSample=machine.state.elapsedMs}
        if(settings.safetyEnabled && hr!=null) {
            val percent=settings.hrPercent(hr)
            if(percent>=100) {if(dangerAt==0L)dangerAt=now;if(now-dangerAt>=15000) {
                app.runState.value=app.runState.value.copy(alert="心率持续达到最大心率，已暂停。请休息后手动继续。")
                pause(false);say(voiceCoach.safetyStop(percent,now));return
            }}
            else dangerAt=0
        } else dangerAt=0
        voiceCoach.next(machine.state,settings,hr?.let(settings::hrPercent),app.voice.isBusy,now)?.let {emission->
            if(emission.line.category==VoiceCategory.HR_LOW)app.runState.value=app.runState.value.copy(alert="心率较高，请放慢节奏")
            say(emission)
        }
        if(now-lastCheckpoint>=5000) {lastCheckpoint=now;checkpoint()}
        publish()
        if(now%1000<250 && (Build.VERSION.SDK_INT<33 || ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)) getSystemService(NotificationManager::class.java).notify(42,notification())
    }
    private fun say(emission:CoachEmission)=app.voice.speak(emission.line,emission.priority,emission.lifetimeMs,emission.allowQueue)
    private fun record():WorkoutEntity {
        val metrics=WorkoutMetrics.measured(machine.state.elapsedMs,gps.steps,GpsRecordChannel(gps.gpsMeters,gps.gpsStepMeters,gps.gpsSteps))
        return WorkoutEntity(id,startedAt,System.currentTimeMillis(),machine.state.mode.name,if(machine.state.mode==RunMode.TIMED)(machine.state.totalMs/60000).toInt() else null,metrics.elapsedMillis,metrics.gpsMeters,metrics.totalSteps,metrics.gpsStepMeters,metrics.gpsSteps)
    }
    private fun checkpoint() {
        if(startedAt==0L || finalizing || checkpointJob?.isActive==true)return
        val record=record();val copy=samples.toList()
        checkpointJob=scope.launch {try {withContext(Dispatchers.IO) {app.checkpoint.write(record,copy)}} catch(e:Exception) {app.postMessage("训练备份失败：${e.message}")}}
    }
    private fun finishWorkout() {
        if(finalizing)return
        if(machine.state.phase==RunPhase.PREPARING) {app.voice.cancel();cleanup();app.runState.value=RunState(status="已取消准备");stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();return}
        if(startedAt==0L)return
        finalizing=true;val now=SystemClock.elapsedRealtime();machine.finish(now);gps.setRunning(false,now);live.setRunning(false,now);livePace.reset()
        beat.stop();releaseWakeLock();app.music.player.pause();app.music.repeatWorkout=false;app.audioFocus.workoutActive=false;app.audioFocus.releaseIfIdle();app.voice.cancel();voiceCoach.finish(settings).forEach(::say)
        app.heartRate.freshBpm()?.takeIf {settings.withWearable}?.let {hr->if(samples.lastOrNull()?.elapsedMillis!=machine.state.elapsedMs)samples.add(HeartRateSampleEntity(id,machine.state.elapsedMs,hr))}
        val w=record();val hr=samples.toList();publish();app.runState.value=app.runState.value.copy(completed=w,samples=hr,saving=true)
        scope.launch {
            try {checkpointJob?.join();withContext(Dispatchers.IO) {app.checkpoint.write(w,hr)};app.workoutRepository.save(w,hr);withContext(Dispatchers.IO) {app.checkpoint.clear()};app.runState.value=app.runState.value.copy(saveError="",saving=false)}
            catch(e:Exception) {app.runState.value=app.runState.value.copy(saveError="保存失败：${e.message}",saving=false)}
            finally {cleanup();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()}
        }
    }
    private fun publish() {
        val now=SystemClock.elapsedRealtime();app.runState.value=app.runState.value.copy(sessionId=id,training=machine.state,gpsMeters=gps.gpsMeters,steps=gps.steps,accuracy=gps.accuracy,gpsFresh=gps.fresh(now),liveEstimatedMeters=live.estimatedMeters,gpsStepMeters=gps.gpsStepMeters,gpsSteps=gps.gpsSteps,heartRate=app.heartRate.freshBpm().takeIf {settings.withWearable},pace=if(machine.state.phase==RunPhase.RUNNING && gps.fresh(now))livePace.pace(now) else null)
    }
    @SuppressLint("MissingPermission") private fun startMeasurements() {
        val fine=ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
        if(fine && locations?.allProviders?.contains(LocationManager.GPS_PROVIDER)==true) {
            // Register even while GPS is switched off so enabling it later delivers fixes.
            locations?.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000,0f,this,Looper.getMainLooper())
            if(locations?.isProviderEnabled(LocationManager.GPS_PROVIDER)!=true)app.runState.value=app.runState.value.copy(status="定位关闭，打开后将自动开始 GPS 记录")
        } else app.runState.value=app.runState.value.copy(status="GPS 不可用，仍可计时计步；记录距离不会补算")
        val permitted=Build.VERSION.SDK_INT<29 || ContextCompat.checkSelfPermission(this,Manifest.permission.ACTIVITY_RECOGNITION)==PackageManager.PERMISSION_GRANTED
        val detector=if(permitted)sensors?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) else null
        stepSensor=detector!=null;val sensor=detector ?: sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if(sensor!=null)sensors?.registerListener(this,sensor,SensorManager.SENSOR_DELAY_GAME) else app.runState.value=app.runState.value.copy(status="设备没有可用计步传感器")
    }
    override fun onLocationChanged(location:Location) {
        if(location.provider!=LocationManager.GPS_PROVIDER)return
        val now=SystemClock.elapsedRealtime();if(machine.state.phase==RunPhase.RUNNING) {machine.tick(now,app.heartRate.freshBpm(),false);if(machine.state.phase==RunPhase.FINISHED) {finishWorkout();return}}
        val time=location.elapsedRealtimeNanos/1_000_000;val accuracy=if(location.hasAccuracy())location.accuracy.toDouble() else Double.NaN;val speed=if(location.hasSpeed())location.speed.toDouble() else null
        val valid=gps.fix(location.latitude,location.longitude,accuracy,speed,time,now);live.fix(location.latitude,location.longitude,accuracy,speed,time,now)
        if(valid) {
            app.runState.value=app.runState.value.copy(status="")
            if(machine.state.phase==RunPhase.RUNNING)livePace.fix(location.latitude,location.longitude,accuracy,speed,time,now,if(location.hasSpeedAccuracy())location.speedAccuracyMetersPerSecond.toDouble() else null)
        }
        publish()
    }
    override fun onProviderDisabled(provider:String) {if(provider==LocationManager.GPS_PROVIDER)livePace.reset();app.runState.value=app.runState.value.copy(status="定位关闭，记录距离不会补算");publish()}
    override fun onProviderEnabled(provider:String) {app.runState.value=app.runState.value.copy(status="等待 GPS 信号")}
    @Deprecated("Platform API") override fun onStatusChanged(provider:String?,status:Int,extras:Bundle?) {}
    override fun onSensorChanged(event:SensorEvent) {
        if(machine.state.phase!=RunPhase.RUNNING)return
        val now=SystemClock.elapsedRealtime();machine.tick(now,app.heartRate.freshBpm(),false);if(machine.state.phase==RunPhase.FINISHED) {finishWorkout();return}
        if(stepSensor) {gps.step(now);live.step(now);return}
        val v=event.values;val magnitude=sqrt((v[0]*v[0]+v[1]*v[1]+v[2]*v[2]).toDouble())-9.8
        if(magnitude>1.5 && !above) {above=true;if(now-lastStep>2000)confirmation=0;if(now-lastStep>=250) {lastStep=now;if(confirmation<3)confirmation++ else {gps.step(now);live.step(now)}}} else if(magnitude<1)above=false
    }
    override fun onAccuracyChanged(sensor:Sensor?,accuracy:Int) {}
    private fun acquireWakeLock() {if(wakeLock==null)wakeLock=getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"${packageName}:workout");if(wakeLock?.isHeld!=true)wakeLock?.acquire(3_600_000)}
    private fun releaseWakeLock() {if(wakeLock?.isHeld==true)wakeLock?.release()}
    private fun foreground() {
        if(Build.VERSION.SDK_INT>=29) {
            var type=ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)type=type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            startForeground(42,notification(),type)
        } else startForeground(42,notification())
    }
    private fun notification():Notification {
        val state=machine.state;val content=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
        fun action(name:String)=PendingIntent.getService(this,name.hashCode(),Intent(this,RunService::class.java).setAction(name).putExtra("sessionId",id),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this,"workout").setSmallIcon(R.drawable.ic_launcher).setContentTitle("洛天依陪跑 · ${if(state.phase==RunPhase.PAUSED)"已暂停" else state.stageName}")
            .setContentText("${state.elapsedMs/60000}:${(state.elapsedMs/1000%60).toString().padStart(2,'0')} · 距离 %.2f km · %d BPM".format(gps.gpsMeters/1000,state.bpm))
            .setStyle(NotificationCompat.InboxStyle().addLine("步数 ${gps.steps} · 心率 ${app.heartRate.freshBpm() ?: "--"}").addLine(app.music.state.value.queue.getOrNull(app.music.state.value.index)?.title ?: "未播放音乐"))
            .setContentIntent(content).setOnlyAlertOnce(true).setOngoing(true).setSilent(true)
            .addAction(0,"上一首",action("MUSIC_PREVIOUS")).addAction(0,"播放/暂停",action("MUSIC_TOGGLE")).addAction(0,"下一首",action("MUSIC_NEXT"))
            .addAction(0,when(state.phase) {RunPhase.PREPARING->"跳过等待";RunPhase.PAUSED->"继续运动";else->"暂停运动"},action(if(state.phase==RunPhase.PREPARING)"SKIP" else "TOGGLE")).addAction(0,"结束运动",action("FINISH")).build()
    }
    private fun cleanup() {preparationJob?.cancel();pulseJob?.cancel();beat.stop();releaseWakeLock();runCatching {locations?.removeUpdates(this)};sensors?.unregisterListener(this);app.audioFocus.workoutActive=false;app.audioFocus.releaseIfIdle()}
    override fun onDestroy() {cleanup();scope.cancel();super.onDestroy()}
    companion object {
        fun command(context:Context,action:String,mode:RunMode?=null) {
            val app=context.applicationContext as RunningApplication
            val intent=Intent(context,RunService::class.java).setAction(action).putExtra("sessionId",app.runState.value.sessionId).putExtra("mode",mode?.name)
            if(action=="START")ContextCompat.startForegroundService(context,intent) else context.startService(intent)
        }
    }
}
