package za.org.rtc.community.feature.community

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import za.org.rtc.community.ui.components.PostCardSkeleton
import za.org.rtc.community.ui.components.PurposefulEmptyState
import za.org.rtc.community.ui.components.SkeletonBox
import za.org.rtc.community.ui.components.skeletonPulse
import za.org.rtc.community.ui.theme.RtcSize
import za.org.rtc.community.ui.theme.RtcSpacing

@Composable
internal fun CommunityPostDetailLoadingState(
    postId: String,
    message: String?,
    sharedModifier: Modifier,
    onRetry: () -> Unit,
) {
    if (message == null && postId.isNotBlank()) {
        LazyColumn(
            contentPadding = PaddingValues(RtcSpacing.standard),
            verticalArrangement = Arrangement.spacedBy(RtcSpacing.small),
        ) {
            item { PostCardSkeleton(modifier = Modifier.fillMaxWidth().then(sharedModifier)) }
            item {
                Spacer(Modifier.height(RtcSpacing.small))
                SkeletonBox(height = RtcSpacing.standard, width = RtcSize.mediaThumbnail)
            }
            items(3) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.Top) {
                        Box(modifier = Modifier.size(RtcSize.avatarCompact).skeletonPulse(shape = CircleShape))
                        Spacer(Modifier.width(RtcSpacing.compact))
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(RtcSpacing.relatedText)) {
                            SkeletonBox(height = RtcSpacing.small, width = RtcSize.mediaThumbnail)
                            SkeletonBox(height = RtcSpacing.small)
                            SkeletonBox(height = RtcSpacing.small, width = RtcSize.adaptiveCardMinWidth)
                        }
                    }
                }
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            PurposefulEmptyState(
                message ?: "This Community post is no longer available.",
                if (postId.isNotBlank()) "Retry" else "Return to Community",
                if (postId.isNotBlank()) onRetry else {},
            )
        }
    }
}
