package za.org.rtc.community.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SafeUiErrorTest {

    @Test
    fun `generic unknown backend failure returns only bounded fallback copy`() {
        val rawDetail = "PostgrestException PGRST202: {\"code\":\"42883\",\"message\":\"operator does not exist: record ->> unknown\",\"details\":\"select * from private.table\"}"

        val message = SafeUiError.generic(
            IllegalStateException(rawDetail),
            "Something went wrong. Please try again.",
        )

        assertEquals("Something went wrong. Please try again.", message)
        assertFalse(message.contains("IllegalStateException"))
        assertFalse(message.contains("PGRST202"))
        assertFalse(message.contains("42883"))
        assertFalse(message.contains("private.table"))
    }

    @Test
    fun `generic keeps concise semantic authentication guidance`() {
        assertEquals(
            "Invalid login credentials. Please check your email and password.",
            SafeUiError.generic(
                IllegalArgumentException("Invalid login credentials"),
                "Sign-in could not be completed.",
            ),
        )
        assertEquals(
            "Please confirm your email address before signing in.",
            SafeUiError.generic(
                IllegalArgumentException("Email not confirmed"),
                "Sign-in could not be completed.",
            ),
        )
    }

    @Test
    fun `generic keeps concise permission timeout and connectivity guidance`() {
        assertEquals(
            "You do not have access to complete this action.",
            SafeUiError.generic(SecurityException("permission denied by row-level policy"), "Action failed."),
        )
        assertEquals(
            "The request took too long. Check your connection and try again.",
            SafeUiError.generic(IllegalStateException("request timed out"), "Action failed."),
        )
        assertEquals(
            "The service could not be reached. Check your connection and try again.",
            SafeUiError.generic(IllegalStateException("network connection refused"), "Action failed."),
        )
    }
}
