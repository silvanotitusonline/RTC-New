with open("app/src/main/java/za/org/rtc/community/RtcCommunityApplication.kt", "r") as f:
    content = f.read()

import_statement = """import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
"""
import_idx = content.find("import android.app.Application")
content = content[:import_idx] + import_statement + content[import_idx:]

content = content.replace(
    "class RtcCommunityApplication : Application(), Configuration.Provider {",
    "class RtcCommunityApplication : Application(), Configuration.Provider, ImageLoaderFactory {"
)

image_loader_impl = """
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .crossfade(true)
            .respectCacheHeaders(false) // aggressively cache regardless of server headers
            .build()
    }
"""

on_create_idx = content.find("    override fun onCreate()")
content = content[:on_create_idx] + image_loader_impl + content[on_create_idx:]

with open("app/src/main/java/za/org/rtc/community/RtcCommunityApplication.kt", "w") as f:
    f.write(content)
