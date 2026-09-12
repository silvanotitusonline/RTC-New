from pathlib import Path
import re


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


def sub_once(text: str, pattern: str, replacement: str, label: str) -> str:
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one regex match, found {count}")
    return updated


# --- Frontend: eligible-assignee picker -------------------------------------------------------
components_path = Path("app/src/main/java/za/org/rtc/community/feature/administration/AdminWorkspaceComponents.kt")
components = components_path.read_text()
components = replace_once(
    components,
    "import androidx.compose.material3.CircularProgressIndicator\n",
    "import androidx.compose.material3.CircularProgressIndicator\n"
    "import androidx.compose.material3.DropdownMenuItem\n"
    "import androidx.compose.material3.ExposedDropdownMenu\n"
    "import androidx.compose.material3.ExposedDropdownMenuAnchorType\n"
    "import androidx.compose.material3.ExposedDropdownMenuBox\n"
    "import androidx.compose.material3.ExposedDropdownMenuDefaults\n"
    "import androidx.compose.material3.ExperimentalMaterial3Api\n",
    "material3 picker imports",
)
components = replace_once(
    components,
    "    onReadyForReview: (String) -> Unit,\n    onReassign: (String, String) -> Unit,\n    onOpen: () -> Unit,\n",
    "    onReadyForReview: (String) -> Unit,\n"
    "    eligibleAssignees: List<AdminEligibleAssignee>,\n"
    "    assigneesLoading: Boolean,\n"
    "    assigneesError: String?,\n"
    "    onLoadEligibleAssignees: () -> Unit,\n"
    "    onRetryEligibleAssignees: () -> Unit,\n"
    "    onClearEligibleAssignees: () -> Unit,\n"
    "    onReassign: (String, String) -> Unit,\n"
    "    onOpen: () -> Unit,\n",
    "work item picker parameters",
)
components = replace_once(
    components,
    "    var reassignOpen by rememberSaveable(item.id) { mutableStateOf(false) }\n\n    Card(\n",
    "    var reassignOpen by rememberSaveable(item.id) { mutableStateOf(false) }\n\n"
    "    fun openReassignPicker() {\n"
    "        reassignOpen = true\n"
    "        onLoadEligibleAssignees()\n"
    "    }\n\n"
    "    Card(\n",
    "open picker helper",
)
components = components.replace(
    "if (canReassign) TextButton(onClick = { reassignOpen = true }) { Text(\"Reassign\") }",
    "if (canReassign) TextButton(onClick = { openReassignPicker() }) { Text(\"Reassign\") }",
)
components = components.replace(
    "onClick = { reassignOpen = true },\n                    modifier = Modifier.align(Alignment.End),",
    "onClick = { openReassignPicker() },\n                    modifier = Modifier.align(Alignment.End),",
)
components = replace_once(
    components,
    "        WorkItemReassignDialog(\n"
    "            onConfirm = { ownerId, reason -> onReassign(ownerId, reason); reassignOpen = false },\n"
    "            onDismiss = { reassignOpen = false },\n"
    "        )\n",
    "        WorkItemReassignDialog(\n"
    "            assignees = eligibleAssignees,\n"
    "            isLoading = assigneesLoading,\n"
    "            errorMessage = assigneesError,\n"
    "            onRetry = onRetryEligibleAssignees,\n"
    "            onConfirm = { ownerId, reason ->\n"
    "                onReassign(ownerId, reason)\n"
    "                onClearEligibleAssignees()\n"
    "                reassignOpen = false\n"
    "            },\n"
    "            onDismiss = {\n"
    "                onClearEligibleAssignees()\n"
    "                reassignOpen = false\n"
    "            },\n"
    "        )\n",
    "wire picker dialog state",
)
old_dialog_pattern = r"@Composable\nprivate fun WorkItemReassignDialog\(.*?\n\}\n\n@Composable\ninternal fun AdminWorkspaceMetricTile"
new_dialog = '''@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkItemReassignDialog(
    assignees: List<AdminEligibleAssignee>,
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedUserId by rememberSaveable { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var reason by rememberSaveable { mutableStateOf("") }
    val selectedAssignee = assignees.firstOrNull { it.userId == selectedUserId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reassign work item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                Text(
                    "Choose an eligible staff member. Only accounts returned by the protected server role check can be selected.",
                    style = MaterialTheme.typography.bodyMedium,
                )

                when {
                    isLoading -> Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.widthIn(max = 24.dp))
                        Text("Loading eligible staff…", style = MaterialTheme.typography.bodySmall)
                    }

                    errorMessage != null -> Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(RtcSpacing.compact),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                errorMessage,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            TextButton(onClick = onRetry) { Text("Retry") }
                        }
                    }

                    assignees.isEmpty() -> Text(
                        "No eligible staff accounts are currently available for this work item.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    else -> ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                    ) {
                        OutlinedTextField(
                            value = selectedAssignee?.let {
                                "${it.displayName} · ${it.role.replace('_', ' ').lowercase().replaceFirstChar(Char::titlecase)}"
                            }.orEmpty(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Assign to") },
                            placeholder = { Text("Select eligible staff") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                            singleLine = true,
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                        ) {
                            assignees.forEach { assignee ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(assignee.displayName, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                assignee.role.replace('_', ' ').lowercase().replaceFirstChar(Char::titlecase),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedUserId = assignee.userId
                                        expanded = false
                                    },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reassignment reason") },
                    supportingText = { Text("Required for the assignment audit trail") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { selectedUserId?.let { onConfirm(it, reason.trim()) } },
                enabled = selectedUserId != null && reason.trim().length >= 3 && !isLoading,
            ) { Text("Reassign") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun AdminWorkspaceMetricTile'''
components = sub_once(components, old_dialog_pattern, new_dialog, "reassign dialog")
components_path.write_text(components)

workspace_path = Path("app/src/main/java/za/org/rtc/community/feature/administration/AdminWorkspace.kt")
workspace = workspace_path.read_text()
workspace = replace_once(
    workspace,
    "        items(visibleWorkItems, key = { it.id }) { workItem ->\n"
    "            OperationsWorkItemCard(\n",
    "        items(visibleWorkItems, key = { it.id }) { workItem ->\n"
    "            val eligibleAssignees = dashboardState.eligibleAssigneesByWorkItem[workItem.id].orEmpty()\n"
    "            val assigneesLoading = dashboardState.assigneeLoadingWorkItemId == workItem.id\n"
    "            val assigneesError = dashboardState.assigneeErrorByWorkItem[workItem.id]\n"
    "            OperationsWorkItemCard(\n",
    "workspace picker local state",
)
workspace = replace_once(
    workspace,
    "                onReadyForReview = { note -> viewModel.markOperationsWorkReadyForReview(workItem.id, note) },\n"
    "                onReassign = { ownerId, reason -> viewModel.reassignOperationsWorkItem(workItem.id, ownerId, reason) },\n",
    "                onReadyForReview = { note -> viewModel.markOperationsWorkReadyForReview(workItem.id, note) },\n"
    "                eligibleAssignees = eligibleAssignees,\n"
    "                assigneesLoading = assigneesLoading,\n"
    "                assigneesError = assigneesError,\n"
    "                onLoadEligibleAssignees = { dashboardViewModel.loadEligibleAssignees(workItem.id) },\n"
    "                onRetryEligibleAssignees = { dashboardViewModel.retryEligibleAssignees(workItem.id) },\n"
    "                onClearEligibleAssignees = { dashboardViewModel.invalidateEligibleAssignees(workItem.id) },\n"
    "                onReassign = { ownerId, reason -> viewModel.reassignOperationsWorkItem(workItem.id, ownerId, reason) },\n",
    "workspace picker callbacks",
)
workspace_path.write_text(workspace)


# --- Security: fail closed when there is no verified server metadata --------------------------
repo_path = Path("app/src/main/java/za/org/rtc/community/data/RtcRepository.kt")
repo = repo_path.read_text()
repo = replace_once(
    repo,
    "import kotlinx.serialization.json.booleanOrNull\n",
    "import kotlinx.serialization.json.JsonObject\nimport kotlinx.serialization.json.booleanOrNull\n",
    "JsonObject import",
)
repo = replace_once(
    repo,
    "import za.org.rtc.community.feature.community.CommunityMockData\n",
    "import za.org.rtc.community.feature.community.CommunityMockData\n"
    "import za.org.rtc.community.feature.administration.security.hasServerAdminClaim\n",
    "admin claim resolver import",
)
repo = replace_once(
    repo,
    "data class AdministratorTotpEnrollment(\n"
    "    val factorId: String,\n"
    "    val secret: String,\n"
    "    val uri: String,\n"
    ")\n\n@Singleton\nclass RtcRepository",
    "data class AdministratorTotpEnrollment(\n"
    "    val factorId: String,\n"
    "    val secret: String,\n"
    "    val uri: String,\n"
    ")\n\n"
    "/** Local cache is never authoritative for staff privilege. */\n"
    "internal fun resolveLocalRestoredRole(cachedEmail: String, cachedRole: String?): UserRole {\n"
    "    if (cachedEmail.isBlank()) return UserRole.RESIDENT_A\n"
    "    val parsed = cachedRole?.let { runCatching { UserRole.valueOf(it) }.getOrNull() }\n"
    "    return parsed?.takeUnless(UserRole::isStaff) ?: UserRole.RESIDENT_A\n"
    "}\n\n"
    "/** SYSTEM_ADMIN is a server-managed app_metadata capability, never an email or user_metadata convention. */\n"
    "internal fun resolveVerifiedServerRole(appMetadata: JsonObject?, resolvedRoles: List<UserRole>): UserRole {\n"
    "    if (hasServerAdminClaim(appMetadata)) return UserRole.SYSTEM_ADMIN\n"
    "    return resolvedRoles.filterNot { it == UserRole.SYSTEM_ADMIN }.singleOrNull() ?: UserRole.RESIDENT_A\n"
    "}\n\n"
    "@Singleton\nclass RtcRepository",
    "session role resolvers",
)
repo = sub_once(
    repo,
    r"        val cached = database\.cachedSessionDao\(\)\.getActiveSession\(\)\n        if \(cached != null\) \{.*?            return@runCatching true\n        \}\n        false\n    \}",
    '''        val cached = database.cachedSessionDao().getActiveSession()
        if (cached != null) {
            val existingProfile = database.cachedUserProfileDao().getProfileByEmail(cached.email)
            val restoredRole = resolveLocalRestoredRole(cached.email, existingProfile?.role)
            _session.value = RtcSession(
                id = cached.userId,
                displayName = existingProfile?.displayName
                    ?: cached.email.substringBefore("@").replaceFirstChar { it.uppercase() },
                role = restoredRole,
                authority = SessionAuthority.SUPABASE_AUTH,
                authenticatedEmail = cached.email,
                handle = "@${cached.email.substringBefore("@").lowercase().replace(Regex("[^a-z0-9_]"), "")}",
                avatarUrl = existingProfile?.avatarUrl,
                bio = existingProfile?.bio.orEmpty(),
                interests = existingProfile?.interestsJson?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList(),
                onboardingComplete = true,
                administratorMfaStatus = AdministratorMfaStatus.NOT_REQUIRED,
            )
            refreshLiveContent()
            return@runCatching true
        }
        false
    }''',
    "cached session restoration",
)
repo = sub_once(
    repo,
    r"    suspend fun signInWithEmail\(email: String, password: String\): Result<Unit> = runCatching \{.*?\n    \}\n\n    suspend fun signInWithGoogleIdToken",
    '''    suspend fun signInWithEmail(email: String, password: String): Result<Unit> = runCatching {
        val cleanEmail = email.trim()
        supabase.auth.signInWith(Email) {
            this.email = cleanEmail
            this.password = password
        }
        supabase.auth.startAutoRefreshForCurrentSession()
        hydrateSupabaseSession()

        val current = _session.value
        database.cachedSessionDao().upsertSession(
            CachedSessionEntity(
                userId = current.id,
                email = current.authenticatedEmail ?: cleanEmail,
                isLoggedIn = true,
                sessionJson = "",
                updatedAtEpochMillis = System.currentTimeMillis(),
            )
        )
        recordPrivacyAnalyticsAppActivity()
        refreshLiveContent()
        enqueueUploadRecovery()
    }

    suspend fun signInWithGoogleIdToken''',
    "email sign-in fallback removal",
)
repo = replace_once(
    repo,
    "        val userId = \"user_${UUID.nameUUIDFromBytes(cleanEmail.toByteArray())}\"\n"
    "        val isAdminEmail = cleanEmail.equals(\"SilvanoTitusOnline@gmail.com\", ignoreCase = true) ||\n"
    "                cleanEmail.startsWith(\"admin\", ignoreCase = true) ||\n"
    "                cleanEmail.contains(\"admin@\", ignoreCase = true)\n"
    "        val targetRole = if (isAdminEmail) UserRole.SYSTEM_ADMIN else UserRole.RESIDENT_A\n",
    "        val userId = \"user_${UUID.nameUUIDFromBytes(cleanEmail.toByteArray())}\"\n"
    "        val targetRole = UserRole.RESIDENT_A\n",
    "signup email role inference",
)
repo = replace_once(
    repo,
    "            handle = if (isAdminEmail) \"@silvano_admin\" else \"@${cleanEmail.substringBefore(\"@\").lowercase().replace(Regex(\"[^a-z0-9_]\"), \"\")}\",\n",
    "            handle = \"@${cleanEmail.substringBefore(\"@\").lowercase().replace(Regex(\"[^a-z0-9_]\"), \"\")}\",\n",
    "signup admin handle inference",
)
repo = sub_once(
    repo,
    r"    private suspend fun hydrateSupabaseSession\(\) \{\n        val user = supabase\.auth\.currentUserOrNull\(\) \?: error\(\"A verified Supabase session is required\.\"\)\n        val email = user\.email \?: \"resident@rtc\.community\"\n.*?        val resolvedRoles = runCatching \{\n            supabase\.from\(\"user_roles\"\)\n                \.select \{ filter \{ eq\(\"user_id\", user\.id\) \} \}\n                \.decodeList<UserRoleRow>\(\)\n                \.map \{ mapSupabaseRole\(it\.role\) \}\n                \.distinct\(\)\n        \}\.getOrDefault\(emptyList\(\)\)\n\n        val isAdmin = .*?\n        val role = .*?\n        val displayName = user\.userMetadata\?\.get\(\"full_name\"\)\?\.jsonPrimitive\?\.contentOrNull\n            \?\.takeIf\(String::isNotBlank\)\n            \?: .*?\n",
    '''    private suspend fun hydrateSupabaseSession() {
        val user = supabase.auth.currentUserOrNull() ?: error("A verified Supabase session is required.")
        val email = user.email ?: "resident@rtc.community"

        val resolvedRoles = runCatching {
            supabase.from("user_roles")
                .select { filter { eq("user_id", user.id) } }
                .decodeList<UserRoleRow>()
                .map { mapSupabaseRole(it.role) }
                .distinct()
        }.getOrDefault(emptyList())

        val role = resolveVerifiedServerRole(user.appMetadata, resolvedRoles)
        val isAdmin = role == UserRole.SYSTEM_ADMIN
        val displayName = user.userMetadata?.get("full_name")?.jsonPrimitive?.contentOrNull
            ?.takeIf(String::isNotBlank)
            ?: email.substringBefore("@")
''',
    "verified session privilege resolution",
)
repo = repo.replace(
    "            handle = if (isAdminEmail) \"@silvano_admin\" else \"@${email.substringBefore(\"@\").lowercase().replace(Regex(\"[^a-z0-9_]\"), \"\")}\",",
    "            handle = \"@${email.substringBefore(\"@\").lowercase().replace(Regex(\"[^a-z0-9_]\"), \"\")}\",",
)
for forbidden in (
    "isAdminEmail",
    'contains("admin@"',
    'startsWith("admin"',
    "SilvanoTitusOnline@gmail.com",
    "isAdminUserClaim",
    "userRoleClaim",
):
    if forbidden in repo:
        raise RuntimeError(f"RtcRepository still contains forbidden privilege heuristic: {forbidden}")
repo_path.write_text(repo)

print("Remaining admin hardening patch applied successfully.")
