with open("app/src/main/java/za/org/rtc/community/feature/servicecentre/presentation/ServiceCentreBookingHubScreen.kt", "r") as f:
    content = f.read()

import_statement = "import za.org.rtc.community.ui.navigation.LocalSharedTransitionScope\nimport za.org.rtc.community.ui.navigation.LocalNavAnimatedVisibilityScope\nimport androidx.compose.animation.SharedTransitionScope\nimport androidx.compose.animation.core.spring\nimport androidx.compose.animation.core.Spring\nimport androidx.compose.animation.ExperimentalSharedTransitionApi\n"

import_idx = content.find("import za.org.rtc.community.navigation.RtcRoute")
content = content[:import_idx] + import_statement + content[import_idx:]

content = content.replace("@Composable\nfun ServiceCentreBookingHubRoute", "@OptIn(ExperimentalSharedTransitionApi::class)\n@Composable\nfun ServiceCentreBookingHubRoute")

# Add the Local variables
setup_local = """
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current
"""
column_idx = content.find("    Column(Modifier.fillMaxSize()) {")
content = content[:column_idx] + setup_local + content[column_idx:]

card_call_old = """                    ServiceCentreBookingCard(
                        booking = booking,
                        onOpen = { onNavigate(RtcRoute.serviceCentreBooking(booking.id)) },"""

card_call_new = """                    val sharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                        with(sharedTransitionScope) {
                            Modifier.sharedBounds(
                                sharedContentState = rememberSharedContentState(key = "service_booking_${booking.id}"),
                                animatedVisibilityScope = animatedVisibilityScope,
                                boundsTransform = { _, _ -> spring(stiffness = Spring.StiffnessMediumLow) },
                                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                            )
                        }
                    } else { Modifier }
                    ServiceCentreBookingCard(
                        modifier = sharedModifier,
                        booking = booking,
                        onOpen = { onNavigate(RtcRoute.serviceCentreBooking(booking.id)) },"""

content = content.replace(card_call_old, card_call_new)

with open("app/src/main/java/za/org/rtc/community/feature/servicecentre/presentation/ServiceCentreBookingHubScreen.kt", "w") as f:
    f.write(content)
