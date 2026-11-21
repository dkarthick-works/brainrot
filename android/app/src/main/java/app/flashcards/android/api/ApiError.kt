package app.flashcards.android.api

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

/** status 0 = the request never reached the server. */
class ApiError(val status: Int, val detail: String) : Exception(detail)

@Serializable
private data class ProblemDetail(val detail: String? = null)

internal fun problemDetail(body: String?, fallback: String): String = try {
    body?.let { ApiClient.json.decodeFromString<ProblemDetail>(it).detail } ?: fallback
} catch (_: IllegalArgumentException) {
    fallback
}

suspend fun <T> apiCall(block: suspend () -> T): T = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: HttpException) {
    throw ApiError(e.code(), problemDetail(e.response()?.errorBody()?.string(), e.message()))
} catch (_: IOException) {
    throw ApiError(0, "network error")
} catch (_: SerializationException) {
    throw ApiError(-1, "unexpected response")
}
