package za.org.rtc.community.core.media

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.LinkedHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SignedUrlValue(
    val url: String,
    val expiresAt: Instant,
)

class SignedUrlCache(
    private val capacity: Int,
    private val refreshSkew: Duration,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val mutex = Mutex()
    private val entries = LinkedHashMap<String, SignedUrlValue>(capacity, 0.75f, true)
    private val inFlight = mutableMapOf<String, CompletableDeferred<SignedUrlValue?>>()

    init {
        require(capacity > 0) { "Signed URL cache capacity must be positive." }
        require(!refreshSkew.isNegative) { "Signed URL refresh skew cannot be negative." }
    }

    suspend fun getOrIssue(
        key: String,
        issue: suspend () -> SignedUrlValue?,
    ): String? {
        var shared: CompletableDeferred<SignedUrlValue?>? = null
        var leader: CompletableDeferred<SignedUrlValue?>? = null
        val now = clock.instant()

        mutex.withLock {
            val cached = entries[key]
            if (cached != null && now.plus(refreshSkew).isBefore(cached.expiresAt)) {
                return cached.url
            }
            if (cached != null) entries.remove(key)

            shared = inFlight[key]
            if (shared == null) {
                leader = CompletableDeferred<SignedUrlValue?>().also { inFlight[key] = it }
            }
        }

        shared?.let { return it.await()?.url }
        val deferred = requireNotNull(leader)

        return try {
            val issued = issue()
            mutex.withLock {
                if (issued != null) {
                    entries[key] = issued
                    while (entries.size > capacity) {
                        entries.entries.iterator().also { iterator ->
                            if (iterator.hasNext()) {
                                iterator.next()
                                iterator.remove()
                            }
                        }
                    }
                }
                inFlight.remove(key)
                deferred.complete(issued)
            }
            issued?.url
        } catch (error: Throwable) {
            mutex.withLock {
                inFlight.remove(key)
                deferred.completeExceptionally(error)
            }
            throw error
        }
    }

    suspend fun invalidate(key: String) {
        mutex.withLock { entries.remove(key) }
    }

    suspend fun clear() {
        mutex.withLock { entries.clear() }
    }
}
