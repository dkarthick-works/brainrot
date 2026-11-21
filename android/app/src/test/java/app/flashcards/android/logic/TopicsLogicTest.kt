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

    @Test fun dueLabelMatchesWeb() {
        val now = java.time.Instant.parse("2026-11-05T00:00:00Z")
        assertEquals("due now", dueLabel(now.minusSeconds(600).toString(), now))
        assertEquals("in 1d", dueLabel(now.plusSeconds(60).toString(), now))
        assertEquals("in 3d", dueLabel(now.plusSeconds(3 * 86_400).toString(), now))
        assertEquals("in 2mo", dueLabel(now.plusSeconds(60 * 86_400).toString(), now))
    }

    @Test fun questionKeyMatchesBackendRule() {
        assertEquals("what is atp?", questionKey("  What   is\nATP? "))
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
