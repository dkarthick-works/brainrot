package app.flashcards.android

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import app.flashcards.android.data.ThemeMode
import app.flashcards.android.ui.AuthGate
import app.flashcards.android.ui.theme.BrainrotTheme
import app.flashcards.android.ui.theme.DarkColors
import app.flashcards.android.ui.theme.LightColors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as BrainrotApp).container
        enableEdgeToEdge()
        setContent {
            val mode by container.themeStore.mode.collectAsState()
            val dark = when (mode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            LaunchedEffect(dark) {
                val bars = if (dark) {
                    SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                } else {
                    SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                window.decorView.setBackgroundColor((if (dark) DarkColors else LightColors).bg.toArgb())
            }
            BrainrotTheme(dark) { AuthGate(container) }
        }
    }
}
