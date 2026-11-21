package app.flashcards.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.flashcards.android.BuildConfig
import app.flashcards.android.data.ThemeStore
import app.flashcards.android.ui.components.BText
import app.flashcards.android.ui.components.BrainrotIcon
import app.flashcards.android.ui.components.BrainrotIcons
import app.flashcards.android.ui.components.ThemeSegmented
import app.flashcards.android.ui.components.TopBar
import app.flashcards.android.ui.components.plainClick
import app.flashcards.android.ui.theme.Brainrot
import com.clerk.api.Clerk
import kotlinx.coroutines.launch

@Composable
fun ScreenPlaceholder(title: String, note: String) {
    Column(Modifier.fillMaxSize()) {
        TopBar(title)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            BText(note, color = Brainrot.colors.muted)
        }
    }
}

@Composable
fun AgentAccessPlaceholder() = ScreenPlaceholder("Agent access", "Arrives in phase 6")

@Composable
fun SettingsPlaceholder(themeStore: ThemeStore, onAgentAccess: () -> Unit) {
    val colors = Brainrot.colors
    val mode by themeStore.mode.collectAsState()
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(14.dp)
    Column(Modifier.fillMaxSize()) {
        TopBar("Settings")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BText("THEME", color = colors.muted, size = 12.sp, weight = FontWeight.SemiBold)
            ThemeSegmented(mode, themeStore::set)
            Column(Modifier.fillMaxWidth().clip(shape).background(colors.card).border(1.dp, colors.line, shape)) {
                Row(
                    Modifier.fillMaxWidth().plainClick(onClick = onAgentAccess).padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BText("API keys")
                    BrainrotIcon(BrainrotIcons.ChevronRight, colors.muted)
                }
                Box(Modifier.fillMaxWidth().plainClick { scope.launch { Clerk.auth.signOut() } }.padding(16.dp)) {
                    BText("Sign out", color = colors.again)
                }
            }
            BText("brainrot ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", color = colors.muted, size = 12.sp)
        }
    }
}
