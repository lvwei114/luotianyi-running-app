package com.luotianyi.running.nativeapp.audio
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.AudioFormat
import com.luotianyi.running.nativeapp.domain.BpmEstimator
import java.nio.ByteOrder
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

object BpmAnalyzer {
    suspend fun analyze(path:String):Int? {
        val extractor=MediaExtractor();var codec:MediaCodec?=null
        try {
            extractor.setDataSource(path)
            val index=(0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/")==true } ?: return null
            val format=extractor.getTrackFormat(index);extractor.selectTrack(index)
            codec=MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(format,null,null,0);codec.start()
            var sampleRate=format.getInteger(MediaFormat.KEY_SAMPLE_RATE);var channels=format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);var encoding=AudioFormat.ENCODING_PCM_16BIT
            val energy=ArrayList<Double>(3000);var sum=0.0;var count=0;var inputEnded=false;val info=MediaCodec.BufferInfo()
            val deadline=System.nanoTime()+12_000_000_000L
            while(energy.size<3000 && System.nanoTime()<deadline) {
                currentCoroutineContext().ensureActive()
                if(!inputEnded) {
                    val slot=codec.dequeueInputBuffer(1000)
                    if(slot>=0) { val buffer=codec.getInputBuffer(slot)!!;val size=extractor.readSampleData(buffer,0)
                        if(size<0) {codec.queueInputBuffer(slot,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputEnded=true}
                        else {codec.queueInputBuffer(slot,0,size,extractor.sampleTime,0);extractor.advance()}
                    }
                }
                val slot=codec.dequeueOutputBuffer(info,1000)
                if(slot==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val out=codec.outputFormat;sampleRate=out.getInteger(MediaFormat.KEY_SAMPLE_RATE);channels=out.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    encoding=if(out.containsKey(MediaFormat.KEY_PCM_ENCODING))out.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                    if(encoding !in listOf(AudioFormat.ENCODING_PCM_16BIT,AudioFormat.ENCODING_PCM_FLOAT))return null
                } else if(slot>=0) {
                    val buffer=codec.getOutputBuffer(slot)!!.order(ByteOrder.LITTLE_ENDIAN);buffer.position(info.offset);buffer.limit(info.offset+info.size)
                    val bytes=if(encoding==AudioFormat.ENCODING_PCM_FLOAT)4 else 2
                    while(buffer.remaining()>=bytes*channels && energy.size<3000) {
                        var mono=0.0
                        repeat(channels) {mono+=if(bytes==4)buffer.float.toDouble() else buffer.short/32768.0};mono/=channels
                        sum+=mono*mono;count++
                        if(count>=sampleRate/100) {energy.add(sum/count);sum=0.0;count=0}
                    }
                    codec.releaseOutputBuffer(slot,false)
                    if(info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0)break
                }
            }
            return BpmEstimator.estimate(energy.toDoubleArray())
        } finally {runCatching {codec?.stop();codec?.release()};extractor.release()}
    }
}
