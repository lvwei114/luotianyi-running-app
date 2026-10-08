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
