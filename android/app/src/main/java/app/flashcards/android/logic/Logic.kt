package app.flashcards.android.logic

import app.flashcards.android.api.Card
import java.time.Duration
import java.time.Instant
import kotlin.math.max
import kotlin.math.roundToInt

data class Grade(val label: String, val quality: Int)

// Quality values match Sm2.java
val Grades = listOf(Grade("Again", 1), Grade("Hard", 3), Grade("Good", 4), Grade("Easy", 5))

/** Days until the card returns if graded [quality]. Mirrors Sm2.java; used only for button hints. */
fun nextInterval(repetitions: Int, interval: Int, easeFactor: Double, quality: Int): Int {
    if (quality < 3) return 1
    if (repetitions == 0) return when (quality) { 3 -> 1; 4 -> 2; else -> 4 }
    val hard = max(interval + 1, Math.round(interval * 1.2).toInt())
    val good = max(hard + 1, Math.round(interval * easeFactor).toInt())
    val easy = max(good + 1, Math.round(interval * easeFactor * 1.3).toInt())
    return when (quality) { 3 -> hard; 4 -> good; else -> easy }
}

/** "in 40m", "in 3h", "tomorrow", "in 5 days" */
fun untilLabel(iso: String, now: Instant = Instant.now()): String {
    val mins = Duration.between(now, Instant.parse(iso)).toMillis() / 60_000.0
    if (mins < 60) return "in ${max(1, mins.roundToInt())}m"
    if (mins < 60 * 24) return "in ${(mins / 60).roundToInt()}h"
    val days = (mins / 1440).roundToInt()
    return if (days <= 1) "tomorrow" else "in $days days"
}

/** Cards from a fresh batch that this session hasn't queued yet. */
fun unseen(batch: List<Card>, seen: Set<String>): List<Card> = batch.filter { it.id !in seen }

/** Prefetch when 5 or fewer remain and the server says more are due than we hold. */
fun shouldPrefetch(queued: Int, dueCount: Int): Boolean = queued <= 5 && dueCount > queued

/** Earliest of the given ISO times, ignoring nulls. */
fun earliest(vararg isos: String?): String? = isos.filterNotNull().minByOrNull { Instant.parse(it) }
