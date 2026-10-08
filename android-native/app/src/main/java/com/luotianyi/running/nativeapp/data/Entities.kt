package com.luotianyi.running.nativeapp.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "workouts", indices = [Index("startedAt")])
data class WorkoutEntity(
    @PrimaryKey val id: String,
    val startedAt: Long,
    val endedAt: Long,
    val mode: String,
    val timedMinutes: Int?,
    val elapsedMillis: Long,
    val gpsMeters: Double,
    val totalSteps: Long,
    val gpsStepMeters: Double,
    val gpsSteps: Long,
)

@Entity(
    tableName = "heart_rate_samples", primaryKeys = ["workoutId", "elapsedMillis"],
    foreignKeys = [ForeignKey(entity = WorkoutEntity::class, parentColumns = ["id"], childColumns = ["workoutId"], onDelete = ForeignKey.CASCADE)],
)
data class HeartRateSampleEntity(val workoutId: String, val elapsedMillis: Long, val bpm: Int)

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val localPath: String,
    val durationMillis: Long,
    val bpm: Int?,
    val liked: Boolean = false,
    val removedFromQueue: Boolean = false,
    val bpmTried: Boolean = false,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(@PrimaryKey val id: String, val name: String, val createdAt: Long)

@Entity(
    tableName = "playlist_songs", primaryKeys = ["playlistId", "songId"],
    indices = [Index("songId"), Index(value = ["playlistId", "position"], unique = true)],
    foreignKeys = [
        ForeignKey(entity = PlaylistEntity::class, parentColumns = ["id"], childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = SongEntity::class, parentColumns = ["id"], childColumns = ["songId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class PlaylistSongEntity(val playlistId: String, val songId: String, val position: Int)
