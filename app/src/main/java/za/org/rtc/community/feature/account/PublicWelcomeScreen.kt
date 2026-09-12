
package za.org.rtc.community.feature.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import za.org.rtc.community.ui.theme.RtcDesignSystem

@Composable
fun PublicWelcomeScreen(onSignInClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RtcDesignSystem.BackgroundDark),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "RTC Community",
                style = MaterialTheme.typography.displayMedium,
                color = RtcDesignSystem.PrimaryBrand,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "Connect with your neighbors,\n report civic issues, and\n build a better community.",
                style = MaterialTheme.typography.bodyLarge,
                color = RtcDesignSystem.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 24.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = onSignInClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RtcDesignSystem.PrimaryBrand)
            ) {
                Text("Get Started", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            Text(
                text = "By continuing, you agree to our Terms of Service",
                style = MaterialTheme.typography.labelSmall,
                color = RtcDesignSystem.TextSecondary,
                modifier = Modifier.padding(top = 24.dp)
            )
        }
    }
}
