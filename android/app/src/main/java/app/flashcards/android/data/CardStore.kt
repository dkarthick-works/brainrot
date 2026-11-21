package app.flashcards.android.data

import app.flashcards.android.api.ApiError
import app.flashcards.android.api.Card
import app.flashcards.android.api.CreateResult
import app.flashcards.android.api.Page
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The server refused this question as a duplicate. The screen puts it back in the bar. */
data class RejectedCard(val topicId: String, val question: String)

/** No cards and no error means the first page is still loading. */
data class CardsState(
    val topicId: String? = null,
    val q: String = "",
    val cards: List<Card>? = null,
    val nextCursor: String? = null,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val moreFailed: Boolean = false,
    val error: ApiError? = null,
    val rejected: List<RejectedCard> = emptyList(),
)

/**
 * The current Cards list with optimistic saves. Lives above screens, so a save finishes after
 * navigating away. Reads and saves share one queue, so a page can't race a save.
 */
class CardStore(
    private val fetchPage: suspend (topicId: String?, q: String, after: String?) -> Page,
    private val postCard: suspend (topicId: String?, question: String, answer: String) -> CreateResult,
    private val patchCard: suspend (id: String, question: String, answer: String, topicId: String) -> Card,
    private val removeCard: suspend (id: String) -> Unit,
    private val notice: SaveNotice,
    private val onSettled: () -> Unit,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(CardsState())
    val state: StateFlow<CardsState> = mutableState.asStateFlow()

    private val jobs = SupervisorJob(scope.coroutineContext[Job])
    private val order = Mutex()
    private var loadJob: Job? = null
    private var loadedKey: Pair<String?, String>? = null
    private val leaving = mutableListOf<String>() // saves in flight that take a card off this list
    private var pending = 0
    private var generation = 0
    private var boundUser: String? = null

    fun bindUser(userId: String?) {
        if (userId == boundUser) return
        boundUser = userId
        generation++
        pending = 0
        leaving.clear()
        loadedKey = null
        jobs.cancelChildren()
        mutableState.value = CardsState()
    }

    /** Loads the first page. Results for the same topic stay up until the new ones arrive. */
    fun show(topicId: String?, q: String) {
        loadJob?.cancel()
        mutableState.update { s ->
            val keep = s.topicId == topicId && s.cards != null
            s.copy(
                topicId = topicId,
                q = q,
                cards = if (keep) s.cards else null,
                nextCursor = if (keep) s.nextCursor else null,
                refreshing = true,
                loadingMore = false,
                moreFailed = false,
                error = null,
            )
        }
        val key = topicId to q
        loadJob = scope.launch(jobs) {
            try {
                val page = order.withLock { fetchPage(topicId, q, null) }
                mutableState.update { s ->
                    val unsaved = if (q.isEmpty()) s.cards.orEmpty().filter(::isUnsaved) else emptyList()
                    s.copy(
                        cards = unsaved + page.cards.filter { it.id !in leaving },
                        nextCursor = page.nextCursor,
                        refreshing = false,
                    )
                }
                loadedKey = key
            } catch (e: ApiError) {
                // Results from a different search must not pass for this one
                mutableState.update { s ->
                    if (loadedKey != key) s.copy(cards = null, nextCursor = null, error = e, refreshing = false)
                    else if (s.cards == null) s.copy(error = e, refreshing = false)
                    else s.copy(refreshing = false)
                }
            }
        }
    }

    fun reload() {
        val s = mutableState.value
        show(s.topicId, s.q)
    }

    fun loadMore() {
        val s = mutableState.value
        val cursor = s.nextCursor ?: return
        if (s.loadingMore || s.moreFailed || loadJob?.isActive == true) return
        mutableState.update { it.copy(loadingMore = true) }
        loadJob = scope.launch(jobs) {
            try {
                val page = order.withLock { fetchPage(s.topicId, s.q, cursor) }
                mutableState.update { current ->
                    val have = current.cards.orEmpty().mapTo(HashSet()) { it.id }
                    val fresh = page.cards.filter { it.id !in have && it.id !in leaving }
                    current.copy(
                        cards = current.cards.orEmpty() + fresh,
                        nextCursor = page.nextCursor.takeIf { it != cursor },
                        loadingMore = false,
                    )
                }
            } catch (e: ApiError) {
                mutableState.update { it.copy(loadingMore = false, moreFailed = true) }
            }
        }
    }

    fun retryMore() {
        mutableState.update { it.copy(moreFailed = false) }
        loadMore()
    }

    fun consumeRejected(card: RejectedCard) {
        mutableState.update { it.copy(rejected = it.rejected - card) }
    }

    /** [selectedTopicId] is null on All, so the server files it under the default topic. */
    fun create(selectedTopicId: String?, targetId: String, question: String, answer: String) {
        val now = Instant.now().toString()
        val temp = Card("$TEMP_PREFIX${UUID.randomUUID()}", targetId, question, answer, 2.5, 0, 0, now, null, now, now)
        edit { list, s -> if (s.lists(targetId) && s.q.isEmpty()) listOf(temp) + list else list }
        write(
            leavingId = null,
            onFailure = { e ->
                edit { list, _ -> list.filter { it.id != temp.id } }
                if (e.status == 409) {
                    mutableState.update { it.copy(rejected = it.rejected + RejectedCard(targetId, question)) }
                } else {
                    notice.show { create(selectedTopicId, targetId, question, answer) }
                }
            },
        ) {
            val result = postCard(selectedTopicId, question, answer)
            if (result.skipped.isNotEmpty()) throw ApiError(409, "duplicate")
            val saved = result.created.firstOrNull() ?: throw ApiError(-1, "unexpected response")
            edit { list, s ->
                when {
                    list.any { it.id == temp.id } -> list.map { if (it.id == temp.id) saved else it }
                    s.lists(saved.topicId) && s.q.isEmpty() -> listOf(saved) + list
                    else -> list
                }
            }
        }
    }

    fun update(card: Card, question: String, answer: String, topicId: String) {
        val startKey = mutableState.value.key
        val index = mutableState.value.cards.orEmpty().indexOfFirst { it.id == card.id }
        val stays = mutableState.value.lists(topicId)
        edit { list, _ ->
            if (stays) {
                list.map { if (it.id == card.id) it.copy(question = question, answer = answer, topicId = topicId) else it }
            } else {
                list.filter { it.id != card.id }
            }
        }
        write(
            leavingId = card.id.takeUnless { stays },
            onFailure = {
                restore(card, index, startKey)
                notice.show { update(card, question, answer, topicId) }
            },
        ) {
            val saved = patchCard(card.id, question, answer, topicId)
            edit { list, s ->
                if (s.lists(saved.topicId)) {
                    list.map { if (it.id == saved.id) saved else it }
                } else {
                    list.filter { it.id != saved.id }
                }
            }
        }
    }

    fun delete(card: Card) {
        val startKey = mutableState.value.key
        val index = mutableState.value.cards.orEmpty().indexOfFirst { it.id == card.id }
        edit { list, _ -> list.filter { it.id != card.id } }
        write(
            leavingId = card.id,
            onFailure = {
                restore(card, index, startKey)
                notice.show { delete(card) }
            },
        ) { removeCard(card.id) }
    }

    private val CardsState.key get() = topicId to q

    private fun CardsState.lists(cardTopicId: String) = topicId == null || topicId == cardTopicId

    private fun restore(card: Card, index: Int, startKey: Pair<String?, String>) {
        edit { list, s ->
            when {
                list.any { it.id == card.id } -> list.map { if (it.id == card.id) card else it }
                s.key == startKey && index >= 0 -> list.take(index) + card + list.drop(index)
                else -> list
            }
        }
    }

    private fun edit(change: (List<Card>, CardsState) -> List<Card>) {
        mutableState.update { s -> s.cards?.let { s.copy(cards = change(it, s)) } ?: s }
    }

    private fun write(leavingId: String?, onFailure: (ApiError) -> Unit, request: suspend () -> Unit) {
        leavingId?.let(leaving::add)
        pending++
        val startedIn = generation
        scope.launch(jobs) {
            var failure: ApiError? = null
            try {
                order.withLock { request() }
            } catch (e: ApiError) {
                failure = e
            } finally {
                if (startedIn == generation) {
                    leavingId?.let(leaving::remove)
                    failure?.let(onFailure)
                    if (--pending == 0) onSettled()
                }
            }
        }
    }

    companion object {
        private const val TEMP_PREFIX = "tmp-"

        /** Not saved yet, so it can't be edited or deleted. */
        fun isUnsaved(card: Card) = card.id.startsWith(TEMP_PREFIX)
    }
}
