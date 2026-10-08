package com.luotianyi.running.nativeapp.data

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.Shadows
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BackgroundRepositoryTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private fun image(width:Int,height:Int,format:Bitmap.CompressFormat):File {
        val file=File(context.cacheDir,"source-image")
        val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.CYAN)
        file.outputStream().use {assertTrue(bitmap.compress(format,100,it))};bitmap.recycle()
        return file
    }
    @Test fun documentProviderImageImportsAndRemainsAfterSourceRemoved()=runBlocking {
        val source=image(2000,1000,Bitmap.CompressFormat.PNG)
        val uri=Uri.parse("content://test.images/picked-image")
        Shadows.shadowOf(context.contentResolver).registerInputStreamSupplier(uri) {source.inputStream()}
        val repository=BackgroundRepository(context)
        val path=repository.add(uri)
        source.delete()
        val decoded=File(path).inputStream().use {BitmapFactory.decodeStream(it)}!!
        assertNotNull(decoded);assertEquals(1000,decoded.width);assertEquals(500,decoded.height);decoded.recycle()
        val entry=repository.list().single {it.path==path}
        assertTrue(entry.id.startsWith("custom:"));repository.delete(entry);assertFalse(File(path).exists())
    }
    @Test fun avatarImportRespectsExifRotation()=runBlocking {
        val source=image(64,96,Bitmap.CompressFormat.JPEG)
        ExifInterface(source).apply {setAttribute(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_ROTATE_90.toString());saveAttributes()}
        val repository=BackgroundRepository(context)
        val path=repository.add(Uri.fromFile(source),"avatars")
        val decoded=File(path).inputStream().use {BitmapFactory.decodeStream(it)}!!
        assertEquals(96,decoded.width);assertEquals(64,decoded.height);decoded.recycle()
        assertEquals(File(context.filesDir,"avatars").canonicalPath,File(path).parentFile!!.canonicalPath)
        assertFalse(repository.list().any {it.path==path})
    }
    @Test fun invalidImageIsRejectedWithoutOrphanBackground()=runBlocking {
        val source=File(context.cacheDir,"invalid").apply {writeText("not an image")}
        val repository=BackgroundRepository(context);val before=repository.list()
        try {repository.add(Uri.fromFile(source));fail("Invalid data must be rejected")} catch(e:IllegalArgumentException) {assertEquals("无法识别图片",e.message)}
        assertEquals(before,repository.list())
    }
}
