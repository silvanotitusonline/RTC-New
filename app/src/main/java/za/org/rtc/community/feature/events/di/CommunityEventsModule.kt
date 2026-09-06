package za.org.rtc.community.feature.events.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import za.org.rtc.community.feature.events.data.remote.SupabaseCommunityEventsRepository
import za.org.rtc.community.feature.events.domain.CommunityEventsRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class CommunityEventsModule {
    @Binds
    abstract fun bindCommunityEventsRepository(
        repository: SupabaseCommunityEventsRepository,
    ): CommunityEventsRepository
}
