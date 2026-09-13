package za.org.rtc.community.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "local_drafts",
    primaryKeys = ["owner_user_id", "area"],
)
data class LocalDraftEntity(
    @ColumnInfo(name = "owner_user_id") val ownerUserId: String,
    val area: String,
    val id: String,
    val title: String,
    val body: String,
    val savedAtEpochMillis: Long,
)

@Entity(tableName = "community_upload_outbox")
data class UploadOutboxEntity(
    @PrimaryKey val id: String,
    /** Null only for pre-migration rows; those rows are deliberately never resumed. */
    @ColumnInfo(name = "owner_user_id") val ownerUserId: String? = null,
    val draftId: String,
    val sourceUri: String,
    val stagedPath: String?,
    val storagePath: String?,
    val mediaKind: String,
    val mimeType: String,
    val byteSize: Long,
    val width: Int?,
    val height: Int?,
    val durationSeconds: Int?,
    val position: Int,
    val state: String,
    val progress: Int,
    val attemptCount: Int,
    val lastError: String?,
    val updatedAtEpochMillis: Long,
)

@Dao
interface LocalDraftDao {
    @Query("SELECT * FROM local_drafts WHERE owner_user_id = :ownerUserId ORDER BY savedAtEpochMillis DESC")
    fun observeForOwner(ownerUserId: String): Flow<List<LocalDraftEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LocalDraftEntity)

    @Query("DELETE FROM local_drafts WHERE owner_user_id = :ownerUserId AND area = :area")
    suspend fun deleteAreaForOwner(ownerUserId: String, area: String)
}

@Dao
interface UploadOutboxDao {
    @Query("SELECT * FROM community_upload_outbox ORDER BY position")
    fun observeAll(): Flow<List<UploadOutboxEntity>>

    @Query("SELECT COUNT(*) FROM community_upload_outbox WHERE owner_user_id = :ownerUserId AND state IN ('PENDING','PREPARING','UPLOADING','RETRY')")
    fun observePendingCountForOwner(ownerUserId: String): Flow<Int>

    @Query("SELECT * FROM community_upload_outbox WHERE owner_user_id = :ownerUserId AND state IN ('PENDING','RETRY') ORDER BY updatedAtEpochMillis LIMIT :limit")
    suspend fun pendingForOwner(ownerUserId: String, limit: Int = 20): List<UploadOutboxEntity>

    @Query("SELECT * FROM community_upload_outbox WHERE draftId = :draftId AND owner_user_id = :ownerUserId ORDER BY position")
    suspend fun forDraftForOwner(draftId: String, ownerUserId: String): List<UploadOutboxEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: UploadOutboxEntity)

    @Query("DELETE FROM community_upload_outbox WHERE draftId = :draftId AND owner_user_id = :ownerUserId")
    suspend fun deleteDraftForOwner(draftId: String, ownerUserId: String)
}

@Entity(tableName = "cached_reports")
data class CachedReportEntity(
    @PrimaryKey val id: String,
    val targetType: String,
    val targetId: String,
    val reason: String,
    val details: String,
    val status: String,
    val createdAtEpochMillis: Long,
)

@Dao
interface CachedReportDao {
    @Query("SELECT * FROM cached_reports ORDER BY createdAtEpochMillis DESC")
    fun observeAllReports(): Flow<List<CachedReportEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: CachedReportEntity)

    @Query("DELETE FROM cached_reports WHERE id = :id")
    suspend fun deleteReport(id: String)
}

/** Public, rounded projections of reports that were verified by the server. */
@Entity(tableName = "verified_public_report_cache")
data class VerifiedPublicReportEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val startedAt: String?,
    val categoryId: String,
    val categorySlug: String,
    val categoryLabel: String,
    val urgency: String,
    val status: String,
    val identityMode: String,
    val publicLocationLabel: String,
    val authorDisplayName: String,
    val verificationReason: String?,
    val duplicateOf: String?,
    val thumbsUpCount: Int,
    val thumbsDownCount: Int,
    val commentCount: Int,
    val evidenceCount: Int,
    val currentUserVote: Int,
    val createdAt: String,
    val updatedAt: String,
    val publicLatitude: Double?,
    val publicLongitude: Double?,
    val cachedAtEpochMillis: Long,
)

@Dao
interface VerifiedPublicReportDao {
    @Query("SELECT * FROM verified_public_report_cache ORDER BY createdAt DESC, id DESC LIMIT :limit")
    suspend fun latest(limit: Int): List<VerifiedPublicReportEntity>

    @Query("SELECT * FROM verified_public_report_cache WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): VerifiedPublicReportEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(reports: List<VerifiedPublicReportEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(report: VerifiedPublicReportEntity)

    @Query("DELETE FROM verified_public_report_cache WHERE cachedAtEpochMillis < :cutoffEpochMillis")
    suspend fun deleteOlderThan(cutoffEpochMillis: Long)
}

@Entity(tableName = "cached_app_state")
data class CachedAppStateEntity(
    @PrimaryKey val stateKey: String,
    val stateValue: String,
    val updatedAtEpochMillis: Long,
)

@Dao
interface CachedAppStateDao {
    @Query("SELECT stateValue FROM cached_app_state WHERE stateKey = :key")
    suspend fun getStateValue(key: String): String?

    @Query("SELECT stateValue FROM cached_app_state WHERE stateKey = :key")
    fun observeStateValue(key: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertState(entity: CachedAppStateEntity)

    @Query("DELETE FROM cached_app_state WHERE stateKey = :key")
    suspend fun deleteState(key: String)
}

@Entity(tableName = "cached_session")
data class CachedSessionEntity(
    @PrimaryKey val userId: String,
    val email: String,
    val isLoggedIn: Boolean,
    val sessionJson: String,
    val updatedAtEpochMillis: Long,
)

@Dao
interface CachedSessionDao {
    @Query("SELECT * FROM cached_session WHERE isLoggedIn = 1 ORDER BY updatedAtEpochMillis DESC LIMIT 1")
    fun observeActiveSession(): Flow<CachedSessionEntity?>

    @Query("SELECT * FROM cached_session WHERE isLoggedIn = 1 ORDER BY updatedAtEpochMillis DESC LIMIT 1")
    suspend fun getActiveSession(): CachedSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: CachedSessionEntity)

    @Query("UPDATE cached_session SET isLoggedIn = 0 WHERE userId = :userId")
    suspend fun logoutUser(userId: String)

    @Query("UPDATE cached_session SET isLoggedIn = 0")
    suspend fun logoutAll()
}

@Entity(tableName = "cached_posts")
data class CachedPostEntity(
    @PrimaryKey val id: String,
    val author: String,
    val handle: String,
    val content: String,
    val category: String,
    val createdAt: String,
    val reactions: Int,
    val comments: Int,
    val viewerHasLiked: Boolean,
    val userReactionsJson: String? = null,
    val reactionCountsJson: String? = null,
    val trendingScore: Int,
    val isOfficial: Boolean,
    val authorId: String?,
    val authorAvatarUrl: String?,
    val mediaJson: String?,
    val createdAtEpochMillis: Long,
    val repostOfId: String? = null,
    val quotePostId: String? = null,
    val repostCount: Int = 0,
    val bookmarkCount: Int = 0,
    val isRepostedByViewer: Boolean = false,
    val isBookmarkedByViewer: Boolean = false,
)

@Dao
interface CachedPostDao {
    @Query("SELECT * FROM cached_posts ORDER BY createdAtEpochMillis DESC")
    fun observeAllPosts(): Flow<List<CachedPostEntity>>

    @Query("SELECT * FROM cached_posts ORDER BY createdAtEpochMillis DESC")
    suspend fun getAllPosts(): List<CachedPostEntity>

    @Query("SELECT * FROM cached_posts WHERE id = :id LIMIT 1")
    suspend fun getPostById(id: String): CachedPostEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPost(post: CachedPostEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPosts(posts: List<CachedPostEntity>)

    @Query("DELETE FROM cached_posts WHERE id = :id")
    suspend fun deletePost(id: String)
}

@Entity(tableName = "cached_comments")
data class CachedCommentEntity(
    @PrimaryKey val id: String,
    val postId: String,
    val authorId: String,
    val author: String,
    val handle: String,
    val content: String,
    val createdAt: String,
    val authorAvatarUrl: String?,
    val createdAtEpochMillis: Long,
    val parentId: String? = null,
    val replyCount: Int = 0,
    val depth: Int = 0,
)

@Dao
interface CachedCommentDao {
    @Query("SELECT * FROM cached_comments WHERE postId = :postId ORDER BY createdAtEpochMillis ASC")
    fun observeCommentsForPost(postId: String): Flow<List<CachedCommentEntity>>

    @Query("SELECT * FROM cached_comments WHERE postId = :postId ORDER BY createdAtEpochMillis ASC")
    suspend fun getCommentsForPost(postId: String): List<CachedCommentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComment(comment: CachedCommentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComments(comments: List<CachedCommentEntity>)

    @Query("DELETE FROM cached_comments WHERE id = :id")
    suspend fun deleteComment(id: String)

    @Query("DELETE FROM cached_comments WHERE postId = :postId")
    suspend fun deleteCommentsForPost(postId: String)
}

@Entity(tableName = "cached_user_profiles")
data class CachedUserProfileEntity(
    @PrimaryKey val userId: String,
    val email: String?,
    val displayName: String,
    val bio: String,
    val interestsJson: String,
    val avatarUrl: String?,
    val role: String,
    val updatedAtEpochMillis: Long,
)

@Dao
interface CachedUserProfileDao {
    @Query("SELECT * FROM cached_user_profiles WHERE userId = :userId LIMIT 1")
    suspend fun getProfile(userId: String): CachedUserProfileEntity?

    @Query("SELECT * FROM cached_user_profiles WHERE email = :email LIMIT 1")
    suspend fun getProfileByEmail(email: String): CachedUserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: CachedUserProfileEntity)
}

/**
 * Existing v1 rows have no verified account owner. Preserve them but make them ineligible for
 * automatic recovery; a later account must never resume another account's staged media.
 */
val RTC_DATABASE_MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE community_upload_outbox ADD COLUMN owner_user_id TEXT")
    }
}

/**
 * Legacy drafts have no trustworthy account owner. They are deliberately discarded rather than
 * attributed to whichever account signs in after the upgrade, which would leak private draft
 * content across accounts on a shared installation.
 */
val RTC_DATABASE_MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS local_drafts_v3 (
                owner_user_id TEXT NOT NULL,
                area TEXT NOT NULL,
                id TEXT NOT NULL,
                title TEXT NOT NULL,
                body TEXT NOT NULL,
                savedAtEpochMillis INTEGER NOT NULL,
                PRIMARY KEY(owner_user_id, area)
            )
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE local_drafts")
        db.execSQL("ALTER TABLE local_drafts_v3 RENAME TO local_drafts")
    }
}

val RTC_DATABASE_MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS cached_reports (
                id TEXT NOT NULL PRIMARY KEY,
                targetType TEXT NOT NULL,
                targetId TEXT NOT NULL,
                reason TEXT NOT NULL,
                details TEXT NOT NULL,
                status TEXT NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS cached_app_state (
                stateKey TEXT NOT NULL PRIMARY KEY,
                stateValue TEXT NOT NULL,
                updatedAtEpochMillis INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS cached_session (
                userId TEXT NOT NULL PRIMARY KEY,
                email TEXT NOT NULL,
                isLoggedIn INTEGER NOT NULL,
                sessionJson TEXT NOT NULL,
                updatedAtEpochMillis INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

val RTC_DATABASE_MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS cached_posts (
                id TEXT NOT NULL PRIMARY KEY,
                author TEXT NOT NULL,
                handle TEXT NOT NULL,
                content TEXT NOT NULL,
                category TEXT NOT NULL,
                createdAt TEXT NOT NULL,
                reactions INTEGER NOT NULL,
                comments INTEGER NOT NULL,
                viewerHasLiked INTEGER NOT NULL,
                trendingScore INTEGER NOT NULL,
                isOfficial INTEGER NOT NULL,
                authorId TEXT,
                authorAvatarUrl TEXT,
                mediaJson TEXT,
                createdAtEpochMillis INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS cached_comments (
                id TEXT NOT NULL PRIMARY KEY,
                postId TEXT NOT NULL,
                authorId TEXT NOT NULL,
                author TEXT NOT NULL,
                handle TEXT NOT NULL,
                content TEXT NOT NULL,
                createdAt TEXT NOT NULL,
                authorAvatarUrl TEXT,
                createdAtEpochMillis INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS cached_user_profiles (
                userId TEXT NOT NULL PRIMARY KEY,
                email TEXT,
                displayName TEXT NOT NULL,
                bio TEXT NOT NULL,
                interestsJson TEXT NOT NULL,
                avatarUrl TEXT,
                role TEXT NOT NULL,
                updatedAtEpochMillis INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

val RTC_DATABASE_MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE cached_posts ADD COLUMN userReactionsJson TEXT")
        db.execSQL("ALTER TABLE cached_posts ADD COLUMN reactionCountsJson TEXT")
        db.execSQL("ALTER TABLE cached_comments ADD COLUMN parentId TEXT")
    }
}

val RTC_DATABASE_MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE cached_posts ADD COLUMN repostOfId TEXT")
        db.execSQL("ALTER TABLE cached_posts ADD COLUMN quotePostId TEXT")
        db.execSQL("ALTER TABLE cached_posts ADD COLUMN repostCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE cached_posts ADD COLUMN bookmarkCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE cached_posts ADD COLUMN isRepostedByViewer INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE cached_posts ADD COLUMN isBookmarkedByViewer INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE cached_comments ADD COLUMN replyCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE cached_comments ADD COLUMN depth INTEGER NOT NULL DEFAULT 0")
    }
}

val RTC_DATABASE_MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS verified_public_report_cache (
                id TEXT NOT NULL PRIMARY KEY,
                title TEXT NOT NULL,
                description TEXT NOT NULL,
                startedAt TEXT,
                categoryId TEXT NOT NULL,
                categorySlug TEXT NOT NULL,
                categoryLabel TEXT NOT NULL,
                urgency TEXT NOT NULL,
                status TEXT NOT NULL,
                identityMode TEXT NOT NULL,
                publicLocationLabel TEXT NOT NULL,
                authorDisplayName TEXT NOT NULL,
                verificationReason TEXT,
                duplicateOf TEXT,
                thumbsUpCount INTEGER NOT NULL,
                thumbsDownCount INTEGER NOT NULL,
                commentCount INTEGER NOT NULL,
                evidenceCount INTEGER NOT NULL,
                currentUserVote INTEGER NOT NULL,
                createdAt TEXT NOT NULL,
                updatedAt TEXT NOT NULL,
                publicLatitude REAL,
                publicLongitude REAL,
                cachedAtEpochMillis INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS verified_public_report_cache_order ON verified_public_report_cache(createdAt DESC, id DESC)")
    }
}

@Database(
    entities = [
        LocalDraftEntity::class,
        UploadOutboxEntity::class,
        CachedReportEntity::class,
        VerifiedPublicReportEntity::class,
        CachedAppStateEntity::class,
        CachedSessionEntity::class,
        CachedPostEntity::class,
        CachedCommentEntity::class,
        CachedUserProfileEntity::class,
    ],
    version = 8,
    exportSchema = false,
)
abstract class RtcDatabase : RoomDatabase() {
    abstract fun localDraftDao(): LocalDraftDao
    abstract fun uploadOutboxDao(): UploadOutboxDao
    abstract fun cachedReportDao(): CachedReportDao
    abstract fun verifiedPublicReportDao(): VerifiedPublicReportDao
    abstract fun cachedAppStateDao(): CachedAppStateDao
    abstract fun cachedSessionDao(): CachedSessionDao
    abstract fun cachedPostDao(): CachedPostDao
    abstract fun cachedCommentDao(): CachedCommentDao
    abstract fun cachedUserProfileDao(): CachedUserProfileDao
}
