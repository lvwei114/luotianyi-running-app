package com.luotianyi.running.nativeapp.ui
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import android.graphics.Bitmap
import android.graphics.Canvas
import com.luotianyi.running.nativeapp.MainActivity
import com.luotianyi.running.nativeapp.RunningApplication
import com.luotianyi.running.nativeapp.domain.*
import com.luotianyi.running.nativeapp.data.WorkoutEntity
import com.luotianyi.running.nativeapp.data.HeartRateSampleEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.filter
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w390dp-h844dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class NavigationUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test @Config(qualifiers="w360dp-h640dp-mdpi")
    fun acceptedWorkoutOpensSeparateScreenAndCanBeMinimizedWithoutStopping() {
        dismissIntroduction()
        val app=compose.activity.application as RunningApplication
        compose.runOnUiThread {app.runState.value=app.runState.value.copy(sessionId="navigation-session",training=TrainingState(phase=RunPhase.RUNNING,mode=RunMode.TIMED,elapsedMs=60000),gpsMeters=1234.0)}
        compose.waitUntil(15000) {org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();compose.onAllNodesWithTag("run-hero").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("run-hero").assertIsDisplayed()
        compose.onNodeWithText("距离").assertIsDisplayed();compose.onNodeWithText("运动时间").assertIsDisplayed()
        compose.onNodeWithText("实时配速").assertIsDisplayed()
        // There is historical distance and time, but no live motion: never display average pace.
        compose.onNodeWithTag("run-live-pace").onChildren().filter(hasText("--")).assertCountEquals(1)
        val distanceBounds=compose.onNodeWithTag("run-distance").fetchSemanticsNode().boundsInRoot
        val paceBounds=compose.onNodeWithTag("run-live-pace").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue(paceBounds.left>=distanceBounds.right)
        org.junit.Assert.assertEquals(distanceBounds.top,paceBounds.top,1f)
        compose.runOnUiThread {app.runState.value=app.runState.value.copy(pace=400.0,gpsFresh=true)}
        compose.onNodeWithTag("run-live-pace").onChildren().filter(hasText("6'40\"")).assertCountEquals(1)
        capture("workout-moving-small")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("运动控制"))
        capture("workout-controls-small")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag("run-hero"))
        compose.runOnUiThread {app.runState.value=app.runState.value.copy(gpsFresh=false)}
        compose.onNodeWithTag("run-live-pace").onChildren().filter(hasText("--")).assertCountEquals(1)
        compose.runOnUiThread {app.runState.value=app.runState.value.copy(pace=null)}
        compose.onNodeWithTag("run-live-pace").onChildren().filter(hasText("--")).assertCountEquals(1)
        compose.onNode(hasText("首页") and hasClickAction()).assertDoesNotExist()
        capture("workout-small")
        compose.runOnUiThread {app.voice.speak("jiayou","加油哦，天依相信你！")}
        compose.onNodeWithText("天依：加油哦，天依相信你！").assertIsDisplayed()
        capture("workout-toast-small")
        compose.runOnUiThread {app.voice.cancel()}
        compose.onNodeWithText("收起跑步界面").performClick()
        compose.onNodeWithText("返回跑步界面").assertIsDisplayed()
        org.junit.Assert.assertEquals(RunPhase.RUNNING,app.runState.value.training.phase)
        compose.runOnUiThread {app.runState.value=app.runState.value.copy(training=app.runState.value.training.copy(phase=RunPhase.PAUSED))}
        compose.onNodeWithText("返回跑步界面").assertIsDisplayed()
        compose.onNodeWithTag("run-hero").assertDoesNotExist()
        compose.onNode(hasText("记录") and hasClickAction()).performClick()
        compose.onNodeWithText("跑步记录与分析").assertIsDisplayed()
        compose.onNodeWithText("返回跑步界面").performClick()
        compose.onNodeWithTag("run-hero").assertIsDisplayed()
        compose.runOnUiThread {app.runState.value=app.runState.value.copy(training=app.runState.value.training.copy(phase=RunPhase.FINISHED))}
        compose.onNodeWithTag("run-hero").assertDoesNotExist()
        compose.onNodeWithText("跑步记录与分析").assertIsDisplayed()
    }
    @Test fun corruptCheckpointCanBeDiscardedOnlyAfterConfirmation() {
        dismissIntroduction()
        val app=compose.activity.application as RunningApplication
        val checkpoint=File(app.filesDir,"active_workout.json")
        compose.waitUntil(15000) {org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();!app.recovering.value}
        compose.runOnUiThread {checkpoint.writeText("broken-json");org.junit.Assert.assertThrows(org.json.JSONException::class.java) {app.checkpoint.read()};app.retryRecovery()}
        compose.waitUntil(15000) {org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();app.runState.value.recoveryError.isNotEmpty() && !app.recovering.value}
        compose.onNodeWithText("放弃上次记录").performClick()
        compose.onNodeWithText("取消").performClick();org.junit.Assert.assertTrue(checkpoint.exists())
        compose.onNodeWithText("放弃上次记录").performClick();compose.onNodeWithText("确认放弃").performClick()
        compose.waitUntil(15000) {org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();!checkpoint.exists() && app.runState.value.recoveryError.isEmpty() && !app.recovering.value}
        compose.onNode(hasText("开始跑步") and hasClickAction()).assertIsEnabled()
    }
    @Test fun identicalMessagesAreBothDelivered() {
        val app=compose.activity.application as RunningApplication
        val received=mutableListOf<String>()
        val job=CoroutineScope(Dispatchers.Unconfined).launch {app.message.filter {it=="重复错误"}.take(2).collect {received.add(it)}}
        try {
            compose.runOnUiThread {app.postMessage("重复错误");app.postMessage("重复错误")}
            org.junit.Assert.assertEquals(listOf("重复错误","重复错误"),received)
        } finally {job.cancel()}
    }
    @Test fun allFourNativePagesKeepNavigationAndRender() {
        dismissIntroduction()
        val app=compose.activity.application as RunningApplication
        val ended=System.currentTimeMillis()
        val sample=WorkoutEntity("ui-preview",ended-1_200_000,ended,RunMode.TIMED.name,20,1_200_000,3000.0,3200,3000.0,3200)
        runBlocking {app.workoutRepository.save(sample,listOf(HeartRateSampleEntity(sample.id,0,110),HeartRateSampleEntity(sample.id,600000,142),HeartRateSampleEntity(sample.id,1200000,130)))}
        capture("home")
        for(tab in listOf("音乐","记录","设置","首页")) {
            compose.onNode(hasText(tab) and hasClickAction()).performClick()
            compose.waitForIdle()
            for(label in listOf("首页","记录","音乐","设置"))compose.onNode(hasText(label) and hasClickAction()).assertIsDisplayed()
            capture(when(tab) {"音乐"->"music";"记录"->"run";"设置"->"settings";else->"home"})
            if(tab=="记录") {
                compose.onNodeWithText("跑步记录与分析").assertIsDisplayed()
                compose.onNodeWithText("${RunMode.TIMED.label} · 3.00 km").performScrollTo().performClick()
                compose.onNodeWithText("平均步频").assertIsDisplayed()
                capture("record-detail-top",dialog=true)
                compose.onNodeWithText("删除这条记录").performScrollTo()
                capture("record-detail",dialog=true)
                compose.onNodeWithText("关闭").performClick()
            }
            if(tab=="设置") {
                compose.onNode(hasScrollToNodeAction()).performScrollToIndex(5)
                capture("settings-reminders")
                compose.onNode(hasScrollToNodeAction()).performScrollToIndex(6)
                capture("settings-audio")
                compose.onNode(hasScrollToNodeAction()).performScrollToIndex(7)
                capture("settings-about")
            }
        }
    }
    @Test @Config(qualifiers="w360dp-h640dp-mdpi")
    fun smallScreenAndFinishedTimedWorkoutKeepLegacyChoicesAvailable() {
        dismissIntroduction()
        compose.runOnUiThread {
            val app=compose.activity.application as RunningApplication
            app.runState.value=app.runState.value.copy(training=TrainingState(phase=RunPhase.FINISHED,mode=RunMode.TIMED))
        }
        compose.waitForIdle()
        compose.onNode(hasText("跟天依跑") and hasClickAction()).assertIsEnabled()
        compose.onNode(hasText("天依跟你跑") and hasClickAction()).assertIsEnabled()
        compose.onNode(hasText("自定义 BPM") and hasClickAction()).assertIsEnabled()
        capture("home-small")
        for(tab in listOf("音乐","记录","设置")) {
            compose.onNode(hasText(tab) and hasClickAction()).performClick();compose.waitForIdle()
            for(label in listOf("首页","记录","音乐","设置"))compose.onNode(hasText(label) and hasClickAction()).assertIsDisplayed()
            capture(when(tab) {"音乐"->"music-small";"记录"->"run-small";else->"settings-small"})
        }
    }
    private fun dismissIntroduction() {
        compose.waitUntil(15000) {
            (compose.activity.application as RunningApplication).latestSettings.disclaimerAccepted || compose.onAllNodesWithText("我已了解").fetchSemanticsNodes().isNotEmpty()
        }
        if(compose.onAllNodesWithText("我已了解").fetchSemanticsNodes().isNotEmpty())compose.onNodeWithText("我已了解").performClick()
        compose.waitUntil(15000) {compose.onAllNodesWithText("我已了解").fetchSemanticsNodes().isEmpty()}
        compose.waitForIdle()
    }
    @Test fun voiceLibraryDisablesAuditionDuringTrainingAndCaptionsRemainVisibleInDialogs() {
        dismissIntroduction()
        val app=compose.activity.application as RunningApplication
        org.robolectric.shadows.ShadowMediaPlayer.setMediaInfoProvider {org.robolectric.shadows.ShadowMediaPlayer.MediaInfo(5000,0)}
        try {
            compose.runOnUiThread {app.runState.value=app.runState.value.copy(sessionId="voice-library-session",training=TrainingState(phase=RunPhase.RUNNING,mode=RunMode.CUSTOM))}
            compose.waitUntil(15000) {compose.onAllNodesWithText("收起跑步界面").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("收起跑步界面").performClick()
            compose.onNode(hasText("设置") and hasClickAction()).performClick()
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("天依语音（59 条）"))
            compose.onNodeWithText("随机鼓励与聊天频率").assertExists()
            compose.onNodeWithText("天依语音（59 条）").performClick()
            compose.onAllNodesWithText("试听").assertCountEquals(59)
            compose.onAllNodesWithText("试听")[0].assertIsNotEnabled()
            compose.runOnUiThread {app.runState.value=app.runState.value.copy(training=TrainingState(phase=RunPhase.FINISHED))}
            compose.onAllNodesWithText("试听")[0].assertIsEnabled().performClick()
            val caption="天依："+VoiceCatalog.get("zhunbei").text
            compose.waitUntil(15000) {org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();app.voice.playingId.value=="zhunbei"}
            compose.onNode(hasText(caption) and hasAnyAncestor(isDialog())).assertIsDisplayed()
            capture("voice-library",dialog=true)
            compose.onNode(hasText("关闭") and hasAnyAncestor(isDialog())).performClick()
            org.junit.Assert.assertFalse(app.voice.isBusy)
            val ended=System.currentTimeMillis()
            compose.runOnUiThread {
                app.runState.value=app.runState.value.copy(completed=WorkoutEntity("finish-voice",ended-600_000,ended,RunMode.CUSTOM.name,null,600_000,1000.0,1200,1000.0,1200))
                app.voice.speak(VoiceCatalog.get("jieshu"))
            }
            compose.waitUntil(15000) {org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();app.voice.playingId.value=="jieshu" && compose.onAllNodesWithText("本次训练完成").fetchSemanticsNodes().isNotEmpty()}
            compose.onNode(hasText("天依："+VoiceCatalog.get("jieshu").text) and hasAnyAncestor(isDialog())).assertIsDisplayed()
            capture("workout-finish-voice",dialog=true)
        } finally {compose.runOnUiThread {app.voice.cancel()};org.robolectric.shadows.ShadowMediaPlayer.resetStaticState()}
    }
    private fun capture(name:String,dialog:Boolean=false) {
        val file=File("build/qa/native-$name.png");file.parentFile?.mkdirs()
        file.outputStream().use {output ->
            // Robolectric has no compositor for PixelCopy; draw the actual native view tree.
            compose.runOnUiThread {
                val view=if(dialog)android.view.inspector.WindowInspector.getGlobalWindowViews().last {it.width>0 && it!==compose.activity.window.decorView} else compose.activity.window.decorView
                val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                bitmap.compress(Bitmap.CompressFormat.PNG,100,output)
                bitmap.recycle()
            }
        }
    }
}
