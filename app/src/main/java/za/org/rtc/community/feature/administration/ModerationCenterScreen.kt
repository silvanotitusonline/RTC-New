
package za.org.rtc.community.feature.administration

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import za.org.rtc.community.core.FlaggedPost
import za.org.rtc.community.ui.theme.RtcDesignSystem

@Composable
fun ModerationCenterScreen() {
    val flaggedPosts = remember { 
        listOf(
            FlaggedPost("1", "UserA", "Example toxic content", "Hate Speech"),
            FlaggedPost("2", "UserB", "Another spam post", "Spam")
        ) 
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Moderation Queue", style = MaterialTheme.typography.headlineMedium, color = RtcDesignSystem.TextPrimary)
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
                Text(post.content, color = RtcDesignSystem.TextSecondary)
                Text("Reason: ${post.flagReason}", color = Color.Red)
            }
            Row {
                Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = Color.Green)) { Text("Approve") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("Delete") }
            }
        }
    }
}
