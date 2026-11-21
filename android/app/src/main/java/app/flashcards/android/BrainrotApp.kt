package app.flashcards.android

import android.app.Application
import app.flashcards.android.api.ApiClient
import app.flashcards.android.api.BrainrotApi
import app.flashcards.android.data.ThemeStore
import com.clerk.api.Clerk

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
}
