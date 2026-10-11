package com.luotianyi.running.nativeapp.domain
import com.luotianyi.running.nativeapp.data.WorkoutEntity
import java.time.*
import java.time.temporal.TemporalAdjusters
data class WeekStats(val runs:Int,val meters:Double,val elapsedMs:Long,val cadence:Double?,val stride:Double?,val pace:Double?,val daily:List<Long>,val streak:Int)
data class HistorySummary(val runs:Int,val meters:Double,val elapsedMs:Long,val cadence:Double?,val stride:Double?,val pace:Double?)
object HistoryStats {
    fun summary(records:List<WorkoutEntity>):HistorySummary {
        val meters=records.sumOf {it.gpsMeters};val elapsed=records.sumOf {it.elapsedMillis}
        val steps=records.sumOf {it.totalSteps};val gpsSteps=records.sumOf {it.gpsSteps};val gpsStepMeters=records.sumOf {it.gpsStepMeters}
        return HistorySummary(records.size,meters,elapsed,
            if(elapsed>0 && steps>0)steps*60000.0/elapsed else null,
            if(gpsSteps>0 && gpsStepMeters>0)gpsStepMeters/gpsSteps else null,
            if(meters>0)records.filter {it.gpsMeters>0}.sumOf {it.elapsedMillis}/meters else null)
    }
    fun week(records:List<WorkoutEntity>,date:LocalDate=LocalDate.now(),zone:ZoneId=ZoneId.systemDefault()):WeekStats {
        val monday=date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));val start=monday.atStartOfDay(zone).toInstant().toEpochMilli();val end=monday.plusWeeks(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val selected=records.filter {it.startedAt in start until end};val total=summary(selected)
        val streak=RunStreaks.calculate(records,date,zone).days
        return WeekStats(total.runs,total.meters,total.elapsedMs,total.cadence,total.stride,total.pace,List(7) {i->selected.filter {Instant.ofEpochMilli(it.startedAt).atZone(zone).dayOfWeek.value==i+1}.sumOf {it.elapsedMillis}},streak)
    }
}
