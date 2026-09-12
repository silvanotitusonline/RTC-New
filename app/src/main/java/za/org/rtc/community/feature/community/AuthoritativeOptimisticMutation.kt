package za.org.rtc.community.feature.community

/**
 * Executes a cache-backed optimistic mutation while keeping the remote service authoritative.
 *
 * The optimistic write happens first so Room-backed consumers update immediately. A successful
 * remote mutation then reconciles the cache with the authoritative response. If the remote write
 * or reconciliation fails, the original cache state is restored and the failure is propagated so
 * ViewModel-level rollback/error handling can run.
 */
internal suspend fun <T> runAuthoritativeOptimisticMutation(
    applyOptimistic: suspend () -> Unit,
    executeRemote: suspend () -> T,
    applyAuthoritative: suspend (T) -> Unit,
    rollback: suspend () -> Unit,
): T {
    applyOptimistic()
    return try {
        val outcome = executeRemote()
        applyAuthoritative(outcome)
        outcome
    } catch (error: Throwable) {
        try {
            rollback()
        } catch (rollbackError: Throwable) {
            error.addSuppressed(rollbackError)
        }
        throw error
    }
}
