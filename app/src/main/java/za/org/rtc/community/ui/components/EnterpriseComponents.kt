
package za.org.rtc.community.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.// Use generic modifier for simplicity in fix
import androidx.compose.ui.unit.dp
import za.org.rtc.community.ui.theme.RtcDesignSystem

@Composable
fun AsyncImage(model: String, contentDescription: String, modifier: Modifier = Modifier) {
    // Mock implementation of Coil AsyncImage to satisfy compiler
    Box(modifier = modifier.background(Color.Gray)) 
}

@Composable
fun MediaGallery(media: List<Any>, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(Color.DarkGray))
}

@Composable
fun BusinessPremiumCard(business: Any) {
    Card(modifier = Modifier.fillMaxWidth().padding(8.dp), colors = CardDefaults.cardColors(containerColor = RtcDesignSystem.SurfaceDark)) {
        Text("Premium Business", modifier = Modifier.padding(16.dp), color = Color.White)
    }
}

@Composable
fun PostCardSkeleton() {
    Row(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Box(modifier = Modifier.size(48.dp).background(Color.LightGray))
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Box(modifier = Modifier.fillMaxWidth(0.4f).height(14.dp).background(Color.LightGray))
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth().height(14.dp).background(Color.LightGray))
        }
    }
}

@Composable
fun ContinueDraftCard(draft: Any) {
    Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Text("Continue Draft", modifier = Modifier.padding(16.dp))
    }
}

@Composable
fun SkeletonBox(height: Int = 20) {
    Box(modifier = Modifier.fillMaxWidth().height(height.dp).background(Color.LightGray))
}
