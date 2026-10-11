package com.luotianyi.running.nativeapp.domain

import com.luotianyi.running.nativeapp.data.WorkoutEntity
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class RunStreakTest {
    private val today=LocalDate.of(2026,10,12)
    private fun record(date:LocalDate,id:String=date.toString()):WorkoutEntity {
        val start=date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        return WorkoutEntity(id,start,start+600000,"CUSTOM",null,600000,1000.0,1200,1000.0,1200)
    }
    private fun chain(days:Int,end:LocalDate=today)=List(days) {record(end.minusDays(it.toLong()))}
    @Test fun firstRunAndEveryIconBoundaryUseExpectedState() {
        val empty=RunStreaks.calculate(emptyList(),today,ZoneOffset.UTC)
        assertFalse(empty.hasHistory);assertEquals(0,empty.iconIndex);assertEquals("快去跑步吧！",empty.label)
        for((days,icon) in listOf(1 to 1,3 to 1,4 to 2,7 to 2,8 to 3,15 to 3)) {
            val result=RunStreaks.calculate(chain(days),today,ZoneOffset.UTC)
            assertEquals(days,result.days);assertEquals(icon,result.iconIndex)
        }
    }
    @Test fun yesterdayKeepsStreakUntilWholeDayIsMissedAndNextRunRestartsIt() {
        val records=chain(7,today.minusDays(1))
        assertEquals(7,RunStreaks.calculate(records,today,ZoneOffset.UTC).days)
        val broken=RunStreaks.calculate(records,today.plusDays(1),ZoneOffset.UTC)
        assertEquals(0,broken.days);assertTrue(broken.hasHistory);assertEquals(4,broken.iconIndex);assertNull(broken.milestone)
        val restarted=RunStreaks.calculate(records+record(today.plusDays(1)),today.plusDays(1),ZoneOffset.UTC)
        assertEquals(1,restarted.days);assertEquals(1,restarted.iconIndex)
    }
    @Test fun milestonesCountUniqueDaysAndHaveDifferentKeysAfterRestart() {
        val five=RunStreaks.calculate(chain(5),today,ZoneOffset.UTC).milestone!!
        assertEquals(5,five.days)
        assertEquals(five,RunStreaks.calculate(chain(5)+record(today,"second-run"),today,ZoneOffset.UTC).milestone)
        assertEquals(five,RunStreaks.calculate(chain(5),today.plusDays(1),ZoneOffset.UTC).milestone)
        assertNull(RunStreaks.calculate(chain(6),today,ZoneOffset.UTC).milestone)
        assertEquals(10,RunStreaks.calculate(chain(10),today,ZoneOffset.UTC).milestone!!.days)
        assertNotEquals(five.key,RunStreaks.calculate(chain(5,today.plusDays(7)),today.plusDays(7),ZoneOffset.UTC).milestone!!.key)
    }
    @Test fun localDayIsUsedAndFutureRecordsDoNotStartStreak() {
        val future=RunStreaks.calculate(listOf(record(today.plusDays(1))),today,ZoneOffset.UTC)
        assertFalse(future.hasHistory)
        val instant=java.time.Instant.parse("2026-10-11T17:00:00Z").toEpochMilli()
        val evening=record(today.minusDays(1)).copy(startedAt=instant,endedAt=instant+600000)
        assertEquals(today,RunStreaks.calculate(listOf(evening),today,ZoneId.of("Asia/Shanghai")).lastDay)
    }
}
