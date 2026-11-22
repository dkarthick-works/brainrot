package app.flashcards.android.data

import app.flashcards.android.api.ApiError
import app.flashcards.android.api.Card
import app.flashcards.android.api.Page
import app.flashcards.android.api.Topic
import app.flashcards.android.logic.FreestyleScreenState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

private const val TIME = "2026-11-05T00:00:00Z"
private fun card(id: String) = Card(id, "t", id, "a", 2.5, 0, 0, TIME, null, TIME, TIME)

@OptIn(ExperimentalCoroutinesApi::class)
class FreestyleControllerTest {
    private class Harness(scope: TestScope, val pages: Map<String?, Page>) {
        val requests = mutableListOf<String?>()
        var topicsFail = false
        val controller = FreestyleController(
            fetchPage = { cursor ->
                requests += cursor
                pages.getValue(cursor)
            },
            fetchTopics = {
                if (topicsFail) throw ApiError(500, "boom")
                listOf(Topic("t", "General", true, 1, 0))
            },
            scope = scope.backgroundScope,
        )
        val state get() = controller.state.value
        fun advanceAll() { while (state.queue.isNotEmpty()) controller.advance(state.queue.first().id) }
    }

    @Test fun walksEmptyPagesThenCompletesAndReplays() = runTest {
        val h = Harness(this, mapOf(null to Page(emptyList(), "n"), "n" to Page(listOf(card("a")), null)))
        h.controller.start()
        runCurrent()
        assertEquals(listOf(null, "n"), h.requests)
        assertEquals(FreestyleScreenState.Playing, h.state.screen)
        assertEquals("General", h.controller.topicNames.value["t"])

        h.advanceAll()
        assertEquals(FreestyleScreenState.Complete, h.state.screen)

        h.controller.restart()
        runCurrent()
        assertEquals(listOf(null, "n", null, "n"), h.requests)
        assertEquals(0, h.state.advancedCount)
        assertEquals(FreestyleScreenState.Playing, h.state.screen)
    }

    @Test fun failedSecondPageRetriesSameCursor() = runTest {
        var failNext = true
        val pages = mapOf(null to Page(listOf(card("a")), "n"), "n" to Page(listOf(card("b")), null))
        val requests = mutableListOf<String?>()
        val controller = FreestyleController(
            fetchPage = { cursor ->
                requests += cursor
                if (cursor == "n" && failNext) throw ApiError(0, "network error")
                pages.getValue(cursor)
            },
            fetchTopics = { emptyList() },
            scope = backgroundScope,
        )
        controller.start()
        runCurrent()
        assertNotNull(controller.state.value.error)
        assertEquals(listOf("a"), controller.state.value.queue.map { it.id })
        assertEquals(FreestyleScreenState.Playing, controller.state.value.screen)

        failNext = false
        controller.retry()
        runCurrent()
        assertEquals(listOf(null, "n", "n"), requests)
        assertEquals(listOf("a", "b"), controller.state.value.queue.map { it.id })
    }

    @Test fun topicFailureDoesNotBlockCards() = runTest {
        val h = Harness(this, mapOf(null to Page(listOf(card("a")), null)))
        h.topicsFail = true
        h.controller.start()
        runCurrent()
        assertEquals(FreestyleScreenState.Playing, h.state.screen)
        assertEquals(emptyMap<String, String>(), h.controller.topicNames.value)
    }
}
