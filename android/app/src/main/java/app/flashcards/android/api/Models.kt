package app.flashcards.android.api

import kotlinx.serialization.Serializable

// Mirrors the backend JSON. The backend is the source of truth.
@Serializable
data class Card(
    val id: String,
    val topicId: String,
    val question: String,
    val answer: String,
    val easeFactor: Double,
    val interval: Int,
    val repetitions: Int,
    val dueAt: String,
    val lastReviewedAt: String? = null,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class Topic(val id: String, val name: String, val isDefault: Boolean, val cardCount: Int, val dueCount: Int)

@Serializable
data class Due(val cards: List<Card>, val dueCount: Int, val nextDueAt: String? = null)

@Serializable
data class Page(val cards: List<Card>, val nextCursor: String? = null)

@Serializable
data class SkippedCard(val id: String? = null, val question: String, val reason: String)

@Serializable
data class CreateResult(val created: List<Card>, val skipped: List<SkippedCard>)

@Serializable
data class ApiKey(val id: String, val name: String, val prefix: String, val createdAt: String, val lastUsedAt: String? = null)

@Serializable
data class CreatedKey(val id: String, val name: String, val prefix: String, val key: String)

@Serializable
data class ReviewRequest(val quality: Int)

@Serializable
data class CardInput(val question: String, val answer: String)

@Serializable
data class CreateCardsRequest(val topicId: String?, val cards: List<CardInput>)

@Serializable
data class UpdateCardRequest(val question: String, val answer: String, val topicId: String)

@Serializable
data class NameRequest(val name: String)
