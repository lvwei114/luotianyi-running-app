package com.luotianyi.running.nativeapp.ui
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import android.app.Application
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class SliderCommitTest {
    @get:Rule val compose=createComposeRule()
    @Test fun slidersCommitOnceAfterDragCompletes() {
        var commits=0;var value=50
        compose.setContent {LuotianyiTheme {IntegerSlider("音量",50,0..100) {commits++;value=it}}}
        compose.onNode(isFocusable()).performTouchInput {down(center);moveTo(centerRight);moveTo(centerLeft);moveTo(center);assertEquals(0,commits);up()}
        compose.waitForIdle();assertEquals(1,commits);assertTrue(value in 0..100)
    }
}
