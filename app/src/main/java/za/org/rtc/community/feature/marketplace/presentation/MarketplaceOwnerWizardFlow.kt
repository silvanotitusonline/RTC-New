
package za.org.rtc.community.feature.marketplace.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import za.org.rtc.community.ui.theme.RtcDesignSystem

@Composable
fun MarketplaceOwnerWizardFlow(
    viewModel: MarketplaceOwnerViewModel,
    onComplete: () -> Unit
) {
    var currentStep by remember { mutableStateOf(0) }
    
    Column(modifier = Modifier.fillMaxSize().background(RtcDesignSystem.BackgroundDark).padding(16.dp)) {
        Text("Business Setup", style = MaterialTheme.typography.headlineMedium, color = RtcDesignSystem.TextPrimary)
        LinearProgressIndicator(
            progress = (currentStep + 1) / 3f, 
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            color = RtcDesignSystem.PrimaryBrand
        )

        Box(modifier = Modifier.weight(1f)) {
            when (currentStep) {
                0 -> BusinessIdentityStep(viewModel)
                1 -> BusinessLocationStep(viewModel)
                2 -> BusinessMediaStep(viewModel)
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            if (currentStep > 0) {
                TextButton(onClick = { currentStep-- }) { Text("Back", color = Color.White) }
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }

            Button(
                onClick = { 
                    if (currentStep < 2) currentStep++ else {
                        viewModel.submitBusiness()
                        onComplete()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = RtcDesignSystem.PrimaryBrand)
            ) {
                Text(if (currentStep < 2) "Next" else "Finish")
            }
        }
    }
}

@Composable
fun BusinessIdentityStep(viewModel: MarketplaceOwnerViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = viewModel.businessName,
            onValueChange = { viewModel.updateName(it) },
            label = { Text("Business Name") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = viewModel.businessDescription,
            onValueChange = { viewModel.updateDescription(it) },
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun BusinessLocationStep(viewModel: MarketplaceOwnerViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Business Location", color = RtcDesignSystem.TextPrimary, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(12.dp))
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(RtcDesignSystem.SurfaceDark)
                .clickable { viewModel.pickLocation() },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = RtcDesignSystem.PrimaryBrand, modifier = Modifier.size(48.dp))
                Text("Tap to set location on map", color = RtcDesignSystem.TextSecondary)
            }
        }
        
        if (viewModel.selectedLocation != null) {
            Text(
                "Address: ${viewModel.selectedLocation!!.address}", 
                color = RtcDesignSystem.TextPrimary, 
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

@Composable
fun BusinessMediaStep(viewModel: MarketplaceOwnerViewModel) {
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Business Branding", color = RtcDesignSystem.TextPrimary, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(32.dp))

        Box(
            modifier = Modifier
                .size(150.dp)
                .clip(CircleShape)
                .background(RtcDesignSystem.SurfaceDark)
                .clickable { viewModel.uploadLogo() },
            contentAlignment = Alignment.Center
        ) {
            if (viewModel.logoUrl == null) {
                Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = RtcDesignSystem.TextSecondary, modifier = Modifier.size(48.dp))
            } else {
                Text("Logo Uploaded", color = RtcDesignSystem.TextPrimary)
            }
        }
        
        Text("Upload business logo", modifier = Modifier.padding(top = 16.dp), color = RtcDesignSystem.TextSecondary)
    }
}
