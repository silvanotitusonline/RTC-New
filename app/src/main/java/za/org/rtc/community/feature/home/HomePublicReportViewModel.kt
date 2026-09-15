package za.org.rtc.community.feature.home

import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import javax.inject.Inject
import za.org.rtc.community.feature.publicreports.domain.PublicReportRepository

@HiltViewModel
class HomePublicReportViewModel @Inject constructor(
    repository: PublicReportRepository,
    supabase: SupabaseClient,
) : HomeViewModel(repository, supabase)
