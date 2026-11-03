package uz.callguard.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SpamFeedDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(feed: SpamFeedEntity): Long
    @Update
    suspend fun update(feed: SpamFeedEntity): Int
    @Query("DELETE FROM spam_feeds WHERE id = :id")
    suspend fun delete(id: Long): Int
    @Query("SELECT * FROM spam_feeds ORDER BY id ASC")
    fun observeAll(): Flow<List<SpamFeedEntity>>
    @Query("SELECT * FROM spam_feeds ORDER BY id ASC")
    suspend fun getAll(): List<SpamFeedEntity>
    @Query("SELECT * FROM spam_feeds WHERE isActive = 1 ORDER BY id ASC")
    suspend fun getActive(): List<SpamFeedEntity>
    @Query("SELECT * FROM spam_feeds WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): SpamFeedEntity?
    @Query("UPDATE spam_feeds SET lastSync = :timestamp WHERE id = :id")
    suspend fun markSynced(id: Long, timestamp: Long)
}
