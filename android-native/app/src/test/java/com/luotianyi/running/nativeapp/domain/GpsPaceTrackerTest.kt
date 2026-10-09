package com.luotianyi.running.nativeapp.domain

import org.junit.Assert.*
import org.junit.Test

class GpsPaceTrackerTest {
    private fun fix(tracker:GpsPaceTracker,second:Int,meters:Double,speed:Double?=2.5,accuracy:Double=5.0) {
        val time=1000+second*1000L
        tracker.fix(0.0,meters/111195,accuracy,speed,time,time)
    }
    @Test fun stationaryPositionJitterDoesNotTrustReportedSpeed() {
        val tracker=GpsPaceTracker()
        repeat(30) {second->fix(tracker,second,listOf(0.0,.8,-.6,.4)[second%4],1.2);assertNull(tracker.pace(1000+second*1000L))}
    }
    @Test fun coherentRunningRequiresWarmupAndExpiresWhenFixesStop() {
        val tracker=GpsPaceTracker()
        repeat(3) {second->fix(tracker,second,second*2.5);assertNull(tracker.pace(1000+second*1000L))}
        fix(tracker,3,7.5);assertEquals(400.0,tracker.pace(4000)!!,.01)
        assertEquals(400.0,tracker.pace(6999)!!,.01);assertNull(tracker.pace(7000))
        fix(tracker,7,100.0);assertNull(tracker.pace(8000))
    }
    @Test fun stoppingClearsOldMovementAndRestartMustConfirmNewMovement() {
        val tracker=GpsPaceTracker()
        repeat(6) {fix(tracker,it,it*2.5)}
        assertNotNull(tracker.pace(6000))
        fix(tracker,6,12.5,0.0);assertNull(tracker.pace(7000))
        repeat(3) {second->fix(tracker,7+second,12.5+second*2.5);assertNull(tracker.pace(8000+second*1000L))}
        fix(tracker,10,20.0);assertNotNull(tracker.pace(11000))
        tracker.reset();assertNull(tracker.pace(11000))
    }
    @Test fun absentReceiverSpeedUsesGpsMovementWithoutStrideEstimation() {
        val tracker=GpsPaceTracker()
        repeat(6) {fix(tracker,it,it*2.0,null)}
        assertEquals(500.0,tracker.pace(6000)!!,.1)
        repeat(7) {fix(tracker,6+it,10.0+listOf(.2,-.2)[it%2],null)}
        assertNull(tracker.pace(13000))
    }
    @Test fun inaccurateFixesAndSpeedUncertaintyDoNotShowPace() {
        val tracker=GpsPaceTracker()
        repeat(6) {fix(tracker,it,it*2.5,accuracy=40.0);assertNull(tracker.pace(1000+it*1000L))}
        repeat(6) {second->val time=10000+second*1000L;tracker.fix(0.0,second*2.5/111195,5.0,1.0,time,time,1.5);assertNull(tracker.pace(time))}
    }
    @Test fun onePositionJumpDoesNotImmediatelyBecomeRunningPace() {
        val tracker=GpsPaceTracker()
        repeat(5) {fix(tracker,it,0.0,1.0)}
        // A single 7m jump over one second is physically possible but not sustained movement.
        fix(tracker,5,7.0,1.0);assertNull(tracker.pace(6000))
        fix(tracker,6,7.0,1.0);assertNull(tracker.pace(7000))
    }
}
