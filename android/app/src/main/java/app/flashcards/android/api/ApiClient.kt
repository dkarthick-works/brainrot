package app.flashcards.android.api

import com.clerk.api.Clerk
import com.clerk.api.network.serialization.ClerkResult
import com.clerk.api.session.GetTokenOptions
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

object ApiClient {
    internal val json = Json { ignoreUnknownKeys = true }

    fun create(baseUrl: String): BrainrotApi {
        val http = OkHttpClient.Builder()
            .addInterceptor(SessionInterceptor(::clerkToken, ::endClerkSession))
            .build()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(http)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(BrainrotApi::class.java)
    }
}

internal sealed interface SessionToken {
    data class Value(val token: String) : SessionToken

    /** Clerk says the session is gone, so the user has to sign in again. */
    data object Ended : SessionToken

    /** Clerk couldn't be reached, which says nothing about the session. */
    data object Unavailable : SessionToken
}

/**
 * Adds the session token. A 401 gets one retry with a fresh token. If the server still refuses a
 * fresh token, the error stays on screen: signing out there could trap the user in a sign-in loop.
 * Runs on OkHttp's background threads, so blocking for the token is safe.
 */
internal class SessionInterceptor(
    private val token: suspend (fresh: Boolean) -> SessionToken,
    private val endSession: suspend () -> Unit,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request().bearer(requireToken(fresh = false)))
        if (response.code != 401) return response
        response.close()
        return chain.proceed(chain.request().bearer(requireToken(fresh = true)))
    }

    private fun requireToken(fresh: Boolean): String = runBlocking {
        when (val result = token(fresh)) {
            is SessionToken.Value -> result.token
            SessionToken.Ended -> {
                endSession()
                throw IOException("Session ended")
            }
            SessionToken.Unavailable -> throw IOException("No session token")
        }
    }

    private fun Request.bearer(token: String) = newBuilder().header("Authorization", "Bearer $token").build()
}

private suspend fun clerkToken(fresh: Boolean): SessionToken =
    when (val result = Clerk.auth.getToken(GetTokenOptions(skipCache = fresh))) {
        is ClerkResult.Success -> SessionToken.Value(result.value)
        is ClerkResult.Failure -> if (result.code == 401 || result.code == 404) SessionToken.Ended else SessionToken.Unavailable
    }

private val endingSession = AtomicBoolean(false)

/** Clearing the session makes AuthGate show sign-in. Parallel requests only sign out once. */
private suspend fun endClerkSession() {
    if (!endingSession.compareAndSet(false, true)) return
    try {
        Clerk.auth.signOut()
    } finally {
        endingSession.set(false)
    }
}
