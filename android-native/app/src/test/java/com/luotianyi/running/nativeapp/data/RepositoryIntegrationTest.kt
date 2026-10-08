package com.luotianyi.running.nativeapp.data
import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class RepositoryIntegrationTest {
    @Test fun workoutRetryAndCascadeDeletionAreTransactional()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),AppDatabase::class.java).build()
        try {
            val repository=WorkoutRepository(db);val w=WorkoutEntity("session",1000,61000,"CUSTOM",null,60000,200.0,100,200.0,100)
            repository.save(w,listOf(HeartRateSampleEntity(w.id,1000,120),HeartRateSampleEntity(w.id,1000,150),HeartRateSampleEntity(w.id,90000,140)))
            repository.save(w,listOf(HeartRateSampleEntity(w.id,2000,180)))
            assertEquals(1,repository.workouts.first().size);assertEquals(1,db.workouts().heartRates(w.id).size)
            assertEquals(120,db.workouts().heartRates(w.id).single().bpm)
            db.workouts().delete(w.id);assertTrue(repository.workouts.first().isEmpty());assertTrue(db.workouts().heartRates(w.id).isEmpty())
        } finally {db.close()}
    }
    @Test fun settingsPersistTimedChoiceWithoutChangingLegacyMode()=runBlocking {
        val context=RuntimeEnvironment.getApplication();val repository=SettingsRepository(context)
        repository.setLegacyMode(com.luotianyi.running.nativeapp.domain.RunMode.CUSTOM);repository.setTimedMinutes(60)
        repository.update {it.copy(customBpm=185,restingHr=60)}
        val restored=SettingsRepository(context).settings.first()
        assertEquals(60,restored.timedMinutes);assertEquals(185,restored.customBpm);assertEquals(com.luotianyi.running.nativeapp.domain.RunMode.CUSTOM,restored.legacyMode)
        assertTrue(restored.hrPercent(140)>0)
    }
    @Test fun checkpointRoundTripDoesNotAddLostTime() {
        val checkpoint=SessionCheckpoint(RuntimeEnvironment.getApplication());val w=WorkoutEntity("crash",1000,61000,"FOLLOW",null,20000,50.0,40,45.0,30)
        checkpoint.write(w,listOf(HeartRateSampleEntity(w.id,5000,130)));val recovered=checkpoint.read()!!
        assertEquals(20000L,recovered.first.elapsedMillis);assertEquals(50.0,recovered.first.gpsMeters,0.0);assertEquals(130,recovered.second.single().bpm)
        checkpoint.clear();assertNull(checkpoint.read())
    }
    @Test fun playlistSongUpsertDoesNotDestroyAssociations()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),AppDatabase::class.java).build()
        try {
            val song=SongEntity("song","歌曲","歌手","local",100000,170)
            db.music().upsertSong(song);db.music().insertPlaylist(PlaylistEntity("p","歌单",1000));db.music().insertLinks(listOf(PlaylistSongEntity("p",song.id,0)))
            db.music().upsertSong(song.copy(liked=true));assertEquals(1,db.music().playlistSongs("p").size);assertTrue(db.music().playlistSongs("p").single().liked)
            db.music().deletePlaylist("p");assertEquals(1,db.music().songs().size)
        } finally {db.close()}
    }
}
