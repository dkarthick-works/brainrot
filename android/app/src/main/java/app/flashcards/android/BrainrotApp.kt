package app.flashcards.android

import android.app.Application
import app.flashcards.android.api.ApiClient
import app.flashcards.android.api.BrainrotApi
import app.flashcards.android.data.ThemeStore
import app.flashcards.android.api.ReviewRequest
import app.flashcards.android.data.ReviewWrites
import com.clerk.api.Clerk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class BrainrotApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        Clerk.initialize(this, publishableKey = BuildConfig.CLERK_PUBLISHABLE_KEY)
        container = AppContainer(this)
    }
}

class AppContainer(app: Application) {
    val themeStore = ThemeStore(app)
    val api: BrainrotApi = ApiClient.create(BuildConfig.API_BASE_URL)

    // Outlives screens and configuration changes, so a review keeps saving after navigating away
    val reviewWrites = ReviewWrites(
        post = { id, quality -> api.review(id, ReviewRequest(quality)) },
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )
}
