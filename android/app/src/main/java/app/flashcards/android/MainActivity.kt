package app.flashcards.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark = isSystemInDarkTheme()
            Box(
                Modifier.fillMaxSize().background(if (dark) Color(0xFF15140F) else Color(0xFFF4F1EA)),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    "brainrot.",
                    style = TextStyle(
                        color = if (dark) Color(0xFFEFEBE2) else Color(0xFF1D1B18),
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }
}
