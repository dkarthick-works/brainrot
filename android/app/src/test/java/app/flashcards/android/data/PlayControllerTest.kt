package app.flashcards.android.data

import app.flashcards.android.api.ApiError
import app.flashcards.android.api.Card
import app.flashcards.android.api.Due
import app.flashcards.android.api.Topic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val TIME = "2026-11-05T00:00:00Z"
private fun card(id: String, dueAt: String = TIME) = Card(id, "t", "q$id", "a$id", 2.5, 0, 0, dueAt, null, TIME, TIME)
private val topic = Topic("t", "General", true, 3, 3)

@OptIn(ExperimentalCoroutinesApi::class)
class PlayControllerTest {
    private class Harness(scope: TestScope, val dues: MutableList<Due>, topics: List<Topic> = listOf(topic)) {
        val posts = mutableListOf<Pair<String, Int>>()
        var failPosts = false
        var gate: CompletableDeferred<Unit>? = null
        val writes = ReviewWrites(
            post = { id, q ->
                posts += id to q
                gate?.await()
                if (failPosts) throw ApiError(500, "boom")
                card(id, "2026-11-08T00:00:00Z")
            },
            scope = scope.backgroundScope,
        )
        val controller = PlayController(
            fetchDue = { if (dues.size > 1) dues.removeAt(0) else dues.first() },
            fetchTopics = { topics },
            writes = writes,
            scope = scope.backgroundScope,
        )
        val view get() = controller.model.value.toView()
    }

    @Test fun gradingAllCardsEndsInBrainUpgraded() = runTest {
        val h = Harness(this, mutableListOf(Due(listOf(card("a"), card("b")), 2, null), Due(emptyList(), 0, null)))
        h.controller.enter()
        runCurrent()
        assertEquals("1 / 2", (h.view as PlayView.Study).counter)

        h.controller.grade(card("a"), 4)
        runCurrent()
        assertEquals("2 / 2", (h.view as PlayView.Study).counter)

        h.controller.grade(card("b"), 1)
        runCurrent()
        assertEquals(listOf("a" to 4, "b" to 1), h.posts)
        assertEquals(PlayView.Done("2026-11-08T00:00:00Z", 2, 1), h.view)
    }

    @Test fun prefetchSkipsCardsAlreadySeen() = runTest {
        val first = (1..3).map { card("c$it") }
        val h = Harness(this, mutableListOf(Due(first, 4, null), Due(first + card("c4"), 4, null), Due(first + card("c4"), 4, null)))
        h.controller.enter()
        runCurrent()
        assertEquals(listOf("c1", "c2", "c3", "c4"), (h.view as PlayView.Study).queue.map { it.id })
    }

    @Test fun failedSaveReturnsCardAndBlocksCompletion() = runTest {
        val h = Harness(this, mutableListOf(Due(listOf(card("a")), 1, null), Due(emptyList(), 0, null)))
        h.failPosts = true
        h.controller.enter()
        runCurrent()
        h.controller.grade(card("a"), 4)
        runCurrent()
        assertEquals(listOf("a"), (h.view as PlayView.Study).queue.map { it.id })

        h.failPosts = false
        h.writes.retryFailed()
        runCurrent()
        assertTrue(h.view is PlayView.Done)
        assertEquals(2, h.posts.size)
    }

    @Test fun pendingSaveShowsLoadingNotCompletion() = runTest {
        val h = Harness(this, mutableListOf(Due(listOf(card("a")), 1, null), Due(emptyList(), 0, null)))
        h.gate = CompletableDeferred()
        h.controller.enter()
        runCurrent()
        h.controller.grade(card("a"), 5)
        runCurrent()
        assertEquals(PlayView.Loading, h.view)
        h.gate!!.complete(Unit)
        runCurrent()
        assertTrue(h.view is PlayView.Done)
    }

    @Test fun brandNewUserWhenNoCardsExist() = runTest {
        val h = Harness(this, mutableListOf(Due(emptyList(), 0, null)), listOf(topic.copy(cardCount = 0, dueCount = 0)))
        h.controller.enter()
        runCurrent()
        assertEquals(PlayView.BrandNew, h.view)
    }

    @Test fun changingUserClearsSaves() = runTest {
        val h = Harness(this, mutableListOf(Due(listOf(card("a")), 1, null)))
        h.writes.bindUser("user-1")
        h.failPosts = true
        h.writes.submit("s", card("a"), 4)
        runCurrent()
        h.writes.bindUser("user-1")
        assertEquals(1, h.writes.submissions.value.size)
        h.writes.bindUser("user-2")
        assertTrue(h.writes.submissions.value.isEmpty())
    }
}
