package app.flashcards.android.api

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface BrainrotApi {
    @GET("cards/due")
    suspend fun due(@Query("limit") limit: Int = 20): Due

    @POST("cards/{id}/review")
    suspend fun review(@Path("id") id: String, @Body body: ReviewRequest): Card

    @GET("cards")
    suspend fun cards(
        @Query("topicId") topicId: String? = null,
        @Query("q") q: String? = null,
        @Query("after") after: String? = null,
        @Query("limit") limit: Int = 50,
    ): Page

    @POST("cards")
    suspend fun createCards(@Body body: CreateCardsRequest): CreateResult

    @PATCH("cards/{id}")
    suspend fun updateCard(@Path("id") id: String, @Body body: UpdateCardRequest): Card

    @DELETE("cards/{id}")
    suspend fun deleteCard(@Path("id") id: String)

    @GET("topics")
    suspend fun topics(): List<Topic>

    @POST("topics")
    suspend fun createTopic(@Body body: NameRequest): Topic

    @PATCH("topics/{id}")
    suspend fun renameTopic(@Path("id") id: String, @Body body: NameRequest): Topic

    @DELETE("topics/{id}")
    suspend fun deleteTopic(@Path("id") id: String)

    @GET("api-keys")
    suspend fun apiKeys(): List<ApiKey>

    @POST("api-keys")
    suspend fun createKey(@Body body: NameRequest): CreatedKey

    @DELETE("api-keys/{id}")
    suspend fun revokeKey(@Path("id") id: String)
}
