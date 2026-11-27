package app.flashcards.android.data

import app.flashcards.android.api.ApiError
import app.flashcards.android.api.Page
import app.flashcards.android.api.Topic
import app.flashcards.android.logic.FreestyleState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Loads pages for one pass. Only reads, so it can't affect the review schedule. */
class FreestyleController(
    private val fetchPage: suspend (cursor: String?) -> Page,
    private val fetchTopics: suspend () -> List<Topic>,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(FreestyleState())
    val state: StateFlow<FreestyleState> = mutableState.asStateFlow()

    private val mutableTopicNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val topicNames: StateFlow<Map<String, String>> = mutableTopicNames.asStateFlow()

    private var pageJob: Job? = null

    fun start() {
        loadIfNeeded()
        scope.launch {
            // Topic labels are optional, so a failed lookup just leaves them blank
            try {
                mutableTopicNames.value = fetchTopics().associate { it.id to it.name }
            } catch (_: ApiError) {
            }
        }
    }

    fun advance(cardId: String) {
        mutableState.value = mutableState.value.advanced(cardId)
        loadIfNeeded()
    }

    fun retry() {
        val current = mutableState.value
        if (current.error != null && pageJob?.isActive != true) load(current.failedCursor)
    }

    fun restart() {
        pageJob?.cancel()
        pageJob = null
        mutableState.value = FreestyleState()
        load(null)
    }

    private fun loadIfNeeded() {
        val current = mutableState.value
        if (current.wantsPage && pageJob?.isActive != true) {
            load(if (current.firstPageLoaded) current.nextCursor else null)
        }
    }

    private fun load(cursor: String?) {
        mutableState.value = mutableState.value.loading()
        pageJob = scope.launch {
            try {
                val page = fetchPage(cursor)
                mutableState.value = mutableState.value.accepted(cursor, page)
            } catch (e: ApiError) {
                mutableState.value = mutableState.value.failed(cursor, e)
                return@launch
            }
            // This job is still active here, so run the next check after it finishes
            scope.launch { loadIfNeeded() }
        }
    }
}
