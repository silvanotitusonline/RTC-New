package za.org.rtc.remediation.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "rtc_records", primaryKeys = ["ownerId", "kind", "id"])
data class CachedRecord(val ownerId: String, val kind: String, val id: String, val payload: String, val sortAt: String)
@Entity(tableName = "rtc_outbox", indices = [Index(value = ["ownerId", "state", "createdAt"])])
data class OutboxEntity(
    @PrimaryKey val id: String, val ownerId: String, val kind: String, val payload: String,
    val imagePath: String? = null, val imageMime: String? = null,
    val state: String = "PENDING", val attempts: Int = 0, val leaseUntil: Long = 0,
    val lastError: String? = null, val createdAt: String,
)
@Dao abstract class RtcDao {
    @Query("SELECT * FROM rtc_records WHERE ownerId=:owner AND kind=:kind ORDER BY sortAt DESC,id DESC")
    abstract fun observe(owner: String, kind: String): Flow<List<CachedRecord>>
    @Upsert abstract suspend fun put(records: List<CachedRecord>)
    @Query("DELETE FROM rtc_records WHERE ownerId=:owner AND kind=:kind")
    abstract suspend fun clear(owner: String, kind: String)
    @Transaction open suspend fun replace(owner: String, kind: String, records: List<CachedRecord>) {
        clear(owner,kind); put(records)
    }
    @Query("SELECT * FROM rtc_outbox WHERE ownerId=:owner ORDER BY createdAt")
    abstract fun observeOutbox(owner: String): Flow<List<OutboxEntity>>
    @Query("SELECT * FROM rtc_outbox WHERE id=:id AND ownerId=:owner")
    abstract suspend fun findOutbox(id: String, owner: String): OutboxEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun enqueue(item: OutboxEntity)
    @Query("SELECT * FROM rtc_outbox WHERE ownerId=:owner AND (state='PENDING' OR (state='SENDING' AND leaseUntil<:now)) ORDER BY createdAt LIMIT 1")
    abstract suspend fun next(owner: String, now: Long): OutboxEntity?
    @Query("UPDATE rtc_outbox SET state='SENDING',leaseUntil=:until,attempts=attempts+1 WHERE id=:id AND (state='PENDING' OR (state='SENDING' AND leaseUntil<:now))")
    abstract suspend fun claimRow(id: String, now: Long, until: Long): Int
    @Transaction open suspend fun claim(owner: String, now: Long): OutboxEntity? {
        val item = next(owner, now) ?: return null
        return if (claimRow(item.id, now, now+120_000) == 1) item.copy(attempts=item.attempts+1) else null
    }
    @Query("UPDATE rtc_outbox SET state=:state,lastError=:error,leaseUntil=0 WHERE id=:id")
    abstract suspend fun mark(id: String, state: String, error: String?)
    @Query("DELETE FROM rtc_outbox WHERE id=:id AND ownerId=:owner")
    abstract suspend fun remove(id: String, owner: String)
    @Query("UPDATE rtc_outbox SET state='PENDING',lastError=NULL WHERE ownerId=:owner AND state='NEEDS_AUTH'")
    abstract suspend fun resumeAuth(owner: String)
    @Query("UPDATE rtc_outbox SET state='PENDING',attempts=0,lastError=NULL,leaseUntil=0 WHERE id=:id AND ownerId=:owner AND state='FAILED'")
    abstract suspend fun retry(id: String, owner: String)
    @Query("SELECT count(*) FROM rtc_outbox WHERE ownerId=:owner AND state IN ('PENDING','SENDING')")
    abstract suspend fun remaining(owner: String): Int
}
@Database(entities=[CachedRecord::class, OutboxEntity::class], version=1, exportSchema=true)
abstract class RtcDatabase : RoomDatabase() { abstract fun dao(): RtcDao }
