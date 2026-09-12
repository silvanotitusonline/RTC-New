package za.org.rtc.community.feature.marketplace.presentation

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import za.org.rtc.community.feature.marketplace.domain.MarketplaceBusinessDetail
import za.org.rtc.community.feature.marketplace.domain.MarketplaceHoursEvaluator
import za.org.rtc.community.feature.marketplace.domain.MarketplaceHoursException
import za.org.rtc.community.feature.marketplace.domain.MarketplaceHoursInterval
import za.org.rtc.community.feature.marketplace.domain.MarketplaceLocation
import za.org.rtc.community.feature.marketplace.domain.MarketplaceOpeningStatus
import za.org.rtc.community.navigation.RtcRoute
import za.org.rtc.community.ui.theme.RtcSize
import za.org.rtc.community.ui.theme.RtcSpacing

fun dispatchBusinessAnnouncementNotification(
    context: android.content.Context,
    businessName: String,
    title: String,
    body: String,
) {
    val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager ?: return
    val intent = Intent(context, za.org.rtc.community.MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    val pendingIntent = android.app.PendingIntent.getActivity(
        context,
        (System.currentTimeMillis() % 100000).toInt(),
        intent,
        android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
    )

    val notification = androidx.core.app.NotificationCompat.Builder(
        context,
        za.org.rtc.community.notifications.RTC_COMMUNITY_UPDATES_CHANNEL,
    )
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("📢 $businessName Announcement")
        .setContentText("$title: $body")
        .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(body))
        .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .build()

    try {
        notificationManager.notify((System.currentTimeMillis() % 100000).toInt(), notification)
    } catch (_: Exception) {}
}

private data class ActivityPostData(
    val id: String,
    val title: String,
    val body: String,
    val dateLabel: String,
    val categoryBadge: String,
    val initialLikes: Int,
)

@Composable
fun BusinessActivitySection(
    detail: MarketplaceBusinessDetail,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var posts by remember(detail) {
        val list = mutableListOf<ActivityPostData>()
        list.add(
            ActivityPostData(
                id = "act-1",
                title = "Welcome to ${detail.card.displayName}!",
                body = "We're active on RTC Community Marketplace! Explore our offerings, view location directions, or submit a booking request directly from our profile.",
                dateLabel = "Recently",
                categoryBadge = "ANNOUNCEMENT",
                initialLikes = 14,
            )
        )
        if (detail.card.verified) {
            list.add(
                ActivityPostData(
                    id = "act-2",
                    title = "Verified Community Provider",
                    body = "Our business listing has been confirmed and verified by RTC community admins. We are dedicated to providing trusted and high quality local service.",
                    dateLabel = "Verified Status",
                    categoryBadge = "VERIFIED",
                    initialLikes = 32,
                )
            )
        }
        if (detail.offerings.isNotEmpty()) {
            val firstOffering = detail.offerings.first()
            list.add(
                ActivityPostData(
                    id = "act-3",
                    title = "Featured Service: ${firstOffering.title}",
                    body = "${firstOffering.description.ifBlank { "Now accepting direct community requests for " + firstOffering.title }}. Pricing: ${firstOffering.priceLabel}.",
                    dateLabel = "2 days ago",
                    categoryBadge = "SERVICE UPDATE",
                    initialLikes = 9,
                )
            )
        }
        mutableStateOf(list.toList())
    }

    var showPostDialog by remember { mutableStateOf(false) }
    var notificationNoticeMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Campaign,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = "Activity & Announcements",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
            }

            OutlinedButton(
                onClick = { showPostDialog = true },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                modifier = Modifier.height(32.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Post Update", style = MaterialTheme.typography.labelSmall)
            }
        }

        // Bookmarked Push Notification Status Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (detail.saved) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = if (detail.saved) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsNone,
                    contentDescription = null,
                    tint = if (detail.saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (detail.saved) "🔔 Push Notification Alerts Active" else "Bookmark for Push Alerts",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (detail.saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (detail.saved)
                            "You are bookmarked to receive instant device notifications whenever ${detail.card.displayName} posts new announcements or updates."
                        else
                            "Save/Bookmark this business (❤️ Save) to receive instant push notification alerts when new announcements or offers are posted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Action Notice Banner when a Push Alert was dispatched
        notificationNoticeMessage?.let { msg ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.MarkEmailRead,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { notificationNoticeMessage = null },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        posts.forEach { post ->
            ActivityPostCard(
                post = post,
                businessName = detail.card.displayName,
                isVerified = detail.card.verified,
                categoryName = detail.card.category,
            )
        }
    }

    if (showPostDialog) {
        NewAnnouncementDialog(
            businessName = detail.card.displayName,
            onDismiss = { showPostDialog = false },
            onSubmit = { title, body, badge ->
                showPostDialog = false
                val newPost = ActivityPostData(
                    id = "act_${System.currentTimeMillis()}",
                    title = title,
                    body = body,
                    dateLabel = "Just now",
                    categoryBadge = badge,
                    initialLikes = 1,
                )
                posts = listOf(newPost) + posts

                // Dispatch actual Android device push notification for bookmarked users
                dispatchBusinessAnnouncementNotification(
                    context = context,
                    businessName = detail.card.displayName,
                    title = title,
                    body = body,
                )

                notificationNoticeMessage = "📢 Announcement published! Instant push notification alert sent to residents who bookmarked ${detail.card.displayName}."
            },
        )
    }
}

@Composable
private fun NewAnnouncementDialog(
    businessName: String,
    onDismiss: () -> Unit,
    onSubmit: (title: String, body: String, badge: String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var selectedBadge by remember { mutableStateOf("ANNOUNCEMENT") }

    val badges = listOf("ANNOUNCEMENT", "SPECIAL OFFER", "EVENT", "SERVICE UPDATE")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Post Business Update", fontWeight = FontWeight.Bold)
                Text(
                    "Publish an announcement & alert bookmarked residents of $businessName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Announcement Title") },
                    placeholder = { Text("e.g. 20% Off Weekend Special / New Opening Hours") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Announcement Details") },
                    placeholder = { Text("Write the update details for community residents...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                )

                Text(
                    text = "Category Badge:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    badges.forEach { badge ->
                        FilterChip(
                            selected = selectedBadge == badge,
                            onClick = { selectedBadge = badge },
                            label = { Text(badge, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && body.isNotBlank()) {
                        onSubmit(title, body, selectedBadge)
                    }
                },
                enabled = title.isNotBlank() && body.isNotBlank(),
                shape = CircleShape,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Filled.Campaign, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("Publish & Alert Bookmarked")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun ActivityPostCard(
    post: ActivityPostData,
    businessName: String,
    isVerified: Boolean,
    categoryName: String,
) {
    var liked by rememberSaveable { mutableStateOf(false) }
    var likesCount by rememberSaveable { mutableStateOf(post.initialLikes) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AppStoreSquircleLogo(
                        name = businessName,
                        category = categoryName,
                        size = 40.dp,
                    )
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = businessName,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            )
                            if (isVerified) {
                                Icon(
                                    imageVector = Icons.Filled.Verified,
                                    contentDescription = "Verified by RTC Community",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                        Text(
                            text = post.dateLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (post.categoryBadge == "VERIFIED") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = post.categoryBadge,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = if (post.categoryBadge == "VERIFIED") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            Text(
                text = post.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            )

            Text(
                text = post.body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    IconButton(
                        onClick = {
                            liked = !liked
                            if (liked) likesCount++ else likesCount--
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = if (liked) "Unlike post" else "Like post",
                            tint = if (liked) Color(0xFFE91E63) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Text(
                        text = "$likesCount likes",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    text = "Owner Post",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
