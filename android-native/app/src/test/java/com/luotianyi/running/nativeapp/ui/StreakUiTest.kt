package com.luotianyi.running.nativeapp.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.withTransaction
import com.luotianyi.running.nativeapp.MainActivity
import com.luotianyi.running.nativeapp.RunningApplication
import com.luotianyi.running.nativeapp.data.SettingsRepository
import com.luotianyi.running.nativeapp.data.WorkoutEntity
import com.luotianyi.running.nativeapp.domain.RunStreaks
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w390dp-h844dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class StreakUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private lateinit var app:RunningApplication
    private fun records(days:Int,end:LocalDate=LocalDate.now())=List(days) {index->
        val start=end.minusDays(index.toLong()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        WorkoutEntity("streak-$start",start,start+600000,"CUSTOM",null,600000,1000.0,1200,1000.0,1200)
    }
    private fun seed(records:List<WorkoutEntity>)=runBlocking(Dispatchers.IO) {
        val dao=app.database.workouts()
        val existing=dao.observeAll().first()
        app.database.withTransaction {
            existing.forEach {dao.delete(it.id)}
            records.forEach {dao.insert(it)}
        }
    }
    @Test fun allStatesAndMilestonePreviewAndAcknowledgement() {
        app=compose.activity.application as RunningApplication
        compose.waitUntil(15000) {app.latestSettings.disclaimerAccepted || compose.onAllNodesWithText("我已了解").fetchSemanticsNodes().isNotEmpty()}
        if(compose.onAllNodesWithText("我已了解").fetchSemanticsNodes().isNotEmpty())compose.onNodeWithText("我已了解").performClick()
        compose.waitUntil(15000) {org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();app.latestSettings.disclaimerAccepted && !app.recovering.value}
        for((icon,days) in listOf(0 to 0,1 to 2,2 to 6,3 to 9,4 to 0)) {
            seed(if(icon==4)records(3,LocalDate.now().minusDays(3)) else records(days))
            val label=if(icon==0)"快去跑步吧！" else "连续 $days 天"
            compose.waitUntil(15000) {compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText(label).assertIsDisplayed()
            capture("streak-icon$icon")
        }
        // Completing a new day after a break restores the first streak icon.
        seed(records(3,LocalDate.now().minusDays(3))+records(1))
        compose.waitUntil(15000) {compose.onAllNodesWithText("连续 1 天").fetchSemanticsNodes().isNotEmpty()}
        val five=records(5)
        seed(five)
        compose.waitUntil(15000) {compose.onAllNodesWithText("你已经坚持5天了！天依觉得你好棒！").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("你已经坚持5天了！天依觉得你好棒！").assertIsDisplayed()
        capture("streak-icon5",dialog=true)
        compose.onNodeWithText("继续加油").performClick()
        compose.waitUntil(15000) {compose.onAllNodesWithText("继续加油").fetchSemanticsNodes().isEmpty()}
        val key=RunStreaks.calculate(five).milestone!!.key
        assertTrue(runBlocking(Dispatchers.IO) {SettingsRepository(app).seenStreakMilestones.first().contains(key)})
        seed(five+five.first().copy(id="same-day-second-run"))
        compose.waitForIdle()
        compose.onNodeWithText("继续加油").assertDoesNotExist()
        seed(records(10))
        compose.waitUntil(15000) {compose.onAllNodesWithText("你已经坚持10天了！天依觉得你好棒！").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("继续加油").performClick()
    }
    private fun capture(name:String,dialog:Boolean=false) {
        compose.waitForIdle()
        val file=File("build/qa/native-$name.png");file.parentFile?.mkdirs()
        file.outputStream().use {output->compose.runOnUiThread {
            val view=if(dialog)android.view.inspector.WindowInspector.getGlobalWindowViews().last {it.width>0 && it!==compose.activity.window.decorView} else compose.activity.window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap));bitmap.compress(Bitmap.CompressFormat.PNG,100,output);bitmap.recycle()
        }}
    }
}
