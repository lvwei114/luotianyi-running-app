package com.luotianyi.running.nativeapp.domain

import kotlin.math.*

/** Live pace requires recent, coherent GPS movement; historical average pace is separate. */
class GpsPaceTracker {
    private data class Fix(val lat:Double,val lon:Double,val accuracy:Double,val speed:Double?,val time:Long)
    private val fixes=ArrayDeque<Fix>()
    private var currentSpeed:Double?=null
    private var lastInput=Long.MIN_VALUE

    fun reset() {fixes.clear();currentSpeed=null;lastInput=Long.MIN_VALUE}

    fun fix(lat:Double,lon:Double,accuracy:Double,speed:Double?,time:Long,now:Long,speedAccuracy:Double?=null) {
        if(time<=lastInput || now-time>FRESH_MS || time>now)return
        if(!lat.isFinite() || !lon.isFinite() || abs(lat)>90 || abs(lon)>180 || !accuracy.isFinite() || accuracy<=0 || accuracy>25) {reset();return}
        if(lastInput!=Long.MIN_VALUE && time-lastInput>=FRESH_MS)reset()
        lastInput=time
        val validSpeed=speed?.takeIf {it.isFinite() && it in 0.0..8.5}
        // A receiver reporting no movement is definitive; do not retain an old running window.
        if(speed!=null && (validSpeed==null || validSpeed<MIN_SPEED ||
            (speedAccuracy!=null && speedAccuracy.isFinite() && speedAccuracy>=0 && validSpeed-speedAccuracy<.35))) {
            fixes.clear();currentSpeed=null;return
        }
        val point=Fix(lat,lon,accuracy,validSpeed,time)
        val previous=fixes.lastOrNull()
        if(previous!=null && meters(previous,point)/(time-previous.time)*1000>8.5) {fixes.clear();currentSpeed=null;fixes.addLast(point);return}
        fixes.addLast(point)
        while(fixes.isNotEmpty() && time-fixes.first().time>WINDOW_MS)fixes.removeFirst()
        currentSpeed=null
        if(fixes.size<3)return
        val first=fixes.first();val span=(time-first.time)/1000.0
        if(span<3)return
        val net=meters(first,point)
        val path=fixes.zipWithNext {a,b->meters(a,b)}.sum()
        val movingSegments=fixes.zipWithNext {a,b->meters(a,b)/(b.time-a.time)*1000>=MIN_SPEED}.count {it}
        val threshold=(max(first.accuracy,accuracy)*.35).coerceIn(3.0,8.0)
        val derived=net/span
        // Oscillating fixes, small drift, or a stationary latest segment do not confirm motion.
        if(net<threshold || derived<MIN_SPEED || derived>8.5 || movingSegments<2 || path<=0 || net/path<.7 ||
            (previous!=null && meters(previous,point)/(time-previous.time)*1000<.35))return
        val recentSpeeds=fixes.takeLast(3).mapNotNull {it.speed}.sorted()
        currentSpeed=if(validSpeed!=null && recentSpeeds.isNotEmpty())recentSpeeds[recentSpeeds.size/2] else derived
    }

    fun pace(now:Long):Double?=currentSpeed?.takeIf {now>=lastInput && now-lastInput<FRESH_MS}?.let {1000/it}

    private fun meters(a:Fix,b:Fix):Double {
        val lat=Math.toRadians(b.lat-a.lat);val lon=Math.toRadians(b.lon-a.lon)
        val h=sin(lat/2).pow(2)+cos(Math.toRadians(a.lat))*cos(Math.toRadians(b.lat))*sin(lon/2).pow(2)
        return 6371000*2*asin(sqrt(h.coerceIn(0.0,1.0)))
    }
    companion object {private const val MIN_SPEED=.7;private const val WINDOW_MS=5000L;private const val FRESH_MS=3000L}
}
