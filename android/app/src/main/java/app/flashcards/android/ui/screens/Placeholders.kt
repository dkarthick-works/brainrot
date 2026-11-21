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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.flashcards.android.BuildConfig
import app.flashcards.android.api.ApiError
import app.flashcards.android.api.BrainrotApi
import app.flashcards.android.api.apiCall
import app.flashcards.android.api.currentToken
import app.flashcards.android.api.jwtAzp
import app.flashcards.android.data.ThemeStore
import app.flashcards.android.ui.DelayedLoader
import app.flashcards.android.ui.components.BText
import app.flashcards.android.ui.components.BrainError
import app.flashcards.android.ui.components.BrainrotIcon
import app.flashcards.android.ui.components.BrainrotIcons
import app.flashcards.android.ui.components.ThemeSegmented
import app.flashcards.android.ui.components.TopBar
import app.flashcards.android.ui.components.plainClick
import app.flashcards.android.ui.theme.Brainrot
import app.flashcards.android.ui.theme.SpaceGrotesk
import com.clerk.api.Clerk
import kotlinx.coroutines.launch

private sealed interface Check {
    data object Loading : Check
    data class Connected(val due: Int) : Check
    data class Failed(val error: ApiError, val azp: String?) : Check
}

/** Phase 1 only: proves the backend accepts the native Clerk token. Phase 2 replaces it with Play. */
@Composable
fun PlayConnectionCheck(api: BrainrotApi) {
    var attempt by remember { mutableIntStateOf(0) }
    var check by remember { mutableStateOf<Check>(Check.Loading) }
    LaunchedEffect(attempt) {
        check = Check.Loading
        check = try {
            Check.Connected(apiCall { api.due(1) }.dueCount)
        } catch (e: ApiError) {
            val azp = if (e.status == 401 || e.status == 403) currentToken()?.let(::jwtAzp) else null
            Check.Failed(e, azp)
        }
    }
    val colors = Brainrot.colors
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val state = check) {
            Check.Loading -> DelayedLoader()
            is Check.Connected -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BText("${state.due} due", size = 32.sp, weight = FontWeight.Bold, family = SpaceGrotesk)
                BText("Connected to brainrot", color = colors.muted)
            }
            is Check.Failed -> BrainError(
                detail = if (state.error.status == 0) {
                    "Couldn't reach brainrot. Check your connection."
                } else {
                    "Something broke on our end. Try again in a moment."
                },
                diagnostic = if (state.error.status == 0) null else "HTTP ${state.error.status} · azp: ${state.azp ?: "none"}",
            ) { attempt++ }
        }
    }
}

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
