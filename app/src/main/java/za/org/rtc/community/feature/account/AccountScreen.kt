
package za.org.rtc.community.feature.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import za.org.rtc.community.ui.theme.RtcDesignSystem

@Composable
fun AccountScreen(viewModel: AccountViewModel) {
    val state by viewModel.accountState.collectAsState()
    var showVisibilityDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(RtcDesignSystem.BackgroundDark)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                Text("Account Settings", style = MaterialTheme.typography.headlineMedium, color = RtcDesignSystem.TextPrimary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(24.dp))
            }

            item { SectionHeader("Privacy & Security") }
            item {
                SettingRow(
                    label = "Profile Visibility",
                    value = state.profileVisibility,
                    onClick = { showVisibilityDialog = true }
                )
                SettingRow(
                    label = "Two-Factor Authentication",
                    value = if (state.mfaEnabled) "Enabled" else "Disabled",
                    onClick = { viewModel.updateMfaStatus(!state.mfaEnabled) }
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }

            item { SectionHeader("Notifications") }
            item {
                SwitchSetting(
                    label = "Push Notifications",
                    checked = state.notificationsEnabled,
                    onCheckedChange = { viewModel.toggleNotifications(it) }
                )
            }

            item { Spacer(modifier = Modifier.height(40.dp)) }

            item {
                Button(
                    onClick = { viewModel.requestAccountDeletion() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Delete Account", color = Color.White)
                }
            }
        }

        if (showVisibilityDialog) {
            AlertDialog(
                onDismissRequest = { showVisibilityDialog = false },
                title = { Text("Profile Visibility") },
                text = { Text("Choose who can see your profile.") },
                confirmButton = {
                    TextButton(onClick = { 
                        viewModel.updateProfileVisibility("PRIVATE")
                        showVisibilityDialog = false 
                    }) { Text("Private") }
                },
                dismissButton = {
                    TextButton(onClick = { 
                        viewModel.updateProfileVisibility("PUBLIC")
                        showVisibilityDialog = false 
                    }) { Text("Public") }
                }
            )
        }
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(text = text, style = MaterialTheme.typography.labelLarge, color = RtcDesignSystem.TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
fun SettingRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = RtcDesignSystem.TextPrimary)
        Text(value, color = RtcDesignSystem.TextSecondary)
    }
}

@Composable
fun SwitchSetting(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = RtcDesignSystem.TextPrimary)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
