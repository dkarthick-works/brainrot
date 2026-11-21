package app.flashcards.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.flashcards.android.AppContainer
import app.flashcards.android.BuildConfig
import app.flashcards.android.ui.components.BrainError
import app.flashcards.android.ui.components.FullLoader
import app.flashcards.android.ui.theme.Brainrot
import app.flashcards.android.ui.theme.toClerkTheme
import com.clerk.api.Clerk
import com.clerk.ui.auth.AuthView

@Composable
fun AuthGate(container: AppContainer) {
    val initialized by Clerk.isInitialized.collectAsState()
    val initError by Clerk.initializationError.collectAsState()
    val user by Clerk.userFlow.collectAsState()
    val context = LocalContext.current
    val colors = Brainrot.colors

    LaunchedEffect(initialized, user?.id) {
        if (initialized) {
            container.reviewWrites.bindUser(user?.id)
            container.topicStore.bindUser(user?.id)
            container.cardStore.bindUser(user?.id)
        }
    }

    Box(Modifier.fillMaxSize().background(colors.bg), contentAlignment = Alignment.Center) {
        when {
            initError != null && !initialized -> BrainError("Couldn't reach brainrot. Check your connection.") {
                Clerk.initialize(context.applicationContext, publishableKey = BuildConfig.CLERK_PUBLISHABLE_KEY)
            }
            !initialized -> FullLoader()
            user == null -> AuthView(
                clerkTheme = colors.toClerkTheme(),
                preferGoogleOneTap = false, // uses Clerk's web OAuth, so no separate Google Android client is needed
                isDismissible = false,
            )
            else -> Shell(container)
        }
    }
}
