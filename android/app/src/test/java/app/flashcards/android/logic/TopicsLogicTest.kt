package app.flashcards.android.logic

import app.flashcards.android.api.Topic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun topic(id: String, name: String = id) = Topic(id, name, false, 0, 0)

class TopicsLogicTest {
    @Test fun pluralMatchesWeb() {
        assertEquals("0 cards", plural(0, "card"))
        assertEquals("1 card", plural(1, "card"))
        assertEquals("42 cards", plural(42, "card"))
    }

    @Test fun searchAppliesOnlyAboveTenTopics() {
        val ten = (1..10).map { topic("t$it") }
        assertFalse(showsTopicSearch(ten))
        assertEquals(ten, filterTopics(ten, "nothing"))

        val eleven = ten + topic("x", "Japanese")
        assertTrue(showsTopicSearch(eleven))
        assertEquals(listOf("Japanese"), filterTopics(eleven, "  JAPAN ").map { it.name })
        assertEquals(eleven, filterTopics(eleven, "   "))
    }

    @Test fun duplicatesIgnoreCaseAndTheTopicBeingRenamed() {
        val list = listOf(topic("a", "Biology"), topic("b", "SQL"))
        assertTrue(isDuplicateName(list, " biology ", null))
        assertFalse(isDuplicateName(list, "biology", "a"))
        assertTrue(isDuplicateName(list, "sql", "a"))
    }

    @Test fun sendRules() {
        val biology = topic("a", "Biology")
        val list = listOf(biology, topic("b", "SQL"))
        assertFalse(canSendName(list, "   ", null))
        assertFalse(canSendName(list, "sql", null))
        assertTrue(canSendName(list, "Chemistry", null))
        assertFalse(canSendName(list, " Biology ", biology))
        assertTrue(canSendName(list, "BIOLOGY", biology))
        assertFalse(canSendName(list, "SQL", biology))
    }
}
