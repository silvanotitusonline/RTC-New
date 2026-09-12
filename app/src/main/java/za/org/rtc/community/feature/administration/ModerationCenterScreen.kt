
package za.org.rtc.community.feature.administration

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import za.org.rtc.community.ui.theme.RtcDesignSystem

@Composable
fun ModerationCenterScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Moderation Queue", style = MaterialTheme.typography.headlineMedium, color = RtcDesignSystem.TextPrimary)
        Text("Review flagged content and manage community trust", style = MaterialTheme.typography.bodySmall, color = RtcDesignSystem.TextSecondary)
        
        Spacer(modifier = Modifier.height(24.dp))

        LazyColumn {
            items(flaggedPosts) { post ->
                ModerationItem(post)
            }
        }
    }
}

@Composable
fun ModerationItem(post: FlaggedPost) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = RtcDesignSystem.SurfaceDark)
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(post.author, color = RtcDesignSystem.TextPrimary, fontWeight = FontWeight.Bold)
                Text(post.content, color = RtcDesignSystem.TextSecondary, maxLines = 2)
                Text("Reason: ${post.flagReason}", color = Color.Red, style = MaterialTheme.typography.labelSmall)
            }
            
            Row {
                Button(onClick = { /* Approve */ }, colors = ButtonDefaults.buttonColors(containerColor = Color.Green)) {
                    Text("Approve")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { /* Delete */ }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
                    Text("Delete")
                }
            }
        }
    }
}
