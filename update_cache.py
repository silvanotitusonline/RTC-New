with open("app/src/main/java/za/org/rtc/community/RtcCommunityApplication.kt", "r") as f:
    app_content = f.read()

if "marketplaceImageLoader" not in app_content:
    target = "class RtcCommunityApplication : Application(), Configuration.Provider, ImageLoaderFactory {"
    replacement = """class RtcCommunityApplication : Application(), Configuration.Provider, ImageLoaderFactory {

    val marketplaceImageLoader: ImageLoader by lazy {
        ImageLoader.Builder(this)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("marketplace_persistent_cache"))
                    .maxSizeBytes(150L * 1024 * 1024) // 150MB persistent disk cache for offline gallery persistence
                    .build()
            }
            .crossfade(true)
            .respectCacheHeaders(false)
            .build()
    }"""
    app_content = app_content.replace(target, replacement)
    with open("app/src/main/java/za/org/rtc/community/RtcCommunityApplication.kt", "w") as f:
        f.write(app_content)

with open("app/src/main/java/za/org/rtc/community/feature/marketplace/presentation/MarketplaceComponents.kt", "r") as f:
    comp_content = f.read()

old_func_start = """fun MarketplaceProgressiveImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    thumbnailWidth: Int = 200
) {
    val context = LocalContext.current"""

new_func_start = """fun MarketplaceProgressiveImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    thumbnailWidth: Int = 200
) {
    val context = LocalContext.current
    val app = context.applicationContext as? za.org.rtc.community.RtcCommunityApplication
    val loader = app?.marketplaceImageLoader ?: coil.Coil.imageLoader(context)"""

comp_content = comp_content.replace(old_func_start, new_func_start)
comp_content = comp_content.replace("SubcomposeAsyncImage(", "SubcomposeAsyncImage(\n        imageLoader = loader,")
comp_content = comp_content.replace("AsyncImage(", "AsyncImage(\n                imageLoader = loader,")

with open("app/src/main/java/za/org/rtc/community/feature/marketplace/presentation/MarketplaceComponents.kt", "w") as f:
    f.write(comp_content)

