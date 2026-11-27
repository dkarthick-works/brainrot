package app.flashcards.android.api

import org.junit.Assert.assertEquals
import org.junit.Test

class ApiErrorTest {
    @Test fun readsProblemDetail() = assertEquals("Name taken", problemDetail("""{"status":409,"detail":"Name taken"}""", "fallback"))

    @Test fun fallsBackOnNonJsonBody() = assertEquals("fallback", problemDetail("<html>", "fallback"))

    @Test fun fallsBackWhenDetailMissing() = assertEquals("fallback", problemDetail("{}", "fallback"))
}
