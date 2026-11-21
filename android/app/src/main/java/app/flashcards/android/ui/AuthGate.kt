package app.flashcards.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import app.flashcards.android.AppContainer
import app.flashcards.android.BuildConfig
import app.flashcards.android.ui.components.BText
import app.flashcards.android.ui.components.BrainError
import app.flashcards.android.ui.theme.Brainrot
import app.flashcards.android.ui.theme.SpaceGrotesk
import app.flashcards.android.ui.theme.toClerkTheme
import com.clerk.api.Clerk
import com.clerk.ui.auth.AuthView
import kotlinx.coroutines.delay

@Composable
fun AuthGate(container: AppContainer) {
    val initialized by Clerk.isInitialized.collectAsState()
    val initError by Clerk.initializationError.collectAsState()
    val user by Clerk.userFlow.collectAsState()
    val context = LocalContext.current
    val colors = Brainrot.colors

    Box(Modifier.fillMaxSize().background(colors.bg), contentAlignment = Alignment.Center) {
        when {
            initError != null && !initialized -> BrainError("Couldn't reach brainrot. Check your connection.") {
                Clerk.initialize(context.applicationContext, publishableKey = BuildConfig.CLERK_PUBLISHABLE_KEY)
            }
            !initialized -> DelayedLoader()
            user == null -> AuthView(
                clerkTheme = colors.toClerkTheme(),
                preferGoogleOneTap = false, // uses Clerk's web OAuth, so no separate Google Android client is needed
                isDismissible = false,
            )
            else -> Shell(container)
        }
    }
}

@Composable
fun DelayedLoader() {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(300)
        visible = true
    }
    if (visible) {
        BText(
            "Warming up the neurons...",
            size = 24.sp,
            weight = FontWeight.Bold,
            family = SpaceGrotesk,
            align = TextAlign.Center,
        )
    }
}
