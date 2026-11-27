package app.flashcards.android.api

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SessionInterceptorTest {
    private class Harness(
        val codes: ArrayDeque<Int>,
        val fresh: SessionToken = SessionToken.Value("new"),
        val cached: SessionToken = SessionToken.Value("old"),
    ) {
        val sent = mutableListOf<String?>()
        var ended = 0
        private val server = Interceptor { chain ->
            sent += chain.request().header("Authorization")
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(codes.removeFirst())
                .message("test")
                .body("".toResponseBody())
                .build()
        }
        private val client = OkHttpClient.Builder()
            .addInterceptor(SessionInterceptor({ if (it) fresh else cached }, { ended++ }))
            .addInterceptor(server)
            .build()

        fun call() = client.newCall(Request.Builder().url("http://test.invalid/").build()).execute().use { it.code }
    }

    @Test fun successUsesCachedToken() {
        val h = Harness(ArrayDeque(listOf(200)))
        assertEquals(200, h.call())
        assertEquals(listOf("Bearer old"), h.sent)
    }

    @Test fun unauthorizedRetriesOnceWithFreshToken() {
        val h = Harness(ArrayDeque(listOf(401, 200)))
        assertEquals(200, h.call())
        assertEquals(listOf("Bearer old", "Bearer new"), h.sent)
        assertEquals(0, h.ended)
    }

    @Test fun refusedFreshTokenDoesNotSignOut() {
        val h = Harness(ArrayDeque(listOf(401, 401)))
        assertEquals(401, h.call())
        assertEquals(2, h.sent.size)
        assertEquals(0, h.ended)
    }

    @Test fun endedSessionSignsOut() {
        val h = Harness(ArrayDeque(listOf(401)), fresh = SessionToken.Ended)
        assertThrows(IOException::class.java) { h.call() }
        assertEquals(1, h.ended)
    }

    @Test fun unreachableClerkKeepsTheSession() {
        val h = Harness(ArrayDeque(listOf(401)), fresh = SessionToken.Unavailable)
        assertThrows(IOException::class.java) { h.call() }
        assertEquals(0, h.ended)
    }
}
