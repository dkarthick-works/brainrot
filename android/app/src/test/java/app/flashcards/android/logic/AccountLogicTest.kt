package app.flashcards.android.logic

import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountLogicTest {
    private val now = Instant.parse("2026-11-05T12:00:00Z")

    @Test fun usedLabelMatchesWeb() {
        assertEquals("Never used", usedLabel(null, now))
        assertEquals("Used 5m ago", usedLabel(now.minusSeconds(300).toString(), now))
        assertEquals("Used 2h ago", usedLabel(now.minusSeconds(7_200).toString(), now))
        assertEquals("Used 3d ago", usedLabel(now.minusSeconds(3 * 86_400).toString(), now))
    }

    @Test fun dayLabelUsesMonthAndDay() {
        assertEquals("Oct 12", dayLabel("2026-10-12T08:00:00Z", ZoneOffset.UTC, Locale.ENGLISH))
    }

    @Test fun initialsTakeTwoWords() {
        assertEquals("SR", initials("Sam Rivera"))
        assertEquals("SR", initials("  sam  rivera lee "))
        assertEquals("S", initials("sam@example.com"))
        assertEquals("", initials(""))
    }

    @Test fun mcpUrlSitsBesideTheApi() {
        assertEquals("https://brainrot.deeka.work/mcp", mcpUrl("https://brainrot.deeka.work/api/"))
        assertEquals("http://localhost:8088/mcp", mcpUrl("http://localhost:8088/api/"))
    }

    @Test fun claudeCommandMatchesWeb() {
        assertEquals(
            "claude mcp add --transport http brainrot https://x/mcp --header \"Authorization: Bearer <your key>\"",
            claudeCommand("https://x/mcp"),
        )
    }
}
