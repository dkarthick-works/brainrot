package app.flashcards.android

import android.app.Application
import app.flashcards.android.api.ApiClient
import app.flashcards.android.api.BrainrotApi
import app.flashcards.android.data.ThemeStore
import app.flashcards.android.api.CardInput
import app.flashcards.android.api.CreateCardsRequest
import app.flashcards.android.api.NameRequest
import app.flashcards.android.api.ReviewRequest
import app.flashcards.android.api.UpdateCardRequest
import app.flashcards.android.api.apiCall
import app.flashcards.android.data.CardStore
import app.flashcards.android.data.KeyStore
import app.flashcards.android.data.ReviewWrites
import app.flashcards.android.data.SaveNotice
import app.flashcards.android.data.TopicStore
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

    val saveNotice = SaveNotice()

    // On the main thread, so saves and the list update in a single order
    val topicStore = TopicStore(
        fetch = { apiCall { api.topics() } },
        postTopic = { name -> apiCall { api.createTopic(NameRequest(name)) } },
        patchTopic = { id, name -> apiCall { api.renameTopic(id, NameRequest(name)) } },
        deleteTopic = { id -> apiCall { api.deleteTopic(id) } },
        notice = saveNotice,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    )

    val keyStore = KeyStore(
        fetch = { apiCall { api.apiKeys() } },
        postKey = { name -> apiCall { api.createKey(NameRequest(name)) } },
        deleteKey = { id -> apiCall { api.revokeKey(id) } },
        notice = saveNotice,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    )

    val cardStore = CardStore(
        fetchPage = { topicId, q, after -> apiCall { api.cards(topicId, q.ifEmpty { null }, after) } },
        postCard = { topicId, question, answer ->
            apiCall { api.createCards(CreateCardsRequest(topicId, listOf(CardInput(question, answer)))) }
        },
        patchCard = { id, question, answer, topicId ->
            apiCall { api.updateCard(id, UpdateCardRequest(question, answer, topicId)) }
        },
        removeCard = { id -> apiCall { api.deleteCard(id) } },
        notice = saveNotice,
        onSettled = topicStore::refresh, // card counts changed
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    )
}
