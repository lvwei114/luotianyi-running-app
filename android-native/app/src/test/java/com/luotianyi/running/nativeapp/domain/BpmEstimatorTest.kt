package com.luotianyi.running.nativeapp.domain
import org.junit.Assert.*
import org.junit.Test
class BpmEstimatorTest {
    @Test fun periodicOnsetsAndSilence() {
        for(bpm in listOf(100,120,150,180)) {
            val interval=6000.0/bpm;val data=DoubleArray(3000) {i -> if(i%interval.toInt()==0)1.0 else .01}
            assertTrue(kotlin.math.abs((BpmEstimator.estimate(data) ?: 0)-bpm)<=2)
        };assertNull(BpmEstimator.estimate(DoubleArray(3000)))
    }
}
