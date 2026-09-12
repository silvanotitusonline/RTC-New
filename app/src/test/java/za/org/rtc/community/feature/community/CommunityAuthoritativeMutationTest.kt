package za.org.rtc.community.feature.community

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CommunityAuthoritativeMutationTest {

    @Test
    fun `successful remote mutation commits authoritative state without rollback`() = runTest {
        val events = mutableListOf<String>()

        val result = runAuthoritativeOptimisticMutation(
            applyOptimistic = { events += "optimistic" },
            executeRemote = {
                events += "remote"
                7
            },
            applyAuthoritative = { outcome -> events += "authoritative:$outcome" },
            rollback = { events += "rollback" },
        )

        assertEquals(7, result)
        assertEquals(listOf("optimistic", "remote", "authoritative:7"), events)
    }

    @Test
    fun `failed optimistic mutation attempts rollback and propagates original failure`() = runTest {
        val events = mutableListOf<String>()
        val optimisticFailure = IllegalStateException("room unavailable")
        var thrown: Throwable? = null

        try {
            runAuthoritativeOptimisticMutation(
                applyOptimistic = {
                    events += "optimistic"
                    throw optimisticFailure
                },
                executeRemote = {
                    events += "remote"
                    7
                },
                applyAuthoritative = { events += "authoritative:$it" },
                rollback = { events += "rollback" },
            )
        } catch (error: Throwable) {
            thrown = error
        }

        assertSame(optimisticFailure, thrown)
        assertEquals(listOf("optimistic", "rollback"), events)
    }

    @Test
    fun `failed remote mutation rolls back optimistic state and propagates failure`() = runTest {
        val events = mutableListOf<String>()
        val remoteFailure = IllegalStateException("offline")
        var thrown: Throwable? = null

        try {
            runAuthoritativeOptimisticMutation(
                applyOptimistic = { events += "optimistic" },
                executeRemote = {
                    events += "remote"
                    throw remoteFailure
                },
                applyAuthoritative = { events += "authoritative" },
                rollback = { events += "rollback" },
            )
        } catch (error: Throwable) {
            thrown = error
        }

        assertSame(remoteFailure, thrown)
        assertEquals(listOf("optimistic", "remote", "rollback"), events)
        assertTrue(thrown is IllegalStateException)
    }

    @Test
    fun `rollback failure is suppressed on original remote failure`() = runTest {
        val remoteFailure = IllegalStateException("offline")
        val rollbackFailure = IllegalArgumentException("cache unavailable")
        var thrown: Throwable? = null

        try {
            runAuthoritativeOptimisticMutation(
                applyOptimistic = {},
                executeRemote = { throw remoteFailure },
                applyAuthoritative = {},
                rollback = { throw rollbackFailure },
            )
        } catch (error: Throwable) {
            thrown = error
        }

        assertSame(remoteFailure, thrown)
        assertEquals(listOf(rollbackFailure), thrown?.suppressed?.toList())
    }
}
