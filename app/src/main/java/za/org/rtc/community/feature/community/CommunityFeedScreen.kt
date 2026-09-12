
package za.org.rtc.community.feature.community

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import za.org.rtc.community.ui.components.RtcSkeletonLoader

@Composable
fun CommunityFeedScreen(
    viewModel: CommunityViewModel,
    onPostClick: (String) -> Unit
) {
    val state by viewModel.feedState.collectAsState()

    if (state.initialLoading) {
        LazyColumn {
            items(5) { RtcSkeletonLoader() }
        }
    } else if (state.error != null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Error loading feed: ${state.error}", color = Color.Red)
        }
    } else {
        LazyColumn {
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
