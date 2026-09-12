with open("app/src/main/java/za/org/rtc/community/RtcCommunityApplication.kt", "r") as f:
    content = f.read()

import_statement = """import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import androidx.work.Constraints
import androidx.work.NetworkType
import java.util.concurrent.TimeUnit
import za.org.rtc.community.feature.marketplace.data.work.MarketplacePrefetchWorker
"""
import_idx = content.find("import android.app.Application")
content = content[:import_idx] + import_statement + content[import_idx:]

create_channels = "        createRtcNotificationChannels(this)"
schedule_worker = """
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
            
        val prefetchWork = PeriodicWorkRequestBuilder<MarketplacePrefetchWorker>(12, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
            
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "marketplace_prefetch",
            ExistingPeriodicWorkPolicy.KEEP,
            prefetchWork
        )"""

content = content.replace(create_channels, create_channels + schedule_worker)

with open("app/src/main/java/za/org/rtc/community/RtcCommunityApplication.kt", "w") as f:
    f.write(content)
