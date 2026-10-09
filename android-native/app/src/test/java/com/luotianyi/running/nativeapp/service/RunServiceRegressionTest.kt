package com.luotianyi.running.nativeapp.service

import android.Manifest
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.PlaybackException
import com.luotianyi.running.nativeapp.RunningApplication
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class RunServiceRegressionTest {
    @Test fun livePaceNeedsGpsMovementAndClearsOnStopPauseAndDisabledProvider() {
        val app=RuntimeEnvironment.getApplication() as RunningApplication
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION,"${app.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        app.recovering.value=false;app.runState.value=RunState()
        val controller=Robolectric.buildService(RunService::class.java,Intent(app,RunService::class.java).setAction("START").putExtra("mode","CUSTOM")).create()
        fun command(action:String)=controller.get().onStartCommand(Intent(app,RunService::class.java).setAction(action).putExtra("sessionId",app.runState.value.sessionId),0,2)
        fun fix(meters:Double,speed:Float) {
            org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(1))
            controller.get().onLocationChanged(Location(LocationManager.GPS_PROVIDER).apply {
                latitude=0.0;longitude=meters/111195;accuracy=5f;this.speed=speed
                elapsedRealtimeNanos=SystemClock.elapsedRealtimeNanos();time=System.currentTimeMillis()
            })
        }
        try {
            controller.startCommand(0,1);command("SKIP")
            assertEquals(com.luotianyi.running.nativeapp.domain.RunPhase.RUNNING,app.runState.value.training.phase)
            repeat(6) {fix(if(it%2==0).5 else -.5,1.2f);assertNull(app.runState.value.pace)}
            repeat(7) {fix(it*2.5,2.5f)}
            assertEquals(400.0,app.runState.value.pace!!,.1)
            fix(15.0,0f);assertNull(app.runState.value.pace)
            assertTrue(app.runState.value.gpsMeters>0)
            repeat(7) {fix(15+it*2.5,2.5f)}
            assertNotNull(app.runState.value.pace)
            command("PAUSE");assertNull(app.runState.value.pace)
            command("RESUME");assertNull(app.runState.value.pace)
            repeat(7) {fix(30+it*2.5,2.5f)}
            assertNotNull(app.runState.value.pace)
            controller.get().onProviderDisabled(LocationManager.GPS_PROVIDER);assertNull(app.runState.value.pace)
        } finally {controller.destroy()}
    }
    @Test fun successfulMusicPlaybackClearsPreviousError() {
        val app=RuntimeEnvironment.getApplication() as RunningApplication
        shadowOf(app).grantPermissions("${app.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        val music=app.music
        music.playbackListener.onPlayerError(PlaybackException("failed",null,PlaybackException.ERROR_CODE_IO_UNSPECIFIED))
        assertTrue(music.state.value.error.isNotEmpty())
        music.playbackListener.onIsPlayingChanged(true)
        assertEquals("",music.state.value.error)
        music.playbackListener.onIsPlayingChanged(false)
    }
    @Test fun gpsInitiallyDisabledStillRegistersAndReceivesFixAfterEnabled() {
        val app=RuntimeEnvironment.getApplication() as RunningApplication
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION,"${app.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        app.recovering.value=false;app.runState.value=RunState()
        val manager=app.getSystemService(LocationManager::class.java);val locationShadow=shadowOf(manager)
        locationShadow.setProviderEnabled(LocationManager.GPS_PROVIDER,false)
        val controller=Robolectric.buildService(RunService::class.java,Intent(app,RunService::class.java).setAction("START").putExtra("mode","CUSTOM")).create()
        try {
            controller.startCommand(0,1)
            assertTrue(locationShadow.getLocationUpdateListeners(LocationManager.GPS_PROVIDER).contains(controller.get()))
            locationShadow.setProviderEnabled(LocationManager.GPS_PROVIDER,true)
            val fix=Location(LocationManager.GPS_PROVIDER).apply {latitude=35.0;longitude=139.0;accuracy=5f;elapsedRealtimeNanos=SystemClock.elapsedRealtimeNanos();time=System.currentTimeMillis()}
            locationShadow.simulateLocation(fix);shadowOf(Looper.getMainLooper()).idle()
            assertTrue(app.runState.value.gpsFresh)
            assertEquals(5.0,app.runState.value.accuracy!!,0.0)
        } finally {controller.destroy()}
    }
    @Test fun heartRateWarningHasARealLocalVoiceAsset() {
        val app=RuntimeEnvironment.getApplication() as RunningApplication
        app.assets.openFd("voices/hr_l3_a.wav").use {assertTrue(it.length>44)}
    }
}
