package com.luotianyi.running.nativeapp.data

import androidx.room.withTransaction
import com.luotianyi.running.nativeapp.domain.RunMode
import com.luotianyi.running.nativeapp.domain.WorkoutMetrics

class WorkoutRepository(private val database: AppDatabase) {
    val workouts = database.workouts().observeAll()

    suspend fun save(workout: WorkoutEntity, samples: List<HeartRateSampleEntity>) {
        require(workout.id.isNotBlank() && workout.endedAt >= workout.startedAt)
        require(workout.gpsMeters.isFinite() && workout.gpsMeters >= 0)
        require(workout.elapsedMillis >= 0 && workout.totalSteps >= 0)
        require(workout.gpsSteps in 0..workout.totalSteps)
        require(workout.gpsStepMeters.isFinite() && workout.gpsStepMeters in 0.0..workout.gpsMeters)
        require(workout.gpsSteps > 0 || workout.gpsStepMeters == 0.0)
        val mode = RunMode.entries.firstOrNull { it.name == workout.mode }
        require(mode != null)
        require(if (mode == RunMode.TIMED) workout.timedMinutes != null && workout.timedMinutes in 5..60 else workout.timedMinutes == null)
        val clean = samples.filter {
            it.workoutId == workout.id && it.elapsedMillis in 0..workout.elapsedMillis && it.bpm in 30..240
        }.distinctBy { it.elapsedMillis }.sortedBy { it.elapsedMillis }
        database.withTransaction {
            // Retrying the same completed session never replaces it or duplicates its samples.
            if (database.workouts().insert(workout) != -1L) database.workouts().insertSamples(clean)
        }
    }
}

fun WorkoutEntity.metrics() = WorkoutMetrics(gpsMeters, elapsedMillis, totalSteps, gpsStepMeters, gpsSteps)
