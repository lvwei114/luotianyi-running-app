package com.luotianyi.running.nativeapp.domain

/** A record channel carries accepted satellite GPS segments only, never fused/estimated distance. */
data class GpsRecordChannel(
    val meters: Double = 0.0,
    val pairedStepMeters: Double = 0.0,
    val pairedSteps: Long = 0,
)

data class WorkoutMetrics(
    val gpsMeters: Double,
    val elapsedMillis: Long,
    val totalSteps: Long,
    val gpsStepMeters: Double,
    val gpsSteps: Long,
) {
    val averagePaceSecondsPerKm: Double?
        get() = if (gpsMeters > 0 && elapsedMillis > 0) elapsedMillis / gpsMeters else null
    val averageCadence: Double?
        get() = if (totalSteps > 0 && elapsedMillis > 0) totalSteps * 60_000.0 / elapsedMillis else null
    val averageStrideMeters: Double?
        get() = if (gpsSteps > 0 && gpsStepMeters > 0) gpsStepMeters / gpsSteps else null

    companion object {
        fun measured(elapsedMillis: Long, steps: Long, gps: GpsRecordChannel): WorkoutMetrics {
            val meters = gps.meters.takeIf { it.isFinite() && it >= 0 } ?: 0.0
            val totalSteps = steps.coerceAtLeast(0)
            val pairedSteps = gps.pairedSteps.coerceIn(0, totalSteps)
            val pairedMeters = if (pairedSteps > 0) {
                gps.pairedStepMeters.takeIf { it.isFinite() && it >= 0 }?.coerceAtMost(meters) ?: 0.0
            } else 0.0
            return WorkoutMetrics(meters, elapsedMillis.coerceAtLeast(0), totalSteps, pairedMeters, pairedSteps)
        }
    }
}
