package app.flashcards.android.data

import app.flashcards.android.api.ApiError
import app.flashcards.android.api.Card
import app.flashcards.android.api.CreateResult
import app.flashcards.android.api.Page
import app.flashcards.android.api.SkippedCard
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val TIME = "2026-11-05T00:00:00Z"
private fun card(id: String, topic: String = "t1", question: String = "q$id") =
    Card(id, topic, question, "a$id", 2.5, 0, 0, TIME, null, TIME, TIME)

@OptIn(ExperimentalCoroutinesApi::class)
class CardStoreTest {
    private class Harness(scope: TestScope) {
        val pages = mutableMapOf<Triple<String?, String, String?>, Page>(
            Triple(null, "", null) to Page(listOf(card("a"), card("b", "t2")), null),
        )
        var failReads = false
        var failWrites = false
        var duplicate = false
        var gate: CompletableDeferred<Unit>? = null
        var settled = 0
        val calls = mutableListOf<String>()
        val notice = SaveNotice()
        val store = CardStore(
            fetchPage = { topic, q, after ->
                calls += "get:$topic:$q:$after"
                gate?.await()
                if (failReads) throw ApiError(0, "network error")
                pages.getValue(Triple(topic, q, after))
            },
            postCard = { topic, question, answer ->
                calls += "post:$topic:$question"
                gate?.await()
                if (failWrites) throw ApiError(500, "boom")
                if (duplicate) {
                    CreateResult(emptyList(), listOf(SkippedCard("x", question, "duplicate")))
                } else {
                    CreateResult(listOf(card("new", topic ?: "t1", question).copy(answer = answer)), emptyList())
                }
            },
            patchCard = { id, question, answer, topic ->
                calls += "patch:$id"
                gate?.await()
                if (failWrites) throw ApiError(500, "boom")
                card(id, topic, question).copy(answer = answer)
            },
            removeCard = { id ->
                calls += "delete:$id"
                gate?.await()
                if (failWrites) throw ApiError(500, "boom")
            },
            notice = notice,
            onSettled = { settled++ },
            scope = scope.backgroundScope,
        )
        val ids get() = store.state.value.cards?.map { it.id }
    }

    @Test fun loadsAndAppendsPagesWithoutDuplicates() = runTest {
        val h = Harness(this)
        h.pages[Triple(null, "", null)] = Page(listOf(card("a"), card("b")), "next")
        h.pages[Triple(null, "", "next")] = Page(listOf(card("b"), card("c")), null)
        h.store.show(null, "")
        runCurrent()
        h.store.loadMore()
        runCurrent()
        assertEquals(listOf("a", "b", "c"), h.ids)
        assertNull(h.store.state.value.nextCursor)
    }

    @Test fun failedNextPageKeepsCardsAndRetries() = runTest {
        val h = Harness(this)
        h.pages[Triple(null, "", null)] = Page(listOf(card("a")), "next")
        h.pages[Triple(null, "", "next")] = Page(listOf(card("b")), null)
        h.store.show(null, "")
        runCurrent()
        h.failReads = true
        h.store.loadMore()
        runCurrent()
        assertTrue(h.store.state.value.moreFailed)
        assertEquals(listOf("a"), h.ids)

        h.failReads = false
        h.store.retryMore()
        runCurrent()
        assertEquals(listOf("a", "b"), h.ids)
    }

    @Test fun searchKeepsOldResultsUntilNewOnesArrive() = runTest {
        val h = Harness(this)
        h.pages[Triple(null, "x", null)] = Page(listOf(card("b", "t2")), null)
        h.store.show(null, "")
        runCurrent()
        h.gate = CompletableDeferred()
        h.store.show(null, "x")
        runCurrent()
        assertEquals(listOf("a", "b"), h.ids)
        h.gate!!.complete(Unit)
        runCurrent()
        assertEquals(listOf("b"), h.ids)
    }

    @Test fun failedSearchDoesNotShowOldResults() = runTest {
        val h = Harness(this)
        h.store.show(null, "")
        runCurrent()
        h.failReads = true
        h.store.show(null, "x")
        runCurrent()
        assertNull(h.store.state.value.cards)
        assertNotNull(h.store.state.value.error)
    }

    @Test fun createShowsFirstThenSwapsForSavedCard() = runTest {
        val h = Harness(this)
        h.store.show(null, "")
        runCurrent()
        h.gate = CompletableDeferred()
        h.store.create(null, "t1", "Q", "A")
        runCurrent()
        assertTrue(CardStore.isUnsaved(h.store.state.value.cards!!.first()))
        h.gate!!.complete(Unit)
        runCurrent()
        assertEquals(listOf("new", "a", "b"), h.ids)
        assertEquals(1, h.settled)
    }

    @Test fun unsavedCardStaysOutOfSearchResults() = runTest {
        val h = Harness(this)
        h.pages[Triple(null, "x", null)] = Page(listOf(card("b", "t2")), null)
        h.store.show(null, "")
        runCurrent()
        h.gate = CompletableDeferred()
        h.store.create(null, "t1", "Q", "A")
        h.store.show(null, "x")
        h.gate!!.complete(Unit)
        runCurrent()
        assertEquals(listOf("b"), h.ids)
    }

    @Test fun duplicateCreateIsUndoneAndHandedBackWithoutBanner() = runTest {
        val h = Harness(this)
        h.store.show(null, "")
        runCurrent()
        h.duplicate = true
        h.store.create("t1", "t1", "Q", "A")
        runCurrent()
        assertEquals(listOf("a", "b"), h.ids)
        assertEquals(listOf(RejectedCard("t1", "Q")), h.store.state.value.rejected)
        assertNull(h.notice.current.value)

        h.store.consumeRejected(RejectedCard("t1", "Q"))
        assertTrue(h.store.state.value.rejected.isEmpty())
    }

    @Test fun movingCardOutOfFilteredTopicRemovesItAndFailureRestoresIt() = runTest {
        val h = Harness(this)
        h.pages[Triple("t1", "", null)] = Page(listOf(card("a"), card("c")), null)
        h.store.show("t1", "")
        runCurrent()
        val moved = card("a")
        h.gate = CompletableDeferred()
        h.failWrites = true
        h.store.update(moved, "qa", "aa", "t2")
        runCurrent()
        assertEquals(listOf("c"), h.ids)

        h.gate!!.complete(Unit)
        runCurrent()
        assertEquals(listOf("a", "c"), h.ids)
        assertNotNull(h.notice.current.value)
    }

    @Test fun failedDeleteRestoresRowAndRetrySendsIt() = runTest {
        val h = Harness(this)
        h.store.show(null, "")
        runCurrent()
        h.failWrites = true
        h.store.delete(card("a"))
        runCurrent()
        assertEquals(listOf("a", "b"), h.ids)

        h.failWrites = false
        val failed = h.notice.current.value!!
        h.notice.clear()
        failed.retry()
        runCurrent()
        assertEquals(listOf("b"), h.ids)
        assertEquals(2, h.calls.count { it == "delete:a" })
    }

    @Test fun pageInFlightCannotBringBackDeletedCard() = runTest {
        val h = Harness(this)
        h.pages[Triple(null, "", null)] = Page(listOf(card("a")), "next")
        h.pages[Triple(null, "", "next")] = Page(listOf(card("gone"), card("b")), null)
        h.store.show(null, "")
        runCurrent()
        h.gate = CompletableDeferred()
        h.store.loadMore()
        runCurrent()
        h.store.delete(card("gone"))
        h.gate!!.complete(Unit)
        runCurrent()
        assertEquals(listOf("a", "b"), h.ids)
    }

    @Test fun changingUserClearsEverything() = runTest {
        val h = Harness(this)
        h.store.bindUser("u1")
        h.store.show(null, "")
        runCurrent()
        h.gate = CompletableDeferred()
        h.store.delete(card("a"))
        runCurrent()
        h.store.bindUser("u2")
        h.gate!!.complete(Unit)
        runCurrent()
        assertNull(h.store.state.value.cards)
        assertEquals(0, h.settled)
    }
}
