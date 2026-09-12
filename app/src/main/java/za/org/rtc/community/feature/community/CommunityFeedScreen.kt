
package za.org.rtc.community.feature.community

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import za.org.rtc.community.ui.components.RtcSkeletonLoader
import za.org.rtc.community.ui.theme.RtcDesignSystem

@Composable
fun CommunityFeedScreen(
    viewModel: CommunityViewModel,
    onPostClick: (String) -> Unit
) {
    val state by viewModel.feedState.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(RtcDesignSystem.BackgroundDark)) {
        // Floating Header with Glassmorphism feel
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(RtcDesignSystem.BackgroundDark.copy(alpha = 0.9f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text("Community Feed", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = RtcDesignSystem.TextPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                FilterPill("For You", true)
                FilterPill("Following", false)
                FilterPill("Trending", false)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 80.dp, bottom = 16.dp)
        ) {
            if (state.initialLoading) {
                items(5) { RtcSkeletonLoader() }
            } else if (state.error != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Error: ${state.error}", color = Color.Red)
                }
            } else {
                items(state.posts) { post ->
                    CommunityPostCard(
                        post = post,
                        onPostClick = { onPostClick(post.id) },
                        onLikeClick = { viewModel.toggleLike(post.id) },
                        onCommentClick = { /* Navigate to comments */ },
                        onRepostClick = { /* Handle repost */ }
                    )
                }
            }
        }
    }
}

@Composable
fun FilterPill(text: String, selected: Boolean) {
    Surface(
        modifier = Modifier.clickable { },
        shape = RoundedCornerShape(16.dp),
        color = if (selected) RtcDesignSystem.PrimaryBrand else RtcDesignSystem.SurfaceDark,
        border = if (!selected) androidx.compose.foundation.border(1.dp, RtcDesignSystem.AccentBorder, RoundedCornerShape(16.dp)) else null
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Color.White else RtcDesignSystem.TextSecondary
        )
    }
}
