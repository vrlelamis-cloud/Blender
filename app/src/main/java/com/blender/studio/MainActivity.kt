package com.blender.studio

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.blender.studio.ui.HomeScreen
import com.blender.studio.ui.audio.AudioEditorScreen
import com.blender.studio.ui.image.ImageEditorScreen
import com.blender.studio.ui.theme.BColors
import com.blender.studio.ui.theme.BlenderTheme
import com.blender.studio.ui.video.VideoEditorScreen

sealed interface Screen {
    data object Home : Screen
    data class Image(val uri: Uri, val preset: String?, val state: String?, val id: String) : Screen
    data class Video(val uris: List<Uri>, val aspect: String?, val state: String?, val id: String) : Screen
    data class Audio(val uri: Uri, val state: String?, val id: String) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { BlenderTheme { BlenderApp() } }
    }
}

@Composable
fun BlenderApp() {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var tick by remember { mutableIntStateOf(0) }
    val go: (Screen) -> Unit = { screen = it; tick++ }
    BackHandler(enabled = screen !is Screen.Home) { go(Screen.Home) }
    Box(Modifier.fillMaxSize().background(BColors.Bg).systemBarsPadding()) {
        when (val s = screen) {
            Screen.Home -> HomeScreen(tick, go)
            is Screen.Image -> key(s.id) { ImageEditorScreen(s) { go(Screen.Home) } }
            is Screen.Video -> key(s.id) { VideoEditorScreen(s) { go(Screen.Home) } }
            is Screen.Audio -> key(s.id) { AudioEditorScreen(s) { go(Screen.Home) } }
        }
    }
}
