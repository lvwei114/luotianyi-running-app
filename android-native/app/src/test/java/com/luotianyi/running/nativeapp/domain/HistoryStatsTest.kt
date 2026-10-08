package com.luotianyi.running.nativeapp.domain

import com.luotianyi.running.nativeapp.data.WorkoutEntity
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class HistoryStatsTest {
    private fun record(id:String,date:LocalDate,minutes:Int,meters:Double,steps:Long,pairedMeters:Double,pairedSteps:Long)=
        WorkoutEntity(id,date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),0,"CUSTOM",null,minutes*60000L,meters,steps,pairedMeters,pairedSteps)
    @Test fun weeklyAndTotalAnalysisUseWeightedMeasurementsAndGpsOnly() {
        val today=LocalDate.of(2026,10,8)
        val records=listOf(record("old",today.minusWeeks(1),10,1000.0,1000,800.0,800),
            record("gps",today,20,2000.0,2000,1500.0,1000),
            record("no-gps",today.minusDays(1),10,0.0,1200,0.0,0))
        val week=HistoryStats.week(records,today,ZoneOffset.UTC)
        assertEquals(2,week.runs);assertEquals(2000.0,week.meters,0.0)
        assertEquals(3200.0/30,week.cadence!!,.0001);assertEquals(1.5,week.stride!!,.0001)
        assertEquals(600.0,week.pace!!,.0001);assertEquals(2,week.streak)
        assertEquals(30*60000L,week.daily.sum())
        val total=HistoryStats.summary(records)
        assertEquals(3,total.runs);assertEquals(3000.0,total.meters,0.0)
        assertEquals(4200.0/40,total.cadence!!,.0001);assertEquals(2300.0/1800,total.stride!!,.0001)
    }
    @Test fun absentMeasurementsRemainUnknown() {
        val stats=HistoryStats.summary(emptyList())
        assertEquals(0,stats.runs);assertNull(stats.pace);assertNull(stats.cadence);assertNull(stats.stride)
    }
}
