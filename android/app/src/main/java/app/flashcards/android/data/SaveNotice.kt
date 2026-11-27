package app.flashcards.android.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Each failure is a new instance, so the banner's 8s timer restarts. */
class FailedSave(val retry: () -> Unit)

/** The one app-wide "Couldn't save" banner, holding the retry for the last failed save. */
class SaveNotice {
    private val mutableCurrent = MutableStateFlow<FailedSave?>(null)
    val current: StateFlow<FailedSave?> = mutableCurrent.asStateFlow()

    fun show(retry: () -> Unit) {
        mutableCurrent.value = FailedSave(retry)
    }

    fun clear() {
        mutableCurrent.value = null
    }

    fun dismiss(save: FailedSave) {
        mutableCurrent.compareAndSet(save, null)
    }
}
