package za.org.rtc.community.feature.marketplace.presentation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun MarketplaceOwnerRoute(
    onNavigate: (String) -> Unit,
    viewModel: MarketplaceOwnerViewModel = hiltViewModel(),
) = MarketplaceOwnerRouteContent(onNavigate = onNavigate, viewModel = viewModel)

@Composable
fun MarketplaceOwnerWizardRoute(
    businessId: String?,
    onNavigate: (String) -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: MarketplaceOwnerViewModel = hiltViewModel(),
) = MarketplaceOwnerWizardRouteContent(
    businessId = businessId,
    onNavigate = onNavigate,
    onBack = onBack,
    viewModel = viewModel,
)

@Composable
fun MarketplaceOwnerPreviewRoute(
    businessId: String,
    viewModel: MarketplaceOwnerViewModel = hiltViewModel(),
) = MarketplaceOwnerPreviewRouteContent(businessId = businessId, viewModel = viewModel)
