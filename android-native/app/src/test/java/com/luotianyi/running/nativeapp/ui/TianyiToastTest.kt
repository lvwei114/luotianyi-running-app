package com.luotianyi.running.nativeapp.ui

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.luotianyi.running.nativeapp.data.DisplayTier
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="w390dp-h844dp-mdpi")
class TianyiToastTest {
    @get:Rule val compose=createComposeRule()
    @Test fun captionsAppearAtTopWithoutInteractiveControlsAndDisappearAfterSpeech() {
        val text=mutableStateOf("")
        compose.setContent {LuotianyiTheme {Box(Modifier.fillMaxSize()) {
            TianyiToast(text.value,DisplayTier.LOW,Modifier.align(Alignment.TopCenter).padding(top=64.dp))
        }}}
        compose.onNodeWithTag("tianyi-toast").assertDoesNotExist()
        compose.runOnIdle {text.value="加油哦，天依相信你！"}
        compose.onNodeWithText("天依：加油哦，天依相信你！").assertIsDisplayed()
        val bounds=compose.onNodeWithTag("tianyi-toast").fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.top>=64);assertTrue(bounds.bottom<200)
        compose.onNodeWithTag("tianyi-toast").assert(hasNoClickAction())
        compose.runOnIdle {text.value=""}
        compose.onNodeWithTag("tianyi-toast").assertDoesNotExist()
    }
}
