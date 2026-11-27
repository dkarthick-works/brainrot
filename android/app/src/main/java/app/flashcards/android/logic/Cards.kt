package app.flashcards.android.logic

import java.time.Duration
import java.time.Instant
import kotlin.math.ceil
import kotlin.math.roundToInt

const val QUESTION_MAX = 2000
const val ANSWER_MAX = 5000
const val DUE_NOW = "due now"

/** "due now", "in 3d", "in 2mo" */
fun dueLabel(iso: String, now: Instant = Instant.now()): String {
    val days = ceil(Duration.between(now, Instant.parse(iso)).toMillis() / 86_400_000.0).toInt()
    return when {
        days <= 0 -> DUE_NOW
        days < 30 -> "in ${days}d"
        else -> "in ${(days / 30.0).roundToInt()}mo"
    }
}

private val Spaces = Regex("\\s+")

/** Mirrors Card.questionKey: the backend dedupes questions per topic on this. */
fun questionKey(question: String) = question.trim().replace(Spaces, " ").lowercase()
