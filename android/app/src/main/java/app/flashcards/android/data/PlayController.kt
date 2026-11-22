package app.flashcards.android.data

import app.flashcards.android.api.ApiError
import app.flashcards.android.api.Card
import app.flashcards.android.api.Due
import app.flashcards.android.api.Topic
import app.flashcards.android.logic.earliest
import app.flashcards.android.logic.shouldPrefetch
import app.flashcards.android.logic.unseen
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlaySession(
    val queue: List<Card>,
    val seen: Set<String>,
    val total: Int,
    val dueCount: Int,
    val nextDueAt: String?,
    val exhausted: Boolean,
)

data class PlayModel(
    val sessionId: String = "",
    val session: PlaySession? = null,
    val fetching: Boolean = false,
    val error: ApiError? = null,
    val topics: List<Topic>? = null,
    val topicsLoading: Boolean = true,
    val topicsError: ApiError? = null,
    val submissions: List<ReviewSubmission> = emptyList(),
)

sealed interface PlayView {
    data object Loading : PlayView
    data class PageError(val error: ApiError) : PlayView
    data class TopicsError(val error: ApiError?) : PlayView
    data object UnsavedReviews : PlayView
    data object BrandNew : PlayView
    data class Done(val nextDueAt: String?, val reviewed: Int, val goodOrEasy: Int) : PlayView
    data class Study(val queue: List<Card>, val counter: String) : PlayView
}

private fun PlaySession?.needsMore() = this == null || (!exhausted && shouldPrefetch(queue.size, dueCount))

fun PlayModel.toView(): PlayView {
    val s = session ?: return error?.let(PlayView::PageError) ?: PlayView.Loading
    val reviewed = submissions
        .filter { it.sessionId == sessionId && (it.status == ReviewStatus.Pending || it.status == ReviewStatus.Succeeded) }
        .map { it.quality }
    if (s.queue.isNotEmpty()) {
        val position = reviewed.size + 1
        return PlayView.Study(s.queue, "$position / ${max(s.total, position + s.queue.size - 1)}")
    }
    return when {
        error != null -> PlayView.PageError(error)
        s.needsMore() || fetching || topicsLoading || submissions.any { it.status == ReviewStatus.Pending } -> PlayView.Loading
        submissions.any { it.status == ReviewStatus.Failed } -> PlayView.UnsavedReviews
        reviewed.isEmpty() && topics == null -> PlayView.TopicsError(topicsError)
        reviewed.isEmpty() && topics.orEmpty().all { it.cardCount == 0 } -> PlayView.BrandNew
        else -> PlayView.Done(s.nextDueAt, reviewed.size, reviewed.count { it >= 4 })
    }
}

/** One visit to the Play screen: the due queue, prefetching, and reconciling background saves. */
class PlayController(
    private val fetchDue: suspend () -> Due,
    private val fetchTopics: suspend () -> List<Topic>,
    private val writes: ReviewWrites,
    private val scope: CoroutineScope,
) {
    private val state = MutableStateFlow(PlayModel())
    val model: StateFlow<PlayModel> = state.asStateFlow()

    private var active = false
    private var collector: Job? = null
    private var fetchJob: Job? = null
    private var topicsJob: Job? = null
    private val handled = HashSet<String>()
    private val foreignSuccesses = HashSet<String>()

    fun enter() {
        if (active) return
        active = true
        handled.clear()
        foreignSuccesses.clear()
        writes.submissions.value
            .filter { it.status == ReviewStatus.Succeeded }
            .mapTo(foreignSuccesses) { "${it.id}:${it.attemptId}" }
        state.value = PlayModel(sessionId = writes.newSessionId(), submissions = writes.submissions.value)
        loadTopics()
        collector = scope.launch { writes.submissions.collect(::onSubmissions) }
    }

    fun leave() {
        active = false
        collector?.cancel()
        fetchJob?.cancel()
        topicsJob?.cancel()
    }

    fun grade(card: Card, quality: Int) {
        val current = state.value
        if (writes.submit(current.sessionId, card, quality) != null) {
            state.update { m -> m.copy(session = m.session?.let { s -> s.copy(queue = s.queue.filter { it.id != card.id }) }) }
            onSubmissions(writes.submissions.value)
        }
    }

    fun retry() {
        if (state.value.submissions.none { it.status == ReviewStatus.Pending }) fetchBatch()
    }

    fun retryTopics() = loadTopics()

    private fun loadTopics() {
        topicsJob?.cancel()
        state.update { it.copy(topicsLoading = true, topicsError = null) }
        topicsJob = scope.launch {
            try {
                val topics = fetchTopics()
                state.update { it.copy(topics = topics, topicsLoading = false) }
            } catch (e: ApiError) {
                state.update { it.copy(topicsError = e, topicsLoading = false) }
            }
        }
    }

    private fun onSubmissions(list: List<ReviewSubmission>) {
        if (!active) return
        val sessionId = state.value.sessionId

        // A retry from an earlier visit changes the schedule, so reload rather than patch this queue
        val foreign = list.filter {
            it.sessionId != sessionId && it.status == ReviewStatus.Succeeded && "${it.id}:${it.attemptId}" !in foreignSuccesses
        }
        foreign.mapTo(foreignSuccesses) { "${it.id}:${it.attemptId}" }
        if (foreign.isNotEmpty() && state.value.session != null) {
            fetchJob?.cancel()
            state.update { it.copy(session = null, fetching = false, error = null) }
        }

        var session = state.value.session
        list.filter { it.sessionId == sessionId }.forEach { r ->
            if (!handled.add("${r.id}:${r.attemptId}:${r.status}")) return@forEach
            session = session?.let { s -> reconcile(s, r) }
        }
        state.update { it.copy(session = session, submissions = list) }
        maybeFetch()
    }

    private fun reconcile(s: PlaySession, r: ReviewSubmission): PlaySession = when (r.status) {
        ReviewStatus.Failed -> if (s.queue.any { it.id == r.card.id }) s else s.copy(queue = s.queue + r.card)
        ReviewStatus.Pending -> s.copy(queue = s.queue.filter { it.id != r.card.id })
        ReviewStatus.Succeeded -> s.copy(
            queue = s.queue.filter { it.id != r.card.id },
            nextDueAt = earliest(s.nextDueAt, r.result?.dueAt),
        )
        ReviewStatus.Superseded -> s
    }

    private fun maybeFetch() {
        val m = state.value
        if (active && m.session.needsMore() && m.error == null && fetchJob?.isActive != true &&
            m.submissions.none { it.status == ReviewStatus.Pending }
        ) {
            fetchBatch()
        }
    }

    private fun fetchBatch() {
        if (fetchJob?.isActive == true) return
        state.update { it.copy(fetching = true, error = null) }
        fetchJob = scope.launch {
            try {
                val due = fetchDue()
                state.update { m -> m.copy(session = merge(m.session, due), fetching = false) }
            } catch (e: ApiError) {
                state.update { it.copy(error = e, fetching = false) }
                return@launch
            }
            // This job is still active here, so run the next check after it finishes
            scope.launch { maybeFetch() }
        }
    }

    private fun merge(prev: PlaySession?, due: Due): PlaySession {
        val fresh = unseen(due.cards, prev?.seen.orEmpty())
        return PlaySession(
            queue = prev?.queue.orEmpty() + fresh,
            seen = prev?.seen.orEmpty() + fresh.map { it.id },
            total = prev?.total ?: due.dueCount,
            dueCount = due.dueCount,
            nextDueAt = earliest(due.nextDueAt, prev?.nextDueAt),
            exhausted = fresh.isEmpty(),
        )
    }
}
