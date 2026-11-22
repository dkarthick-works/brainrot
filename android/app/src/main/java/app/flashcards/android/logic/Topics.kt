package app.flashcards.android.logic

import app.flashcards.android.api.Topic

const val TOPIC_NAME_MAX = 100
private const val SEARCH_ABOVE = 10

fun plural(n: Int, word: String) = "$n ${if (n == 1) word else word + "s"}"

fun showsTopicSearch(topics: List<Topic>) = topics.size > SEARCH_ABOVE

/** Search applies only once the list is long enough to show the field. */
fun filterTopics(topics: List<Topic>, query: String): List<Topic> {
    if (!showsTopicSearch(topics)) return topics
    val q = query.trim().lowercase()
    return topics.filter { q in it.name.lowercase() }
}

/** Names are unique per user, ignoring case. A rename may change only the case. */
fun isDuplicateName(topics: List<Topic>, name: String, editingId: String?): Boolean {
    val key = name.trim().lowercase()
    return topics.any { it.id != editingId && it.name.lowercase() == key }
}

fun canSendName(topics: List<Topic>, name: String, editing: Topic?): Boolean {
    val trimmed = name.trim()
    return trimmed.isNotEmpty() && !isDuplicateName(topics, trimmed, editing?.id) && trimmed != editing?.name
}
