package com.luotianyi.running.nativeapp.domain
import org.junit.Assert.*
import org.junit.Test
class HeartRatePacketTest {
    @Test fun parsesFlagsAndRejectsTruncation() {
        assertEquals(120,HeartRatePacket.parse(byteArrayOf(0,120))!!.bpm)
        val parsed=HeartRatePacket.parse(byteArrayOf(31,180.toByte(),0,12,0,0,4))!!
        assertEquals(180,parsed.bpm);assertEquals(true,parsed.contact);assertEquals(12,parsed.energy);assertEquals(1000.0,parsed.rrMillis.single(),.001)
        assertNull(HeartRatePacket.parse(byteArrayOf(1,120)));assertNull(HeartRatePacket.parse(byteArrayOf(0,10)))
    }
}
