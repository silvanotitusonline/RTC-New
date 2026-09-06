package za.org.rtc.community.feature.marketplace.domain

import kotlin.math.max

enum class MarketplaceMediaStage {
    PREPARING,
    UPLOADING,
    FINALIZING,
    COMPLETED,
    FAILED,
}

data class MarketplaceMediaOperation(
    val sourceUri: String,
    val operationKey: String,
    val stage: MarketplaceMediaStage,
    val progress: Float,
    val assetId: String? = null,
    val errorMessage: String? = null,
) {
    val retryable: Boolean get() = stage == MarketplaceMediaStage.FAILED

    fun uploading(progress: Float): MarketplaceMediaOperation = copy(
        stage = MarketplaceMediaStage.UPLOADING,
        progress = progress.coerceIn(0f, 1f),
        errorMessage = null,
    )

    fun finalizing(): MarketplaceMediaOperation = copy(
        stage = MarketplaceMediaStage.FINALIZING,
        progress = max(progress, 0.9f),
        errorMessage = null,
    )

    fun completed(assetId: String): MarketplaceMediaOperation = copy(
        stage = MarketplaceMediaStage.COMPLETED,
        progress = 1f,
        assetId = assetId,
        errorMessage = null,
    )

    fun failed(message: String): MarketplaceMediaOperation = copy(
        stage = MarketplaceMediaStage.FAILED,
        errorMessage = message,
    )

    companion object {
        fun start(sourceUri: String, operationKey: String): MarketplaceMediaOperation {
            require(sourceUri.isNotBlank()) { "Media source is required." }
            require(operationKey.isNotBlank()) { "Media operation key is required." }
            return MarketplaceMediaOperation(
                sourceUri = sourceUri,
                operationKey = operationKey,
                stage = MarketplaceMediaStage.PREPARING,
                progress = 0f,
            )
        }
    }
}

class MarketplaceSignedUrlCache(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val maxEntries: Int = 128,
) {
    private data class Entry(val url: String, val expiresAtEpochMillis: Long)

    init {
        require(maxEntries > 0) { "Signed URL cache must allow at least one entry." }
    }

    private val entries = LinkedHashMap<String, Entry>(16, 0.75f, true)

    @Synchronized
    fun put(path: String, url: String, expiresAtEpochMillis: Long) {
        require(path.isNotBlank()) { "Media path is required." }
        require(url.isNotBlank()) { "Signed media URL is required." }
        entries[path] = Entry(url, expiresAtEpochMillis)
        trimToSize()
    }

    @Synchronized
    fun get(path: String): String? {
        val entry = entries[path] ?: return null
        if (entry.expiresAtEpochMillis <= clock()) {
            entries.remove(path)
            return null
        }
        return entry.url
    }

    @Synchronized
    fun containsValid(path: String): Boolean = get(path) != null

    @Synchronized
    fun remove(path: String) {
        entries.remove(path)
    }

    @Synchronized
    fun clearExpired() {
        val now = clock()
        entries.entries.removeAll { (_, entry) -> entry.expiresAtEpochMillis <= now }
    }

    private fun trimToSize() {
        while (entries.size > maxEntries) {
            val iterator = entries.entries.iterator()
            if (!iterator.hasNext()) return
            iterator.next()
            iterator.remove()
        }
    }
}
