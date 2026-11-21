package app.flashcards.android.data

import app.flashcards.android.api.ApiError
import app.flashcards.android.api.ApiKey
import app.flashcards.android.api.CreatedKey
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

/** No keys and no error means the first load is still running. */
data class KeysState(val keys: List<ApiKey>? = null, val error: ApiError? = null)

/**
 * API keys with optimistic saves. A new key's secret exists only in the create response, so it
 * stays in memory until the user dismisses it, even if they navigate away first.
 */
class KeyStore(
    private val fetch: suspend () -> List<ApiKey>,
    private val postKey: suspend (name: String) -> CreatedKey,
    private val deleteKey: suspend (id: String) -> Unit,
    private val notice: SaveNotice,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(KeysState())
    val state: StateFlow<KeysState> = mutableState.asStateFlow()

    private val mutableCreated = MutableStateFlow<List<CreatedKey>>(emptyList())

    /** Keys waiting to be shown, oldest first. */
    val created: StateFlow<List<CreatedKey>> = mutableCreated.asStateFlow()

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
        mutableCreated.value = emptyList()
        mutableState.value = KeysState()
    }

    fun refresh() {
        if (pending > 0) return // the reload after the last save covers this
        refreshJob?.cancel()
        mutableState.update { it.copy(error = null) }
        refreshJob = scope.launch(jobs) {
            try {
                mutableState.value = KeysState(fetch())
            } catch (e: ApiError) {
                mutableState.update { if (it.keys == null) it.copy(error = e) else it }
            }
        }
    }

    fun create(name: String) {
        val temp = ApiKey("$TEMP_PREFIX${UUID.randomUUID()}", name, "fc_", Instant.now().toString(), null)
        edit { it + temp }
        write(undo = { edit { list -> list.filter { it.id != temp.id } } }, retry = { create(name) }) {
            val saved = postKey(name)
            mutableCreated.update { it + saved }
            edit { list ->
                list.map { if (it.id == temp.id) ApiKey(saved.id, saved.name, saved.prefix, temp.createdAt, null) else it }
            }
        }
    }

    fun revoke(id: String) {
        val list = mutableState.value.keys ?: return
        val index = list.indexOfFirst { it.id == id }
        if (index < 0) return
        val old = list[index]
        edit { current -> current.filter { it.id != id } }
        write(undo = { edit { it.take(index) + old + it.drop(index) } }, retry = { revoke(id) }) { deleteKey(id) }
    }

    fun dismissCreated(key: CreatedKey) {
        mutableCreated.update { list -> list.filter { it.id != key.id } }
    }

    private fun edit(change: (List<ApiKey>) -> List<ApiKey>) {
        mutableState.update { s -> s.copy(keys = s.keys?.let(change)) }
    }

    private fun write(undo: () -> Unit, retry: () -> Unit, request: suspend () -> Unit) {
        refreshJob?.cancel() // an older response must not overwrite this change
        pending++
        val startedIn = generation
        scope.launch(jobs) {
            try {
                order.withLock { request() }
            } catch (_: ApiError) {
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

        fun isUnsaved(key: ApiKey) = key.id.startsWith(TEMP_PREFIX)
    }
}
