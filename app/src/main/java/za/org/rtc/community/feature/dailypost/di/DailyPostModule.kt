package za.org.rtc.community.feature.dailypost.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import za.org.rtc.community.feature.dailypost.data.SupabaseDailyPostRepository
import za.org.rtc.community.feature.dailypost.domain.DailyPostRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class DailyPostModule {
    @Binds
    @Singleton
    abstract fun bindDailyPostRepository(implementation: SupabaseDailyPostRepository): DailyPostRepository
}
