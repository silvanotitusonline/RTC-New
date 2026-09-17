package za.org.rtc.community.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import za.org.rtc.community.BuildConfig
import androidx.room.Room
import androidx.work.WorkManager
import za.org.rtc.community.data.local.CachedAppStateDao
import za.org.rtc.community.data.local.CachedCommentDao
import za.org.rtc.community.data.local.CachedPostDao
import za.org.rtc.community.data.local.CachedReportDao
import za.org.rtc.community.data.local.CachedSessionDao
import za.org.rtc.community.data.local.CachedUserProfileDao
import za.org.rtc.community.data.local.RtcDatabase
import za.org.rtc.community.data.local.LocalDraftDao
import za.org.rtc.community.data.local.RTC_DATABASE_MIGRATION_1_2
import za.org.rtc.community.data.local.RTC_DATABASE_MIGRATION_2_3
import za.org.rtc.community.data.local.RTC_DATABASE_MIGRATION_3_4
import za.org.rtc.community.data.local.RTC_DATABASE_MIGRATION_4_5
import za.org.rtc.community.data.local.RTC_DATABASE_MIGRATION_5_6
import za.org.rtc.community.data.local.RTC_DATABASE_MIGRATION_6_7
import za.org.rtc.community.data.local.RTC_DATABASE_MIGRATION_7_8
import za.org.rtc.community.data.local.RTC_DATABASE_MIGRATION_8_9
import za.org.rtc.community.feature.dailypost.data.DailyPostDao
import za.org.rtc.community.feature.dailypost.data.DailyPostRepository
import za.org.rtc.community.feature.dailypost.data.RoomDailyPostRepository
import javax.inject.Singleton
import za.org.rtc.community.feature.community.CommunityRepository
import za.org.rtc.community.feature.community.SupabaseCommunityRepository
import za.org.rtc.community.feature.marketplace.data.remote.SupabaseMarketplaceRepository
import za.org.rtc.community.feature.marketplace.domain.MarketplaceAdminRepository
import za.org.rtc.community.feature.marketplace.domain.MarketplaceDiscoveryRepository
import za.org.rtc.community.feature.marketplace.domain.MarketplaceOwnerRepository
import za.org.rtc.community.feature.marketplace.domain.MarketplaceReviewRepository
import za.org.rtc.community.feature.marketplace.domain.MarketplaceLocationRepository
import za.org.rtc.community.core.location.MarketplaceLocationProvider
import za.org.rtc.community.feature.servicecentre.data.remote.SupabaseServiceCentreRepository
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreBookingRepository
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreDiscoveryRepository
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreProviderRepository

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
    ) {
        install(Auth)
        install(Postgrest)
        install(Storage)
        install(Functions)
        install(Realtime)
    }

    @Provides
    @Singleton
    fun provideRtcDatabase(@dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context): RtcDatabase {
        val builder = Room.databaseBuilder(context, RtcDatabase::class.java, "rtc-community.db")
            .addMigrations(
                RTC_DATABASE_MIGRATION_1_2,
                RTC_DATABASE_MIGRATION_2_3,
                RTC_DATABASE_MIGRATION_3_4,
                RTC_DATABASE_MIGRATION_4_5,
                RTC_DATABASE_MIGRATION_5_6,
                RTC_DATABASE_MIGRATION_6_7,
                RTC_DATABASE_MIGRATION_7_8,
                RTC_DATABASE_MIGRATION_8_9,
            )
        // Destructive fallback silently drops and recreates every table on
        // any migration path gap — including local_drafts (unsent posts)
        // and community_upload_outbox (queued media uploads). Acceptable
        // for debug-cycle convenience; never acceptable in release, where a
        // missed migration would erase a resident's queued content with no
        // warning. Release builds fail loudly instead.
        if (BuildConfig.DEBUG) {
            builder.fallbackToDestructiveMigration(true)
        }
        return builder.build()
    }

    @Provides
    fun provideLocalDraftDao(database: RtcDatabase): LocalDraftDao = database.localDraftDao()

    @Provides
    fun provideDailyPostDao(database: RtcDatabase): DailyPostDao = database.dailyPostDao()

    @Provides
    @Singleton
    fun provideDailyPostRepository(repository: RoomDailyPostRepository): DailyPostRepository = repository

    @Provides
    fun provideCachedReportDao(database: RtcDatabase): CachedReportDao = database.cachedReportDao()

    @Provides
    fun provideCachedAppStateDao(database: RtcDatabase): CachedAppStateDao = database.cachedAppStateDao()

    @Provides
    fun provideCachedSessionDao(database: RtcDatabase): CachedSessionDao = database.cachedSessionDao()

    @Provides
    fun provideCachedPostDao(database: RtcDatabase): CachedPostDao = database.cachedPostDao()

    @Provides
    fun provideCachedCommentDao(database: RtcDatabase): CachedCommentDao = database.cachedCommentDao()

    @Provides
    fun provideCachedUserProfileDao(database: RtcDatabase): CachedUserProfileDao = database.cachedUserProfileDao()

    @Provides
    @Singleton
    fun provideWorkManager(@dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context): WorkManager =
        WorkManager.getInstance(context)

    @Provides
    @Singleton
    fun provideCommunityRepository(repository: SupabaseCommunityRepository): CommunityRepository = repository

    @Provides
    @Singleton
    fun provideMarketplaceLocationRepository(provider: MarketplaceLocationProvider): MarketplaceLocationRepository = provider

    @Provides
    @Singleton
    fun provideMarketplaceDiscoveryRepository(repository: SupabaseMarketplaceRepository): MarketplaceDiscoveryRepository = repository

    @Provides
    @Singleton
    fun provideMarketplaceOwnerRepository(repository: SupabaseMarketplaceRepository): MarketplaceOwnerRepository = repository

    @Provides
    @Singleton
    fun provideMarketplaceReviewRepository(repository: SupabaseMarketplaceRepository): MarketplaceReviewRepository = repository

    @Provides
    @Singleton
    fun provideMarketplaceAdminRepository(repository: SupabaseMarketplaceRepository): MarketplaceAdminRepository = repository

    @Provides
    @Singleton
    fun provideServiceCentreDiscoveryRepository(repository: SupabaseServiceCentreRepository): ServiceCentreDiscoveryRepository = repository

    @Provides
    @Singleton
    fun provideServiceCentreProviderRepository(repository: SupabaseServiceCentreRepository): ServiceCentreProviderRepository = repository

    @Provides
    @Singleton
    fun provideServiceCentreBookingRepository(repository: SupabaseServiceCentreRepository): ServiceCentreBookingRepository = repository
}
