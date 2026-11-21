package app.flashcards.android.data

import app.flashcards.android.api.ApiError
import app.flashcards.android.api.Topic
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

private fun topic(id: String, name: String = id, cards: Int = 0, isDefault: Boolean = false) =
    Topic(id, name, isDefault, cards, 0)

@OptIn(ExperimentalCoroutinesApi::class)
class TopicStoreTest {
    private class Harness(scope: TestScope) {
        var server = listOf(topic("old"), topic("new"))
        var fail = false
        var fetchFails = false
        var gate: CompletableDeferred<Unit>? = null
        var fetches = 0
        val calls = mutableListOf<String>()
        val notice = SaveNotice()
        val store = TopicStore(
            fetch = {
                fetches++
                if (fetchFails) throw ApiError(0, "network error")
                server
            },
            postTopic = { name ->
                calls += "create:$name"
                gate?.await()
                if (fail) throw ApiError(500, "boom")
                topic("id-$name", name).also { server = server + it }
            },
            patchTopic = { id, name ->
                calls += "rename:$id:$name"
                gate?.await()
                if (fail) throw ApiError(500, "boom")
                server = server.map { if (it.id == id) it.copy(name = name) else it }
                topic(id, name)
            },
            deleteTopic = { id ->
                calls += "delete:$id"
                gate?.await()
                if (fail) throw ApiError(500, "boom")
                server = server.filter { it.id != id }
            },
            notice = notice,
            scope = scope.backgroundScope,
        )
        val names get() = store.state.value.topics?.map { it.name }
    }

    @Test fun loadsNewestFirst() = runTest {
        val h = Harness(this)
        h.store.refresh()
        runCurrent()
        assertEquals(listOf("new", "old"), h.names)
    }

    @Test fun createShowsAtTopBeforeServerAnswers() = runTest {
        val h = Harness(this)
        h.store.refresh()
        runCurrent()
        h.gate = CompletableDeferred()
        h.store.create("fresh")
        runCurrent()
        assertEquals(listOf("fresh", "new", "old"), h.names)
        assertTrue(TopicStore.isUnsaved(h.store.state.value.topics!!.first()))

        h.gate!!.complete(Unit)
        runCurrent()
        assertEquals("id-fresh", h.store.state.value.topics!!.first { it.name == "fresh" }.id)
    }

    @Test fun failedRenameUndoesAndRetrySendsSameChange() = runTest {
        val h = Harness(this)
        h.store.refresh()
        runCurrent()
        h.fail = true
        h.store.rename("old", "renamed")
        runCurrent()
        assertEquals(listOf("new", "old"), h.names)
        val failed = h.notice.current.value
        assertNotNull(failed)

        h.fail = false
        h.notice.clear()
        failed!!.retry()
        runCurrent()
        assertEquals(listOf("rename:old:renamed", "rename:old:renamed"), h.calls)
        assertEquals(listOf("new", "renamed"), h.names)
    }

    @Test fun failedDeleteRestoresRowInPlace() = runTest {
        val h = Harness(this)
        h.server = listOf(topic("a"), topic("b"), topic("c"))
        h.store.refresh()
        runCurrent()
        h.fail = true
        h.store.delete("b")
        runCurrent()
        assertEquals(listOf("c", "b", "a"), h.names)
        assertNotNull(h.notice.current.value)
    }

    @Test fun savesRunInOrderAndReloadOnceAtTheEnd() = runTest {
        val h = Harness(this)
        h.store.refresh()
        runCurrent()
        val fetchesBefore = h.fetches
        h.gate = CompletableDeferred()
        h.store.rename("old", "one")
        h.store.rename("old", "two")
        h.store.refresh() // must not overwrite the pending changes
        runCurrent()
        assertEquals(listOf("rename:old:one"), h.calls)
        assertEquals(fetchesBefore, h.fetches)

        h.gate!!.complete(Unit)
        runCurrent()
        assertEquals(listOf("rename:old:one", "rename:old:two"), h.calls)
        assertEquals(fetchesBefore + 1, h.fetches)
        assertEquals(listOf("new", "two"), h.names)
    }

    @Test fun failedBackgroundReloadKeepsTheList() = runTest {
        val h = Harness(this)
        h.store.refresh()
        runCurrent()
        h.fetchFails = true
        h.store.refresh()
        runCurrent()
        assertEquals(listOf("new", "old"), h.names)
        assertNull(h.store.state.value.error)
    }

    @Test fun firstLoadFailureShowsError() = runTest {
        val h = Harness(this)
        h.fetchFails = true
        h.store.refresh()
        runCurrent()
        assertNull(h.store.state.value.topics)
        assertEquals(0, h.store.state.value.error?.status)
    }

    @Test fun changingUserClearsListNoticeAndPendingSave() = runTest {
        val h = Harness(this)
        h.store.bindUser("u1")
        h.store.refresh()
        runCurrent()
        h.notice.show {}
        h.gate = CompletableDeferred()
        h.store.create("fresh")
        runCurrent()

        h.store.bindUser("u2")
        h.gate!!.complete(Unit)
        runCurrent()
        assertNull(h.store.state.value.topics)
        assertNull(h.notice.current.value)

        h.gate = null
        h.store.refresh()
        runCurrent()
        assertEquals(listOf("new", "old"), h.names)
    }
}
