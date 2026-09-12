package za.org.rtc.community.feature.support

import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import za.org.rtc.community.feature.explore.DirectoryCard
import za.org.rtc.community.ui.animation.RtcMotionAlertDialog
import za.org.rtc.community.ui.theme.RtcSpacing

@Composable
internal fun HelpCentreScreen() {
    LazyColumn(contentPadding = PaddingValues(RtcSpacing.standard), verticalArrangement = Arrangement.spacedBy(RtcSpacing.small)) {
        item { Text("How can we help?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() }) }
        item { OutlinedTextField(value = "", onValueChange = {}, label = { Text("Search Help Centre") }, leadingIcon = { Icon(Icons.Filled.Search, null) }, modifier = Modifier.fillMaxWidth()) }
        item { DirectoryCard("Using Support", "Start and track requests.", Icons.Filled.SupportAgent) }
        item { DirectoryCard("Community guidelines", "Posting, reporting, and privacy.", Icons.Filled.Forum) }
        item { DirectoryCard("Privacy controls", "Data exports and account deletion.", Icons.Filled.Shield) }
        item { DirectoryCard("Accessibility", "Reading mode, contrast, and text scaling.", Icons.Filled.AccessibilityNew) }
        item { ApprovedExternalLinkCard("Public service portal", "Access the official public service portal.", "https://www.gov.za/") }
    }
}

@Composable
private fun ApprovedExternalLinkCard(title: String, description: String, url: String) {
    val context = LocalContext.current
    var chooserOpen by rememberSaveable { mutableStateOf(false) }
    DirectoryCard(title, description, Icons.Filled.Visibility) { chooserOpen = true }
    if (chooserOpen) {
        RtcMotionAlertDialog(
            onDismissRequest = { chooserOpen = false },
            title = { Text(title) },
            text = { Text("This approved external link opens in the in-app browser by default. You may instead use your device browser.") },
            confirmButton = {
                Button(onClick = {
                    chooserOpen = false
                    CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
                }) { Text("Open in app") }
            },
            dismissButton = {
                TextButton(onClick = {
                    chooserOpen = false
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }) { Text("Use device browser") }
            }
        )
    }
}
