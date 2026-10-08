package com.luotianyi.running.nativeapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutMetricsTest {
    @Test fun cadenceAndStrideUseDifferentStepPopulations() {
        val metrics = WorkoutMetrics.measured(300_000, 900, GpsRecordChannel(1000.0, 960.0, 800))
        assertEquals(300.0, metrics.averagePaceSecondsPerKm!!, 0.0001)
        assertEquals(180.0, metrics.averageCadence!!, 0.0001)
        assertEquals(1.2, metrics.averageStrideMeters!!, 0.0001)
        assertEquals(1000.0, metrics.gpsMeters, 0.0001)
    }

    @Test fun stepsCannotCreateDistanceWithoutGps() {
        val metrics = WorkoutMetrics.measured(60_000, 120, GpsRecordChannel())
        assertEquals(0.0, metrics.gpsMeters, 0.0)
        assertEquals(120.0, metrics.averageCadence!!, 0.0001)
        assertNull(metrics.averageStrideMeters)
        assertNull(metrics.averagePaceSecondsPerKm)
    }

    @Test fun gpsWithoutPairedStepsCannotCreateStride() {
        val metrics = WorkoutMetrics.measured(60_000, 120, GpsRecordChannel(200.0))
        assertNull(metrics.averageStrideMeters)
        assertEquals(300.0, metrics.averagePaceSecondsPerKm!!, 0.0001)
    }

    @Test fun invalidMeasurementsDoNotProduceInventedValues() {
        val metrics = WorkoutMetrics.measured(-1, -1, GpsRecordChannel(Double.NaN, Double.POSITIVE_INFINITY, 200))
        assertEquals(0.0, metrics.gpsMeters, 0.0)
        assertEquals(0L, metrics.gpsSteps)
        assertNull(metrics.averageCadence)
        assertNull(metrics.averageStrideMeters)
    }
}
