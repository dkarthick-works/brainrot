package app.flashcards.android.logic

import app.flashcards.android.api.ApiError
import app.flashcards.android.api.Card
import app.flashcards.android.api.Page
import kotlin.math.floor
import kotlin.random.Random

enum class FreestyleLoad { Idle, Loading, Error }

enum class FreestyleScreenState { Loading, Error, Playing, Empty, Complete }

/** One read-only pass over every card. Never saved, and never touches the schedule. */
data class FreestyleState(
    val queue: List<Card> = emptyList(),
    val seenIds: Set<String> = emptySet(),
    val acceptedCursors: Set<String?> = emptySet(),
    val advancedCount: Int = 0,
    val firstPageLoaded: Boolean = false,
    val nextCursor: String? = null,
    val load: FreestyleLoad = FreestyleLoad.Idle,
    val error: ApiError? = null,
    val failedCursor: String? = null,
) {
    val screen: FreestyleScreenState
        get() = when {
            queue.isNotEmpty() -> FreestyleScreenState.Playing
            load == FreestyleLoad.Error -> FreestyleScreenState.Error
            !firstPageLoaded || nextCursor != null || load == FreestyleLoad.Loading -> FreestyleScreenState.Loading
            advancedCount > 0 -> FreestyleScreenState.Complete
            else -> FreestyleScreenState.Empty
        }

    /** True when idle and the first page is missing or the buffer is running low. */
    val wantsPage: Boolean
        get() = load == FreestyleLoad.Idle && (!firstPageLoaded || (queue.size <= PREFETCH_AT && nextCursor != null))

    fun loading() = copy(load = FreestyleLoad.Loading, error = null)

    fun failed(cursor: String?, error: ApiError) =
        copy(load = FreestyleLoad.Error, error = error, failedCursor = cursor)

    fun advanced(cardId: String) =
        if (queue.firstOrNull()?.id == cardId) copy(queue = queue.drop(1), advancedCount = advancedCount + 1) else this

    /** Shuffles only this page, so cards already in the buffer keep their order. */
    fun accepted(cursor: String?, page: Page, random: () -> Double = { Random.nextDouble() }): FreestyleState {
        val next = page.nextCursor
        if (cursor in acceptedCursors || (next != null && (next == cursor || next in acceptedCursors))) {
            throw ApiError(-1, "Card pagination did not advance. Try again.")
        }
        val fresh = page.cards.distinctBy { it.id }.filter { it.id !in seenIds }
        val shuffled = shuffle(fresh, random)
        return copy(
            queue = queue + shuffled,
            seenIds = seenIds + shuffled.map { it.id },
            acceptedCursors = acceptedCursors + cursor,
            firstPageLoaded = true,
            nextCursor = next,
            load = FreestyleLoad.Idle,
            error = null,
            failedCursor = null,
        )
    }

    private companion object {
        const val PREFETCH_AT = 5
    }
}

fun <T> shuffle(items: List<T>, random: () -> Double = { Random.nextDouble() }): List<T> {
    val result = items.toMutableList()
    for (i in result.lastIndex downTo 1) {
        val j = floor(random() * (i + 1)).toInt()
        result[i] = result.set(j, result[i])
    }
    return result
}
