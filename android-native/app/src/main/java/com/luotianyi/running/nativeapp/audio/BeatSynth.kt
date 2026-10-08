package com.luotianyi.running.nativeapp.audio
import kotlin.math.*
import kotlin.random.Random

/** PCM sample clock drives beat placement, independently of UI and Handler timing. */
class BeatSynth(private val rate:Int=44100) {
    var bpm=170.0;var volume=.4;var wood=true
    private var phase=1.0;private var age=rate;private var beat=0;private var accent=true
    private val random=Random(53)
    fun render(output:ShortArray) {
        for(i in output.indices) {
            if(phase>=1) { phase-=1;age=0;accent=beat++%4==0 }
            val t=age.toDouble()/rate
            val signal=if(age<rate*.09) {
                if(wood) random.nextDouble(-1.0,1.0)*exp(-t*170)*.45+sin(2*PI*(if(accent)920 else 700)*t)*exp(-t*75)*.55
                else (if(sin(2*PI*(if(accent)1600 else 1200)*t)>=0)1.0 else -1.0)*exp(-t*85)*.65
            } else 0.0
            output[i]=(signal*volume.coerceIn(0.0,1.0)*(if(accent)1.0 else .72)*Short.MAX_VALUE).coerceIn(-32767.0,32767.0).toInt().toShort()
            phase+=bpm.coerceIn(30.0,300.0)/(60*rate);age++
        }
    }
}
