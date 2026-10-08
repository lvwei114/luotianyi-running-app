package com.luotianyi.running.nativeapp.audio
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.common.ForwardingPlayer
import com.luotianyi.running.nativeapp.MainActivity
import com.luotianyi.running.nativeapp.R
import com.luotianyi.running.nativeapp.RunningApplication

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MusicPlaybackService:MediaSessionService() {
    private var session:MediaSession?=null
    override fun onCreate() {
        super.onCreate()
        val app=application as RunningApplication
        val open=PendingIntent.getActivity(this,50,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
        val controlled=object:ForwardingPlayer(app.music.player) {
            override fun play()=app.music.play()
            override fun seekToNext()=app.music.next()
            override fun seekToNextMediaItem()=app.music.next()
            override fun seekToPrevious()=app.music.previous()
            override fun seekToPreviousMediaItem()=app.music.previous()
        }
        setMediaNotificationProvider(DefaultMediaNotificationProvider.Builder(this).setNotificationId(51).build())
        session=MediaSession.Builder(this,controlled).setSessionActivity(open).build()
        val manager=getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("music_prepare","音乐播放",NotificationManager.IMPORTANCE_LOW))
        // Satisfy foreground startup immediately; Media3 subsequently supplies the media controls.
        val notification=NotificationCompat.Builder(this,"music_prepare").setSmallIcon(R.drawable.ic_launcher).setContentTitle("洛天依陪跑 · 音乐").setContentIntent(open).build()
        if(Build.VERSION.SDK_INT>=29)startForeground(51,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK) else startForeground(51,notification)
    }
    override fun onGetSession(controllerInfo:MediaSession.ControllerInfo)=session
    override fun onDestroy() {session?.release();session=null;super.onDestroy()}
}
