with open("app/src/main/java/za/org/rtc/community/feature/marketplace/data/work/MarketplacePrefetchWorker.kt", "r") as f:
    content = f.read()

content = content.replace("import za.org.rtc.community.feature.marketplace.domain.MarketplaceRepository", "import za.org.rtc.community.feature.marketplace.domain.MarketplaceDiscoveryRepository")
content = content.replace("private val marketplaceRepository: MarketplaceRepository", "private val marketplaceRepository: MarketplaceDiscoveryRepository")

with open("app/src/main/java/za/org/rtc/community/feature/marketplace/data/work/MarketplacePrefetchWorker.kt", "w") as f:
    f.write(content)
