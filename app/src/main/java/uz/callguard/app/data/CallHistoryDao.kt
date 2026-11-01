package uz.callguard.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

    @Query("DELETE FROM call_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM call_history")
    suspend fun clearAll()
}
