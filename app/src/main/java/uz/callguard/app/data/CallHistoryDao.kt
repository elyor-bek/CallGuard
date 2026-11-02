package uz.callguard.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CallHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CallHistoryEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM call_history WHERE number = :number AND timestampMs > :minTimestampMs)")
    suspend fun hasRecentEntry(number: String, minTimestampMs: Long): Boolean

    suspend fun insertIfNotRecent(entity: CallHistoryEntity, windowMs: Long = 2000L) {
        val minTime = entity.timestampMs - windowMs
        if (!hasRecentEntry(entity.number, minTime)) {
            insert(entity)
        }
    }

    @Query("SELECT * FROM call_history ORDER BY timestampMs DESC")
    fun observeAll(): Flow<List<CallHistoryEntity>>

    @Query("SELECT * FROM call_history WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): CallHistoryEntity?

    @Query("DELETE FROM call_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM call_history WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Transaction
    suspend fun deleteEntries(ids: List<Long>) {
        // Stay below SQLite's bind-parameter limit even for a very busy day.
        ids.chunked(900).forEach { deleteByIds(it) }
    }

    @Query("DELETE FROM call_history")
    suspend fun clearAll()
}
