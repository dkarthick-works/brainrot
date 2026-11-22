package app.flashcards.android.data

import app.flashcards.android.api.ApiError
import app.flashcards.android.api.ApiKey
import app.flashcards.android.api.CreatedKey
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
private fun key(id: String) = ApiKey(id, id, "fc_$id", TIME, null)

@OptIn(ExperimentalCoroutinesApi::class)
class KeyStoreTest {
    private class Harness(scope: TestScope) {
        var server = listOf(key("a"), key("b"))
        var fail = false
        var gate: CompletableDeferred<Unit>? = null
        val notice = SaveNotice()
        val store = KeyStore(
            fetch = { server },
            postKey = { name ->
                gate?.await()
                if (fail) throw ApiError(500, "boom")
                server = server + key("id-$name").copy(name = name)
                CreatedKey("id-$name", name, "fc_x", "fc_full_secret")
            },
            deleteKey = { id ->
                gate?.await()
                if (fail) throw ApiError(500, "boom")
                server = server.filter { it.id != id }
            },
            notice = notice,
            scope = scope.backgroundScope,
        )
        val ids get() = store.state.value.keys?.map { it.id }
    }

    @Test fun createHoldsFullKeyUntilDismissed() = runTest {
        val h = Harness(this)
        h.store.refresh()
        runCurrent()
        h.gate = CompletableDeferred()
        h.store.create("laptop")
        runCurrent()
        assertTrue(KeyStore.isUnsaved(h.store.state.value.keys!!.last()))
        assertTrue(h.store.created.value.isEmpty())

        h.gate!!.complete(Unit)
        runCurrent()
        assertEquals(listOf("a", "b", "id-laptop"), h.ids)
        val created = h.store.created.value.single()
        assertEquals("fc_full_secret", created.key)

        h.store.refresh() // reloading the list must not drop the only copy
        runCurrent()
        assertEquals(1, h.store.created.value.size)

        h.store.dismissCreated(created)
        assertTrue(h.store.created.value.isEmpty())
    }

    @Test fun twoNewKeysAreBothKept() = runTest {
        val h = Harness(this)
        h.store.refresh()
        runCurrent()
        h.store.create("one")
        h.store.create("two")
        runCurrent()
        assertEquals(listOf("id-one", "id-two"), h.store.created.value.map { it.id })
    }

    @Test fun failedCreateIsUndoneAndRetried() = runTest {
        val h = Harness(this)
        h.store.refresh()
        runCurrent()
        h.fail = true
        h.store.create("laptop")
        runCurrent()
        assertEquals(listOf("a", "b"), h.ids)
        assertTrue(h.store.created.value.isEmpty())

        h.fail = false
        val failed = h.notice.current.value!!
        h.notice.clear()
        failed.retry()
        runCurrent()
        assertEquals(1, h.store.created.value.size)
    }

    @Test fun failedRevokeRestoresRow() = runTest {
        val h = Harness(this)
        h.store.refresh()
        runCurrent()
        h.fail = true
        h.store.revoke("a")
        runCurrent()
        assertEquals(listOf("a", "b"), h.ids)
        assertNotNull(h.notice.current.value)
    }

    @Test fun changingUserClearsKeysAndAnyUnshownSecret() = runTest {
        val h = Harness(this)
        h.store.bindUser("u1")
        h.store.refresh()
        runCurrent()
        h.store.create("laptop")
        runCurrent()
        assertEquals(1, h.store.created.value.size)

        h.store.bindUser("u2")
        assertTrue(h.store.created.value.isEmpty())
        assertNull(h.store.state.value.keys)
    }
}
