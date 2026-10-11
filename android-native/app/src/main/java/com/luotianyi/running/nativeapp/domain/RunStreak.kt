package com.luotianyi.running.nativeapp.domain

import com.luotianyi.running.nativeapp.data.WorkoutEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class StreakMilestone(val key:String,val days:Int)
data class RunStreak(val days:Int,val hasHistory:Boolean,val lastDay:LocalDate?=null) {
    val iconIndex:Int get()=when {
        !hasHistory->0
        days==0->4
        days<=3->1
        days<=7->2
        else->3
    }
    val label:String get()=if(hasHistory)"连续 $days 天" else "快去跑步吧！"
    val milestone:StreakMilestone? get()=if(days>0 && days%5==0 && lastDay!=null)
        StreakMilestone("${lastDay.minusDays(days.toLong()-1)}:$days",days) else null
}

object RunStreaks {
    fun calculate(records:List<WorkoutEntity>,today:LocalDate=LocalDate.now(),zone:ZoneId=ZoneId.systemDefault()):RunStreak {
        val dates=records.map {Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate()}.filter {it<=today}.toSet()
        if(dates.isEmpty())return RunStreak(0,false)
        val lastDay=dates.max()
        var current=if(today in dates)today else today.minusDays(1)
        var days=0
        while(current in dates) {days++;current=current.minusDays(1)}
        return RunStreak(days,true,lastDay)
    }
}
