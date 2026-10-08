package com.luotianyi.running.nativeapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.luotianyi.running.nativeapp.ui.AppViewModel
import com.luotianyi.running.nativeapp.ui.LuotianyiApp
import com.luotianyi.running.nativeapp.ui.LuotianyiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val app = application as RunningApplication
        val viewModel = ViewModelProvider(this, AppViewModel.Factory(app))[AppViewModel::class.java]
        setContent { LuotianyiTheme { LuotianyiApp(viewModel) } }
    }
}
