package app.flashcards.android.data

import app.flashcards.android.api.ApiError
import app.flashcards.android.api.Topic
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

/** No topics and no error means the first load is still running. */
data class TopicsState(val topics: List<Topic>? = null, val error: ApiError? = null)

/**
 * Newest-first topics with optimistic saves. Lives above screens, so a save finishes after
 * navigating away. Saves run in order, and the list reloads once they have all settled.
 */
class TopicStore(
    private val fetch: suspend () -> List<Topic>,
    private val postTopic: suspend (name: String) -> Topic,
    private val patchTopic: suspend (id: String, name: String) -> Topic,
    private val deleteTopic: suspend (id: String) -> Unit,
    private val notice: SaveNotice,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(TopicsState())
    val state: StateFlow<TopicsState> = mutableState.asStateFlow()

    private val jobs = SupervisorJob(scope.coroutineContext[Job])
    private val order = Mutex()
    private var refreshJob: Job? = null
    private var pending = 0
    private var generation = 0
    private var boundUser: String? = null

    fun bindUser(userId: String?) {
        if (userId == boundUser) return
        boundUser = userId
        generation++
        pending = 0
        jobs.cancelChildren()
        notice.clear()
        mutableState.value = TopicsState()
    }

    fun refresh() {
        if (pending > 0) return // the reload after the last save covers this
        refreshJob?.cancel()
        mutableState.update { it.copy(error = null) }
        refreshJob = scope.launch(jobs) {
            try {
                mutableState.value = TopicsState(fetch().reversed())
            } catch (e: ApiError) {
                // A list already on screen stays usable when a background reload fails
                mutableState.update { if (it.topics == null) it.copy(error = e) else it }
            }
        }
    }

    fun create(name: String) {
        if (mutableState.value.topics == null) return
        val temp = Topic("$TEMP_PREFIX${UUID.randomUUID()}", name, isDefault = false, cardCount = 0, dueCount = 0)
        edit { listOf(temp) + it }
        write(undo = { edit { list -> list.filter { it.id != temp.id } } }, retry = { create(name) }) {
            val saved = postTopic(name)
            edit { list -> list.map { if (it.id == temp.id) saved else it } }
        }
    }

    fun rename(id: String, name: String) {
        val old = mutableState.value.topics?.firstOrNull { it.id == id } ?: return
        edit { list -> list.map { if (it.id == id) it.copy(name = name) else it } }
        write(
            undo = { edit { list -> list.map { if (it.id == id) it.copy(name = old.name) else it } } },
            retry = { rename(id, name) },
        ) { patchTopic(id, name) }
    }

    fun delete(id: String) {
        val list = mutableState.value.topics ?: return
        val index = list.indexOfFirst { it.id == id }
        if (index < 0) return
        val old = list[index]
        edit { current -> current.filter { it.id != id } }
        write(
            undo = { edit { it.take(index) + old + it.drop(index) } },
            retry = { delete(id) },
        ) { deleteTopic(id) }
    }

    private fun edit(change: (List<Topic>) -> List<Topic>) {
        mutableState.update { s -> s.copy(topics = s.topics?.let(change)) }
    }

    private fun write(undo: () -> Unit, retry: () -> Unit, request: suspend () -> Unit) {
        refreshJob?.cancel() // an older response must not overwrite this change
        pending++
        val startedIn = generation
        scope.launch(jobs) {
            try {
                order.withLock { request() }
            } catch (e: ApiError) {
                if (startedIn == generation) {
                    undo()
                    notice.show(retry)
                }
            } finally {
                if (startedIn == generation && --pending == 0) refresh()
            }
        }
    }

    companion object {
        private const val TEMP_PREFIX = "tmp-"

        /** Not saved yet, so it can't be opened, renamed, or deleted. */
        fun isUnsaved(topic: Topic) = topic.id.startsWith(TEMP_PREFIX)
    }
}
