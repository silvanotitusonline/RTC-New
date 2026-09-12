package za.org.rtc.community.feature.publicreports.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import za.org.rtc.community.feature.publicreports.data.SupabasePublicReportRepository
import za.org.rtc.community.feature.publicreports.domain.PublicReportRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class PublicReportModule {
    @Binds
    abstract fun bindPublicReportRepository(
        repository: SupabasePublicReportRepository,
    ): PublicReportRepository
}
