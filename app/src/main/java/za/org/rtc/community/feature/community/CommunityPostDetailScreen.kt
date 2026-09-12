
package za.org.rtc.community.feature.community

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import za.org.rtc.community.ui.components.RtcSkeletonLoader

@Composable
fun CommunityPostDetailScreen(
    postId: String,
    viewModel: CommunityViewModel
) {
    val state by viewModel.detailState.collectAsState()

    LaunchedEffect(postId) {
        viewModel.loadPostDetail(postId)
    }

    if (state.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (state.post == null) {
        Text("Post not found")
    } else {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(state.post!!.authorName, style = MaterialTheme.typography.headlineSmall)
            Text(state.post!!.body, style = MaterialTheme.typography.bodyLarge)
            // Add other detail components here
        }
    }
}
