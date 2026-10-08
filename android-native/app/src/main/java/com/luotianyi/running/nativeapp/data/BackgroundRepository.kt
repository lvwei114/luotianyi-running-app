package com.luotianyi.running.nativeapp.data
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BackgroundEntry(val id:String,val name:String,val path:String)
class BackgroundRepository(private val context:Context) {
    fun list():List<BackgroundEntry> = listOf(BackgroundEntry("off","纯色",""),BackgroundEntry("builtin:bg1","天依陪跑","asset:backgrounds/bg1.jpg"))+File(context.filesDir,"backgrounds").listFiles().orEmpty().filter {it.extension=="jpg"}.sortedBy {it.lastModified()}.mapIndexed {i,file->BackgroundEntry("custom:${file.name}","自选 ${i+1}",file.absolutePath)}
    suspend fun add(uri:Uri,directory:String="backgrounds"):String=withContext(Dispatchers.IO) {
        require(directory in listOf("backgrounds","avatars"))
        val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
        val boundsStream=context.contentResolver.openInputStream(uri) ?: error("图片无法读取")
        // Bounds-only decoding returns null even when the image is valid.
        boundsStream.use {BitmapFactory.decodeStream(it,null,bounds)}
        require(bounds.outWidth>0 && bounds.outHeight>0) {"无法识别图片"}
        var scale=1;while(bounds.outWidth/scale>1600 || bounds.outHeight/scale>1600)scale*=2
        val options=BitmapFactory.Options().apply {inSampleSize=scale}
        val bitmap=context.contentResolver.openInputStream(uri)?.use {BitmapFactory.decodeStream(it,null,options)} ?: error("图片解码失败")
        val orientation=runCatching {context.contentResolver.openInputStream(uri)!!.use {ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION,1)}}.getOrDefault(1)
        val matrix=Matrix().apply {when(orientation) {2->setScale(-1f,1f);3->postRotate(180f);4->setScale(1f,-1f);5->{postRotate(90f);postScale(-1f,1f)};6->postRotate(90f);7->{postRotate(270f);postScale(-1f,1f)};8->postRotate(270f)}}
        val transformed=if(orientation==1)bitmap else Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true)
        val folder=File(context.filesDir,directory).apply {mkdirs()};val file=File(folder,"${UUID.randomUUID()}.jpg")
        try {file.outputStream().use {check(transformed.compress(Bitmap.CompressFormat.JPEG,88,it))};file.absolutePath}
        catch(e:Exception) {file.delete();throw e}
        finally {if(transformed!==bitmap)bitmap.recycle();transformed.recycle()}
    }
    suspend fun delete(entry:BackgroundEntry)=withContext(Dispatchers.IO) {
        require(entry.id.startsWith("custom:"));val file=File(entry.path)
        require(file.canonicalFile.parentFile==File(context.filesDir,"backgrounds").canonicalFile)
        check(file.delete()) {"图片删除失败"}
    }
}
