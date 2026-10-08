package com.luotianyi.running.nativeapp.service
import com.luotianyi.running.nativeapp.data.HeartRateSampleEntity
import com.luotianyi.running.nativeapp.data.WorkoutEntity
import com.luotianyi.running.nativeapp.domain.TrainingState
data class RunState(
    val sessionId:String="",
    val recoveryError:String="",val playedPlaylistSaved:Boolean=false,
    val training:TrainingState=TrainingState(),val gpsMeters:Double=0.0,val steps:Long=0,val accuracy:Double?=null,
    val pace:Double?=null,val gpsFresh:Boolean=false,val liveEstimatedMeters:Double=0.0,
    val gpsStepMeters:Double=0.0,val gpsSteps:Long=0,val heartRate:Int?=null,val status:String="",val alert:String="",
    val completed:WorkoutEntity?=null,val samples:List<HeartRateSampleEntity> = emptyList(),val saveError:String="",val saving:Boolean=false,
)
