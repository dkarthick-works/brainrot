package app.flashcards.android.api

import com.clerk.api.Clerk
import com.clerk.api.network.serialization.ClerkResult
import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

object ApiClient {
    internal val json = Json { ignoreUnknownKeys = true }

    fun create(baseUrl: String): BrainrotApi {
        val http = OkHttpClient.Builder().addInterceptor(ClerkAuthInterceptor()).build()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(http)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(BrainrotApi::class.java)
    }
}

/** Runs on OkHttp's background threads, so blocking for the (cached) token is safe. */
private class ClerkAuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { currentToken() } ?: throw IOException("No Clerk session token")
        return chain.proceed(chain.request().newBuilder().header("Authorization", "Bearer $token").build())
    }
}

suspend fun currentToken(): String? = when (val result = Clerk.auth.getToken()) {
    is ClerkResult.Success -> result.value
    is ClerkResult.Failure -> null
}
