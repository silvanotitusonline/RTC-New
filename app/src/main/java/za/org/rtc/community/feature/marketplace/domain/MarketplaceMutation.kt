package za.org.rtc.community.feature.marketplace.domain

import java.util.UUID

data class MarketplaceMutationCommand(
    val key: String,
    val action: String,
    val subjectId: String?,
)

/**
 * Tracks logical Marketplace mutations independently from coroutine attempts.
 * A failed retry reuses the same key; a successful intent is retired so a later intent gets a new key.
 */
class MarketplaceMutationTracker(
    private val keyFactory: () -> String = { UUID.randomUUID().toString() },
) {
    private data class Slot(val action: String, val subjectId: String?)

    private val retained = mutableMapOf<Slot, MarketplaceMutationCommand>()
    private val inFlight = mutableSetOf<Slot>()

    @Synchronized
    fun begin(action: String, subjectId: String? = null): MarketplaceMutationCommand? {
        val normalizedAction = action.trim().uppercase()
        require(normalizedAction.isNotBlank()) { "Mutation action is required." }
        val slot = Slot(normalizedAction, subjectId)
        if (!inFlight.add(slot)) return null
        return retained.getOrPut(slot) {
            MarketplaceMutationCommand(
                key = keyFactory(),
                action = normalizedAction,
                subjectId = subjectId,
            )
        }
    }

    @Synchronized
    fun failed(command: MarketplaceMutationCommand) {
        inFlight.remove(Slot(command.action, command.subjectId))
    }

    @Synchronized
    fun succeeded(command: MarketplaceMutationCommand) {
        val slot = Slot(command.action, command.subjectId)
        inFlight.remove(slot)
        retained.remove(slot)
    }

    @Synchronized
    fun isInFlight(action: String, subjectId: String? = null): Boolean =
        Slot(action.trim().uppercase(), subjectId) in inFlight
}
