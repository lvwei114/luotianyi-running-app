package com.luotianyi.running.nativeapp.domain
import org.junit.Assert.*
import org.junit.Test
class DistanceTrackerTest {
    @Test fun gpsGapsDoNotEstimateOrJoinAcrossMissingSegment() {
        val gps=RunDistanceTracker(1000,false);gps.setRunning(true,1000)
        gps.fix(0.0,0.0,5.0,1.0,1100,1100)
        repeat(5) {gps.step(1200+it*300L)}
        gps.fix(0.0,5.0/111195,5.0,1.0,4000,4000)
        repeat(20) {gps.step(16000+it*300L)}
        assertEquals(5.0,gps.gpsMeters,.02);assertEquals(0.0,gps.estimatedMeters,0.0)
        gps.fix(0.0,100.0/111195,5.0,1.0,23000,23000)
        repeat(5) {gps.step(23100+it*300L)}
        gps.fix(0.0,105.0/111195,5.0,1.0,26000,26000)
        assertEquals(10.0,gps.gpsMeters,.03);assertEquals(10L,gps.gpsSteps)
        gps.setRunning(false,27000);gps.step(28000);assertEquals(30L,gps.steps)
        gps.setRunning(true,30000);gps.fix(0.0,200.0/111195,5.0,1.0,31000,31000);assertEquals(10.0,gps.gpsMeters,.03)
    }
}
