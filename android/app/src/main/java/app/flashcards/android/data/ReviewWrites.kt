package app.flashcards.android.data

import app.flashcards.android.api.Card
import app.flashcards.android.api.apiCall
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ReviewStatus { Pending, Succeeded, Failed, Superseded }

data class ReviewSubmission(
    val id: String,
    val sessionId: String,
    val card: Card,
    val quality: Int,
    val attemptId: Int,
    val status: ReviewStatus,
    val result: Card? = null,
)

/**
 * Memory-only review saves, tied to the signed-in user. Retries can duplicate an ambiguously
 * accepted POST because the API has no idempotency key, so a retry is always user-initiated.
 */
class ReviewWrites(
    private val post: suspend (id: String, quality: Int) -> Card,
    private val scope: CoroutineScope,
) {
    private val records = MutableStateFlow<List<ReviewSubmission>>(emptyList())
    val submissions: StateFlow<List<ReviewSubmission>> = records.asStateFlow()

    private val nextId = AtomicInteger()
    private val generation = AtomicInteger()
    private val jobs = SupervisorJob(scope.coroutineContext[Job])
    private var boundUser: String? = null

    fun newSessionId(): String = "home-${nextId.incrementAndGet()}"

    /** Clears saves when the signed-in user changes. Safe to call again for the same user. */
    fun bindUser(userId: String?) {
        if (userId == boundUser) return
        boundUser = userId
        generation.incrementAndGet()
        jobs.cancelChildren()
        records.value = emptyList()
    }

    fun submit(sessionId: String, card: Card, quality: Int): String? {
        if (records.value.any { it.card.id == card.id && it.status == ReviewStatus.Pending }) return null
        val record = ReviewSubmission("review-${nextId.incrementAndGet()}", sessionId, card, quality, 1, ReviewStatus.Pending)
        records.update { it + record }
        send(record)
        return record.id
    }

    fun retryFailed() {
        records.value.filter { it.status == ReviewStatus.Failed }.forEach { retry(it.id) }
    }

    private fun retry(id: String) {
        val current = records.value
        val record = current.firstOrNull { it.id == id } ?: return
        if (record.status != ReviewStatus.Failed) return
        if (current.any { it.card.id == record.card.id && it.status == ReviewStatus.Pending }) return
        val attempt = record.copy(status = ReviewStatus.Pending, attemptId = record.attemptId + 1)
        records.update { list -> list.map { if (it.id == id) attempt else it } }
        send(attempt)
    }

    private fun send(record: ReviewSubmission) {
        val startedIn = generation.get()
        scope.launch(jobs) {
            try {
                val result = apiCall { post(record.card.id, record.quality) }
                if (startedIn != generation.get()) return@launch
                records.update { list ->
                    list.map {
                        when {
                            it.id == record.id -> it.copy(status = ReviewStatus.Succeeded, result = result)
                            it.card.id == record.card.id && it.status == ReviewStatus.Failed ->
                                it.copy(status = ReviewStatus.Superseded)
                            else -> it
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (startedIn != generation.get()) return@launch
                records.update { list -> list.map { if (it.id == record.id) it.copy(status = ReviewStatus.Failed) else it } }
            }
        }
    }
}
