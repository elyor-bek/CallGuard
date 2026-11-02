package uz.callguard.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SpamNumberDao {

    // --- Create ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SpamNumberEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<SpamNumberEntity>): List<Long>

    // --- Read ---
    @Query("SELECT * FROM spam_numbers WHERE numberHash = :hash LIMIT 1")
    suspend fun findByHash(hash: String): SpamNumberEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM spam_numbers WHERE numberHash = :hash)")
    suspend fun isSpamByHash(hash: String): Boolean

    @Query("SELECT * FROM spam_numbers ORDER BY addedAtEpochMs DESC")
    fun observeAll(): Flow<List<SpamNumberEntity>>

    @Query("SELECT * FROM spam_numbers ORDER BY addedAtEpochMs DESC")
    suspend fun getAll(): List<SpamNumberEntity>

    @Query("SELECT COUNT(*) FROM spam_numbers")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM spam_numbers")
    fun observeCount(): Flow<Int>

    // --- Update ---
    @Update
    suspend fun update(entity: SpamNumberEntity)

    @Query("UPDATE spam_numbers SET reportCount = reportCount + 1 WHERE numberHash = :hash")
    suspend fun incrementReportCount(hash: String)

    @Query("DELETE FROM spam_numbers WHERE isGlobal = 1 AND updatedAt < :timestamp")
    suspend fun deleteOldGlobalNumbers(timestamp: Long): Int

    @Query("UPDATE spam_numbers SET spamScore = :score, category = :category, updatedAt = :updatedAt WHERE numberHash = :hash AND isGlobal = 1")
    suspend fun refreshGlobal(hash: String, score: Int, category: String?, updatedAt: Long)

    @Transaction
    suspend fun applyGlobalSnapshot(numbers: List<SpamNumberEntity>, cutoff: Long) {
        require(numbers.all { it.isGlobal && it.spamScore in 0..100 })
        insertAll(numbers)
        numbers.forEach { refreshGlobal(it.numberHash, it.spamScore, it.category, it.updatedAt) }
        deleteOldGlobalNumbers(cutoff)
    }

    // --- Delete ---
    @Delete
    suspend fun delete(entity: SpamNumberEntity)

    @Query("DELETE FROM spam_numbers WHERE numberHash = :hash")
    suspend fun deleteByHash(hash: String)

    @Query("DELETE FROM spam_numbers")
    suspend fun clearAll()
}
