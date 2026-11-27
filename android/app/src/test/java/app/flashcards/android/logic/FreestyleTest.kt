package app.flashcards.android.logic

import app.flashcards.android.api.ApiError
import app.flashcards.android.api.Card
import app.flashcards.android.api.Page
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private const val TIME = "2026-11-05T00:00:00Z"
private fun card(id: String) = Card(id, "t", id, "answer", 2.5, 10, 3, TIME, null, TIME, TIME)
private fun page(vararg ids: String, next: String? = null) = Page(ids.map(::card), next)
private val zero = { 0.0 }

class FreestyleTest {
    @Test fun shuffleIsFisherYatesAndKeepsInput() {
        val cards = listOf(card("a"), card("b"), card("c"))
        assertEquals(emptyList<Card>(), shuffle(emptyList<Card>(), zero))
        assertEquals(listOf("b", "c", "a"), shuffle(cards, zero).map { it.id })
        assertEquals(listOf("a", "b", "c"), cards.map { it.id })
        assertEquals(listOf("a", "b", "c"), shuffle(cards).map { it.id }.sorted())
    }

    @Test fun dedupesWithinAndAcrossPages() {
        val first = FreestyleState().accepted(null, page("a", "a", "b", next = "server"), zero)
        assertEquals(listOf("b", "a"), first.queue.map { it.id })
        assertEquals("server", first.nextCursor)
        val next = first.accepted("server", page("a", "c", "c"))
        assertEquals(listOf("b", "a", "c"), next.queue.map { it.id })
        assertEquals(setOf("a", "b", "c"), next.seenIds)
    }

    @Test fun advancesOnlyTheCurrentTopCard() {
        val first = FreestyleState().accepted(null, page("a", "b")) { 0.99 }
        assertSame(first, first.advanced("b"))
        val next = first.advanced("a")
        assertSame(next, next.advanced("a"))
        assertEquals(1, next.advancedCount)
        assertEquals(listOf("b"), next.queue.map { it.id })
        assertTrue("a" in next.seenIds)
    }

    @Test fun screenStatesCoverTheWholePass() {
        assertEquals(FreestyleScreenState.Loading, FreestyleState().screen)
        var s = FreestyleState().accepted(null, page(next = "next"))
        assertEquals(FreestyleScreenState.Loading, s.screen)
        s = s.accepted("next", page("a", next = "final"))
        assertEquals(FreestyleScreenState.Playing, s.screen)
        s = s.advanced("a").loading()
        assertEquals(FreestyleScreenState.Loading, s.screen)
        s = s.failed("final", ApiError(0, "offline"))
        assertEquals(FreestyleScreenState.Error, s.screen)
        s = s.accepted("final", page())
        assertEquals(FreestyleScreenState.Complete, s.screen)
        assertEquals(FreestyleScreenState.Empty, FreestyleState().accepted(null, page()).screen)
    }

    @Test fun rejectsStuckOrCyclicCursors() {
        val first = FreestyleState().accepted(null, page(next = "a"))
        assertThrows(ApiError::class.java) { first.accepted("a", page("x", next = "a")) }
        val second = first.accepted("a", page(next = "b"))
        assertThrows(ApiError::class.java) { second.accepted("b", page(next = "a")) }
        assertThrows(ApiError::class.java) { second.accepted("a", page()) }
    }

    @Test fun laterPageErrorKeepsProgress() {
        val s = FreestyleState().accepted(null, page("a", next = "next"))
        val failed = s.failed("next", ApiError(0, "offline"))
        assertSame(s.queue, failed.queue)
        assertSame(s.seenIds, failed.seenIds)
        assertEquals("next", failed.nextCursor)
        assertEquals("next", failed.failedCursor)
        assertEquals(FreestyleScreenState.Playing, failed.screen)
    }

    @Test fun prefetchesOnlyWhenBufferIsLow() {
        val big = FreestyleState().accepted(null, page("a", "b", "c", "d", "e", "f", next = "n"))
        assertEquals(false, big.wantsPage)
        assertEquals(true, big.advanced(big.queue.first().id).wantsPage)
    }
}
