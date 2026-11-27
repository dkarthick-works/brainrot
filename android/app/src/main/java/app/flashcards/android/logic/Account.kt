package app.flashcards.android.logic

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong

const val KEY_NAME_MAX = 100

private val Whitespace = Regex("\\s+")

/** "Used 2h ago" / "Never used" */
fun usedLabel(iso: String?, now: Instant = Instant.now()): String {
    if (iso == null) return "Never used"
    val mins = (Duration.between(Instant.parse(iso), now).toMillis() / 60_000.0).roundToLong()
    return when {
        mins < 60 -> "Used ${mins}m ago"
        mins < 60 * 24 -> "Used ${(mins / 60.0).roundToLong()}h ago"
        else -> "Used ${(mins / 1440.0).roundToLong()}d ago"
    }
}

/** "Oct 12" */
fun dayLabel(iso: String, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String =
    DateTimeFormatter.ofPattern("MMM d", locale).format(Instant.parse(iso).atZone(zone))

/** "Sam Rivera" -> "SR" */
fun initials(nameOrEmail: String): String =
    nameOrEmail.trim().split(Whitespace).filter { it.isNotEmpty() }.joinToString("") { it.take(1) }.take(2).uppercase()

/** The MCP endpoint lives at /mcp on the same host as the API. */
fun mcpUrl(apiBaseUrl: String) = apiBaseUrl.removeSuffix("/").removeSuffix("/api") + "/mcp"

fun claudeCommand(mcpUrl: String) =
    "claude mcp add --transport http brainrot $mcpUrl --header \"Authorization: Bearer <your key>\""
