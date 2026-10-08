package com.luotianyi.running.nativeapp.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workouts ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM heart_rate_samples WHERE workoutId = :id ORDER BY elapsedMillis")
    suspend fun heartRates(id: String): List<HeartRateSampleEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(workout: WorkoutEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSamples(samples: List<HeartRateSampleEntity>)

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface MusicDao {
    @Query("SELECT * FROM songs ORDER BY rowid") fun observeSongs(): Flow<List<SongEntity>>
    @Query("SELECT * FROM songs ORDER BY rowid") suspend fun songs(): List<SongEntity>
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC") fun observePlaylists(): Flow<List<PlaylistEntity>>
    @Query("SELECT songs.* FROM songs INNER JOIN playlist_songs ON songs.id = playlist_songs.songId WHERE playlist_songs.playlistId = :id ORDER BY playlist_songs.position") suspend fun playlistSongs(id: String): List<SongEntity>
    @Upsert suspend fun upsertSong(song: SongEntity)
    @Insert suspend fun insertPlaylist(playlist: PlaylistEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertLinks(links: List<PlaylistSongEntity>)
    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_songs WHERE playlistId = :id") suspend fun lastPosition(id: String): Int
    @Query("UPDATE songs SET liked = NOT liked WHERE id = :id") suspend fun toggleLike(id: String)
    @Query("UPDATE songs SET removedFromQueue = :removed WHERE id = :id") suspend fun setRemoved(id: String, removed: Boolean)
    @Query("UPDATE songs SET bpm = :bpm, bpmTried = 1 WHERE id = :id") suspend fun setBpm(id: String, bpm: Int?)
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC") suspend fun playlists(): List<PlaylistEntity>
    @Query("DELETE FROM playlists WHERE id = :id") suspend fun deletePlaylist(id: String)
    @Query("UPDATE playlists SET name = :name WHERE id = :id") suspend fun renamePlaylist(id: String, name: String)
    @Query("SELECT COUNT(*) FROM songs")
    fun observeSongCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM playlists")
    fun observePlaylistCount(): Flow<Int>
}

@Database(
    entities = [WorkoutEntity::class, HeartRateSampleEntity::class, SongEntity::class, PlaylistEntity::class, PlaylistSongEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workouts(): WorkoutDao
    abstract fun music(): MusicDao
}
