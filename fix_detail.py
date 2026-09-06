with open("app/src/main/java/za/org/rtc/community/feature/servicecentre/presentation/ServiceCentreBookingDetailScreen.kt", "r") as f:
    content = f.read()

import_statement = "import za.org.rtc.community.ui.navigation.LocalSharedTransitionScope\nimport za.org.rtc.community.ui.navigation.LocalNavAnimatedVisibilityScope\nimport androidx.compose.animation.SharedTransitionScope\nimport androidx.compose.animation.core.spring\nimport androidx.compose.animation.core.Spring\nimport androidx.compose.animation.ExperimentalSharedTransitionApi\n"
import_idx = content.find("import androidx.compose.animation.AnimatedVisibility")
content = content[:import_idx] + import_statement + content[import_idx:]

content = content.replace("@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nfun ServiceCentreBookingDetailRoute", "@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)\n@Composable\nfun ServiceCentreBookingDetailRoute")

setup_local = """
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current
"""
scaffold_idx = content.find("    Scaffold(")
content = content[:scaffold_idx] + setup_local + content[scaffold_idx:]

# The content is a Column inside Scaffold. Let's find:
#     ) { padding ->
#        Column(
#            modifier = Modifier

column_idx = content.find("    ) { padding ->\n        Column(\n            modifier = Modifier")

new_column = """    ) { padding ->
        val sharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
            with(sharedTransitionScope) {
                Modifier.sharedBounds(
                    sharedContentState = rememberSharedContentState(key = "service_booking_${bookingId}"),
                    animatedVisibilityScope = animatedVisibilityScope,
                    boundsTransform = { _, _ -> spring(stiffness = Spring.StiffnessMediumLow) },
                    resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                    renderInOverlayDuringTransition = false,
                    zIndexInOverlay = 1f,
                ).skipToLookaheadSize()
            }
        } else { Modifier }

        Column(
            modifier = Modifier.then(sharedModifier)"""

content = content.replace("    ) { padding ->\n        Column(\n            modifier = Modifier", new_column)

with open("app/src/main/java/za/org/rtc/community/feature/servicecentre/presentation/ServiceCentreBookingDetailScreen.kt", "w") as f:
    f.write(content)
