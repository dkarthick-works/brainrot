package app.flashcards.android.api

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApiErrorTest {
    private fun jwt(payload: String): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        return "h.${encoder.encodeToString(payload.toByteArray())}.s"
    }

    @Test fun readsProblemDetail() = assertEquals("Name taken", problemDetail("""{"status":409,"detail":"Name taken"}""", "fallback"))

    @Test fun fallsBackOnNonJsonBody() = assertEquals("fallback", problemDetail("<html>", "fallback"))

    @Test fun fallsBackWhenDetailMissing() = assertEquals("fallback", problemDetail("{}", "fallback"))

    @Test fun readsAzp() = assertEquals("https://brainrot.deeka.work", jwtAzp(jwt("""{"sub":"u","azp":"https://brainrot.deeka.work"}""")))

    @Test fun missingAzpIsNull() = assertNull(jwtAzp(jwt("""{"sub":"u"}""")))

    @Test fun malformedTokenIsNull() = assertNull(jwtAzp("not-a-jwt"))
}
