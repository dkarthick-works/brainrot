package app.flashcards.android.api

import java.io.IOException
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
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
}

/** Reads the authorized-party claim for diagnosing a rejected token. Never logs the token. */
fun jwtAzp(token: String): String? = try {
    val payload = String(Base64.getUrlDecoder().decode(token.split(".")[1]))
    ApiClient.json.decodeFromString<JwtClaims>(payload).azp
} catch (_: IllegalArgumentException) {
    null
} catch (_: IndexOutOfBoundsException) {
    null
}

@Serializable
private data class JwtClaims(val azp: String? = null)
