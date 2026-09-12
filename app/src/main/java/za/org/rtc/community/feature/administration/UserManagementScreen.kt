package za.org.rtc.community.feature.administration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import za.org.rtc.community.app.RtcViewModel
import za.org.rtc.community.ui.components.RtcCard
import za.org.rtc.community.ui.components.RtcSectionHeader
import za.org.rtc.community.ui.theme.RtcSpacing
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Verified-account lookup and role assignment surface.
 *
 * Lookup is deliberately exact-match and every attempt is recorded on the
 * privacy audit trail by the repository layer; nothing here bypasses that.
 */
@Composable
fun UserManagementScreen(
    viewModel: RtcViewModel = hiltViewModel(),
) {
    val account by viewModel.accessManagedAccount.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(RtcSpacing.cardPadding),
        verticalArrangement = Arrangement.spacedBy(RtcSpacing.cardGap),
    ) {
        RtcSectionHeader(
            title = "Account lookup",
            subtitle = "Search a verified account by exact email address.",
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email address") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        androidx.compose.material3.Button(
            onClick = { viewModel.searchAccessManagedAccount(email.trim()) },
            enabled = email.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Look up account")
        }

        val found = account
        if (found == null) {
            RtcCard {
                Text(
                    text = "No account selected",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Search results appear here once an exact match is confirmed.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            RtcCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = found.email,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Role: " + (found.effectiveRole ?: "unassigned"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
