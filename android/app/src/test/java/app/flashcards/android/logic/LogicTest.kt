package app.flashcards.android.logic

import app.flashcards.android.api.Card
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val now = Instant.parse("2026-11-05T00:00:00Z")
private fun at(mins: Double) = now.plus(Duration.ofMillis((mins * 60_000).toLong())).toString()
private fun card(id: String) = Card(id, "t", "q", "a", 2.5, 0, 0, "2026-11-05T00:00:00Z", null, "2026-11-05T00:00:00Z", "2026-11-05T00:00:00Z")

class LogicTest {
    @Test fun nextIntervalMirrorsSm2() {
        // repetitions, interval, ease, Hard, Good, Easy: same cases as Sm2Test and logic.test.ts
        listOf(
            listOf(0, 0, 2.5, 1, 2, 4),
            listOf(0, 1, 1.3, 1, 2, 4),
            listOf(1, 1, 2.5, 2, 3, 4),
            listOf(1, 2, 2.5, 3, 5, 7),
            listOf(2, 10, 2.5, 12, 25, 33),
            listOf(5, 6, 1.3, 7, 8, 10),
        ).forEach { row ->
            val reps = row[0].toInt()
            val interval = row[1].toInt()
            val ease = row[2].toDouble()
            (0..2).forEach { assertEquals(1, nextInterval(reps, interval, ease, it)) }
            assertEquals(row[3].toInt(), nextInterval(reps, interval, ease, 3))
            assertEquals(row[4].toInt(), nextInterval(reps, interval, ease, 4))
            assertEquals(row[5].toInt(), nextInterval(reps, interval, ease, 5))
        }
    }

    @Test fun untilLabels() {
        assertEquals("in 1m", untilLabel(at(0.2), now))
        assertEquals("in 40m", untilLabel(at(40.0), now))
        assertEquals("in 3h", untilLabel(at(180.0), now))
        assertEquals("tomorrow", untilLabel(at(1440.0), now))
        assertEquals("in 5 days", untilLabel(at(5 * 1440.0), now))
    }

    @Test fun queuePrefetchAndDedupe() {
        assertEquals(listOf("b"), unseen(listOf(card("a"), card("b"), card("c")), setOf("a", "c")).map { it.id })
        assertTrue(shouldPrefetch(5, 30))
        assertFalse(shouldPrefetch(6, 30))
        assertFalse(shouldPrefetch(3, 3))
        assertEquals(at(60.0), earliest(null, at(300.0), at(60.0)))
        assertNull(earliest(null, null))
    }
}
