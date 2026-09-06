package za.org.rtc.community.feature.administration.branding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import za.org.rtc.community.core.BrandDensityPreset
import za.org.rtc.community.core.BrandFontFamily
import za.org.rtc.community.core.BrandShapePreset
import za.org.rtc.community.core.HomeImageAspectPreset
import za.org.rtc.community.core.HomeImageContentScale
import za.org.rtc.community.core.HomeImagePlacement
import za.org.rtc.community.core.HomeSection
import za.org.rtc.community.core.LaunchTreatment
import za.org.rtc.community.core.LauncherIconId
import za.org.rtc.community.core.ScreenDensity
import za.org.rtc.community.core.ScreenHeaderStyle
import za.org.rtc.community.core.ThemePreference
import za.org.rtc.community.core.TypographyScalePreset
import za.org.rtc.community.core.TypographyWeightPreset
import za.org.rtc.community.data.ui_config.UiConfigurationRepository
import za.org.rtc.community.ui.components.RtcCard
import za.org.rtc.community.ui.config.LocalRtcUiConfiguration
import za.org.rtc.community.ui.theme.BrandPaletteEngine
import za.org.rtc.community.ui.theme.RtcCommunityTheme
import za.org.rtc.community.ui.theme.RtcSize
import za.org.rtc.community.ui.theme.RtcSpacing

internal fun LazyListScope.brandDesignSection(
    state: BrandExperienceUiState,
    viewModel: BrandExperienceViewModel,
) {
    item {
        EditorCard("Colours", "Validated #RRGGBB seeds generate coherent light and dark Material schemes.") {
            ColourField("Primary", state.configuration.appearance.primarySeed, !state.mutationInFlight) {
                viewModel.updateAppearance { appearance -> appearance.copy(primarySeed = it) }
            }
            ColourField("Accent", state.configuration.appearance.accentSeed, !state.mutationInFlight) {
                viewModel.updateAppearance { appearance -> appearance.copy(accentSeed = it) }
            }
            ColourField("Background", state.configuration.appearance.backgroundSeed, !state.mutationInFlight) {
                viewModel.updateAppearance { appearance -> appearance.copy(backgroundSeed = it) }
            }
            Text(
                if (state.contrastPasses) "Contrast gate: PASS" else "Contrast gate: BLOCKED — adjust colours before saving.",
                color = if (state.contrastPasses) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelLarge,
            )
            Text("Presets", style = MaterialTheme.typography.titleSmall)
            OptionFlow(
                options = listOf("RTC" to Triple("#2EC27E", "#D4AF37", "#0C1013"), "Civic light" to Triple("#006C43", "#806000", "#F5F9F6")),
                label = { it.first },
                selected = { false },
                enabled = !state.mutationInFlight,
                onSelect = { selected ->
                    viewModel.updateAppearance {
                        it.copy(
                            primarySeed = selected.second.first,
                            accentSeed = selected.second.second,
                            backgroundSeed = selected.second.third,
                        )
                    }
                },
            )
        }
    }
    item {
        EditorCard("Typography", "Only approved system families and bounded scales are available; Android accessibility font scaling still applies.") {
            ChoiceRow("Family", BrandFontFamily.entries, state.configuration.typography.fontFamily, viewModel::setFontFamily, !state.mutationInFlight)
            ChoiceRow("Scale", TypographyScalePreset.entries, state.configuration.typography.scalePreset, viewModel::setTypographyScale, !state.mutationInFlight)
            ChoiceRow("Weight", TypographyWeightPreset.entries, state.configuration.typography.weightPreset, viewModel::setTypographyWeight, !state.mutationInFlight)
        }
    }
    item {
        EditorCard("Shape & density", "Presets map to compiled RTC design-system values rather than arbitrary remote dimensions.") {
            ChoiceRow("Shape", BrandShapePreset.entries, state.configuration.appearance.shapePreset, viewModel::setShape, !state.mutationInFlight)
            ChoiceRow("Global density", BrandDensityPreset.entries, state.configuration.appearance.densityPreset, viewModel::setGlobalDensity, !state.mutationInFlight)
        }
    }
    item {
        EditorCard("Welcome", "This is a normal Compose surface and can support richer copy than Android's native splash lifecycle.") {
            EditorTextField("Headline", state.configuration.welcome.headline, viewModel::setWelcomeHeadline, enabled = !state.mutationInFlight)
            EditorTextField("Supporting text", state.configuration.welcome.supportingText, viewModel::setWelcomeSupportingText, minLines = 2, enabled = !state.mutationInFlight)
            EditorTextField("Primary action", state.configuration.welcome.primaryActionLabel, viewModel::setWelcomePrimaryAction, enabled = !state.mutationInFlight)
            EditorTextField("Secondary action", state.configuration.welcome.secondaryActionLabel, viewModel::setWelcomeSecondaryAction, enabled = !state.mutationInFlight)
            ChoiceRow("Treatment", LaunchTreatment.entries, state.configuration.welcome.launchTreatment, viewModel::setLaunchTreatment, !state.mutationInFlight)
        }
    }
    item {
        EditorCard("Splash", "Android SplashScreen remains lifecycle-controlled. Branding follows the published palette; no artificial blocking delay is exposed.") {
            Text("Native splash timing is intentionally not administrator-configurable.", style = MaterialTheme.typography.bodyMedium)
        }
    }
    item {
        EditorCard("App icon", "Android supports a finite set of prepackaged launcher aliases. Uploaded images cannot become arbitrary installed launcher icons.") {
            ChoiceRow("Packaged icon", LauncherIconId.entries, state.configuration.launcher.recommendedIconId, viewModel::setRecommendedLauncherIcon, !state.mutationInFlight)
        }
    }
}

internal fun LazyListScope.brandScreensSection(
    state: BrandExperienceUiState,
    viewModel: BrandExperienceViewModel,
) {
    item {
        EditorCard("Screen presentation", "Header and density choices are bounded consumer settings; Marketplace and Community business logic is unchanged.") {
            ChoiceRow(
                "Screen",
                CustomizableScreen.entries.filterNot { it in setOf(CustomizableScreen.WELCOME, CustomizableScreen.HOME) },
                state.selectedScreen,
                viewModel::selectScreen,
                !state.mutationInFlight,
            )
            val presentation = state.configuration.presentationFor(state.selectedScreen)
            ChoiceRow(
                "Header",
                ScreenHeaderStyle.entries,
                presentation.headerStyle,
                onSelect = { viewModel.setScreenPresentation(state.selectedScreen, header = it) },
                enabled = !state.mutationInFlight,
            )
            ChoiceRow(
                "Density",
                ScreenDensity.entries,
                presentation.density,
                onSelect = { viewModel.setScreenPresentation(state.selectedScreen, density = it) },
                enabled = !state.mutationInFlight,
            )
        }
    }
}

internal fun LazyListScope.brandHomeSection(
    state: BrandExperienceUiState,
    viewModel: BrandExperienceViewModel,
    onPickImage: () -> Unit,
) {
    item {
        EditorCard("Home sections", "Welcome and Help are mandatory. Other compiled sections can be shown, hidden and reordered.") {
            HomeSection.entries.forEach { section ->
                val visible = section in state.configuration.home.sections
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact),
                ) {
                    Checkbox(
                        checked = visible,
                        enabled = !state.mutationInFlight &&
                            section !in za.org.rtc.community.core.HomeLayout.mandatorySections,
                        onCheckedChange = { viewModel.setHomeSectionVisible(section, it) },
                    )
                    Text(section.name.replace('_', ' '), modifier = Modifier.weight(1f))
                    if (visible) {
                        OutlinedButton(onClick = { viewModel.moveHomeSection(section, -1) }, enabled = !state.mutationInFlight) {
                            androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Move earlier")
                        }
                        OutlinedButton(onClick = { viewModel.moveHomeSection(section, 1) }, enabled = !state.mutationInFlight) {
                            androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Move later")
                        }
                    }
                }
            }
        }
    }
    item {
        EditorCard("Home image", "JPEG, PNG or WebP only; maximum 8 MiB and 8192×8192. Published assets are protected from draft cleanup.") {
            if (state.imageUploadWorking) CircularProgressIndicator(modifier = Modifier.size(RtcSize.loadingIndicator))
            state.imageMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Button(
                onClick = onPickImage,
                enabled = !state.imageUploadWorking && !state.mutationInFlight,
            ) { Text("Choose image") }
            val image = state.configuration.home.imageWidget
            if (image != null) {
                ChoiceRow(
                    "Anchor",
                    state.configuration.home.sections,
                    image.anchorSection,
                    onSelect = { viewModel.setHomeImageMetadata(anchor = it) },
                    enabled = !state.mutationInFlight,
                )
                ChoiceRow(
                    "Placement",
                    HomeImagePlacement.entries,
                    image.placement,
                    onSelect = { viewModel.setHomeImageMetadata(placement = it) },
                    enabled = !state.mutationInFlight,
                )
                ChoiceRow(
                    "Aspect",
                    HomeImageAspectPreset.entries,
                    image.aspectPreset,
                    onSelect = { viewModel.setHomeImageMetadata(aspect = it) },
                    enabled = !state.mutationInFlight,
                )
                ChoiceRow(
                    "Fit",
                    HomeImageContentScale.entries,
                    image.contentScale,
                    onSelect = { viewModel.setHomeImageMetadata(contentScale = it) },
                    enabled = !state.mutationInFlight,
                )
                EditorTextField(
                    label = "Alt text",
                    value = image.altText,
                    onValueChange = { viewModel.setHomeImageMetadata(altText = it) },
                    enabled = !state.mutationInFlight,
                )
                EditorTextField(
                    label = "Caption (optional)",
                    value = image.caption.orEmpty(),
                    onValueChange = { viewModel.setHomeImageMetadata(caption = it.ifBlank { null }) },
                    enabled = !state.mutationInFlight,
                )
                OutlinedButton(
                    onClick = viewModel::removeHomeImage,
                    enabled = !state.mutationInFlight,
                ) { Text("Remove from draft") }
            }
        }
    }
}

internal fun LazyListScope.brandPreviewSection(state: BrandExperienceUiState) {
    item {
        CompositionLocalProvider(LocalRtcUiConfiguration provides state.preview) {
            RtcCommunityTheme(ThemePreference.SYSTEM, state.preview, dynamicColor = false) {
                EditorCard("PREVIEW", "Draft preview uses the same runtime theme engine. Opening preview never publishes.") {
                    AssistChip(onClick = {}, label = { Text("PREVIEW ONLY") })
                    Text(state.preview.welcome.headline, style = MaterialTheme.typography.headlineSmall)
                    Text(state.preview.welcome.supportingText, style = MaterialTheme.typography.bodyLarge)
                    RtcCard {
                        Text("Home presentation", style = MaterialTheme.typography.titleMedium)
                        Text(state.preview.home.sections.joinToString(" • ") { it.name.replace('_', ' ') }, style = MaterialTheme.typography.bodySmall)
                        state.preview.home.imageWidget?.let { widget ->
                            Text("Image: ${widget.placement.name.lowercase()} ${widget.anchorSection.name.lowercase()}", style = MaterialTheme.typography.bodySmall)
                            Text("Alt: ${widget.altText}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

internal fun LazyListScope.brandPublishSection(
    state: BrandExperienceUiState,
    viewModel: BrandExperienceViewModel,
) {
    item {
        EditorCard("Draft", "Saving creates a protected immutable draft based on the current published version.") {
            EditorTextField("Draft reason", state.draftReason, viewModel::setDraftReason, minLines = 2, enabled = !state.mutationInFlight)
            Text("Validation: ${if (state.validatorPasses) "PASS" else "BLOCKED"} · Contrast: ${if (state.contrastPasses) "PASS" else "BLOCKED"}")
            Button(onClick = viewModel::saveDraft, enabled = state.canSaveDraft) { Text("Save draft") }
            state.editor.draftId?.let { Text("Draft ready for publication review.", style = MaterialTheme.typography.bodySmall) }
        }
    }
    item {
        EditorCard("Publish", "Publication is server-authorized and requires an explicit reason and confirmation phrase.") {
            EditorTextField("Publish reason", state.publishReason, viewModel::setPublishReason, minLines = 2, enabled = !state.mutationInFlight)
            EditorTextField("Confirmation", state.publishConfirmation, viewModel::setPublishConfirmation, enabled = !state.mutationInFlight)
            Text("Type ${UiConfigurationRepository.PUBLISH_CONFIRMATION} exactly.", style = MaterialTheme.typography.bodySmall)
            Button(onClick = viewModel::publish, enabled = state.canPublish) { Text("Publish configuration") }
            state.editor.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

internal fun LazyListScope.brandHistorySection(
    state: BrandExperienceUiState,
    viewModel: BrandExperienceViewModel,
) {
    item { BrandHistoryPanel(state, viewModel) }
}

@Composable
private fun BrandHistoryPanel(state: BrandExperienceUiState, viewModel: BrandExperienceViewModel) {
    var reason by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    EditorCard("Version history", "Revert creates and publishes a new version; existing history is never overwritten.") {
        EditorTextField("Restore reason", reason, { reason = it.take(500) }, minLines = 2, enabled = !state.mutationInFlight)
        EditorTextField("Confirmation", confirmation, { confirmation = it.take(80) }, enabled = !state.mutationInFlight)
        Text("Type ${UiConfigurationRepository.PUBLISH_CONFIRMATION} exactly before restoring.", style = MaterialTheme.typography.bodySmall)
        if (state.historyLoading) CircularProgressIndicator(modifier = Modifier.size(RtcSize.loadingIndicator))
        state.history.forEach { version ->
            HorizontalDivider()
            Text("${version.state} · ${version.createdAt}", style = MaterialTheme.typography.titleSmall)
            Text(version.createdReason, style = MaterialTheme.typography.bodySmall)
            if (version.state == "DRAFT") {
                OutlinedButton(
                    onClick = { viewModel.resumeDraft(version) },
                    enabled = !state.mutationInFlight,
                ) { Text("Resume draft") }
            }
            if (version.state in setOf("PUBLISHED", "SUPERSEDED")) {
                OutlinedButton(
                    onClick = { viewModel.restore(version.versionId, reason, confirmation) },
                    enabled = !state.mutationInFlight &&
                        reason.trim().length in 3..500 &&
                        confirmation == UiConfigurationRepository.PUBLISH_CONFIRMATION,
                ) { Text("Restore as new published version") }
            }
        }
        OutlinedButton(onClick = viewModel::refreshHistory) { Text("Refresh history") }
    }
}

@Composable
private fun EditorCard(title: String, supporting: String, content: @Composable () -> Unit) {
    RtcCard(protected = true) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun ColourField(
    label: String,
    value: String,
    enabled: Boolean = true,
    onValueChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val preview = runCatching { BrandPaletteEngine.parseHex(value) }.getOrDefault(Color.Transparent)
        Box(
            modifier = Modifier
                .size(RtcSize.actionIcon)
                .background(preview)
                .semantics { contentDescription = "$label colour preview" },
        )
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it.take(7).uppercase()) },
            label = { Text("$label #RRGGBB") },
            singleLine = true,
            modifier = Modifier.weight(1f),
            enabled = enabled,
        )
    }
}

@Composable
private fun EditorTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    minLines: Int = 1,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        minLines = minLines,
        enabled = enabled,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceRow(
    label: String,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    enabled: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        OptionFlow(options, { optionLabel(it) }, { it == selected }, onSelect, enabled)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> OptionFlow(
    options: List<T>,
    label: (T) -> String,
    selected: (T) -> Boolean,
    onSelect: (T) -> Unit,
    enabled: Boolean = true,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(RtcSpacing.compact),
        verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact),
    ) {
        options.forEach { option ->
            AssistChip(
                onClick = { onSelect(option) },
                label = { Text(if (selected(option)) "✓ ${label(option)}" else label(option)) },
                enabled = enabled,
            )
        }
    }
}

private fun optionLabel(value: Any?): String = when (value) {
    is Enum<*> -> value.name.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() }
    else -> value.toString()
}

private fun za.org.rtc.community.core.GlobalUiConfiguration.presentationFor(screen: CustomizableScreen) = when (screen) {
    CustomizableScreen.COMMUNITY -> screens.community
    CustomizableScreen.EXPLORE -> screens.explore
    CustomizableScreen.SUPPORT -> screens.support
    CustomizableScreen.ACCOUNT -> screens.account
    CustomizableScreen.NOTIFICATIONS -> screens.notifications
    CustomizableScreen.SEARCH -> screens.search
    CustomizableScreen.HELP -> screens.help
    CustomizableScreen.MARKETPLACE -> screens.marketplace
    CustomizableScreen.MARKETPLACE_SEARCH -> screens.marketplaceSearch
    CustomizableScreen.MARKETPLACE_MAP -> screens.marketplaceMap
    CustomizableScreen.MARKETPLACE_ACCOUNT -> screens.marketplaceAccount
    CustomizableScreen.WELCOME, CustomizableScreen.HOME -> screens.account
}
