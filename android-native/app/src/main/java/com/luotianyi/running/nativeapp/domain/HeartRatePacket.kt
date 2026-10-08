package com.luotianyi.running.nativeapp.domain
data class HeartRatePacket(val bpm:Int,val contact:Boolean?,val energy:Int?,val rrMillis:List<Double>) {
    companion object {
        fun parse(bytes:ByteArray):HeartRatePacket? {
            if(bytes.size<2)return null
            fun unsigned(i:Int)=bytes[i].toInt() and 255
            val flags=unsigned(0);var offset=1
            fun word():Int? {if(offset+1>=bytes.size)return null;val value=unsigned(offset)+(unsigned(offset+1) shl 8);offset+=2;return value}
            val hr=if(flags and 1!=0)word() ?: return null else unsigned(offset++)
            if(hr !in 30..240)return null
            val energy=if(flags and 8!=0)word() ?: return null else null
            val rr=mutableListOf<Double>();if(flags and 16!=0)while(offset+1<bytes.size)rr.add((word() ?: break)*1000.0/1024)
            return HeartRatePacket(hr,if(flags and 4!=0)flags and 2!=0 else null,energy,rr)
        }
    }
}
