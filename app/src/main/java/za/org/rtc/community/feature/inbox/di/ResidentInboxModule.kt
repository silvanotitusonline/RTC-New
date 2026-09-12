package za.org.rtc.community.feature.inbox.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import za.org.rtc.community.feature.inbox.data.remote.SupabaseResidentInboxRepository
import za.org.rtc.community.feature.inbox.domain.ResidentInboxRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class ResidentInboxModule {
    @Binds
    abstract fun bindResidentInboxRepository(
        repository: SupabaseResidentInboxRepository,
    ): ResidentInboxRepository
}
