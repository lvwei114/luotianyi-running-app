package com.luotianyi.running.nativeapp.domain
import kotlin.math.*
/** Autocorrelation of positive energy changes; bounded search with harmonic support. */
object BpmEstimator {
    fun estimate(energy:DoubleArray,rate:Int=100):Int? {
        if(energy.size<rate*6)return null
        val onset=DoubleArray(energy.size) { i -> if(i==0)0.0 else (energy[i]-energy[i-1]).coerceAtLeast(0.0) }
        val mean=onset.average();val variance=onset.sumOf { (it-mean).pow(2) }
        if(variance<1e-10)return null
        var bestLag=0;var bestScore=0.0
        fun correlation(lag:Int):Double {
            if(lag<=0 || lag>=onset.size)return 0.0
            var score=0.0
            for(i in lag until onset.size)score+=(onset[i]-mean)*(onset[i-lag]-mean)
            return score/variance
        }
        for(lag in (60.0*rate/220).roundToInt()..(60.0*rate/60).roundToInt()) {
            val score=correlation(lag)+.4*correlation(lag*2)+.2*correlation(lag*3)
            if(score>bestScore) {bestScore=score;bestLag=lag}
        }
        return if(bestScore>.08 && bestLag>0)(60.0*rate/bestLag).roundToInt().coerceIn(60,220) else null
    }
}
