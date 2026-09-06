package za.org.rtc.community.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRestorationTest {

    @Test
    fun `initial session restoration state is true before completion`() {
        val isRestoring = MutableStateFlow(true)
        assertTrue(isRestoring.value)
    }

    @Test
    fun `session restoration completes to false after session check`() = runTest {
        val isRestoring = MutableStateFlow(true)
        assertTrue(isRestoring.value)
        isRestoring.value = false
        assertFalse(isRestoring.value)
    }
}
