package uz.callguard.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SpamNumberDao {

    // --- Create ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SpamNumberEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<SpamNumberEntity>)

    // --- Read ---
    @Query("SELECT * FROM spam_numbers WHERE numberHash = :hash LIMIT 1")
    suspend fun findByHash(hash: String): SpamNumberEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM spam_numbers WHERE numberHash = :hash)")
    suspend fun isSpamByHash(hash: String): Boolean

    @Query("SELECT * FROM spam_numbers ORDER BY addedAtEpochMs DESC")
    fun observeAll(): Flow<List<SpamNumberEntity>>

    @Query("SELECT COUNT(*) FROM spam_numbers")
    suspend fun count(): Int

    // --- Update ---
    @Update
    suspend fun update(entity: SpamNumberEntity)

    @Query("UPDATE spam_numbers SET reportCount = reportCount + 1 WHERE numberHash = :hash")
    suspend fun incrementReportCount(hash: String)

    // --- Delete ---
    @Delete
    suspend fun delete(entity: SpamNumberEntity)

    @Query("DELETE FROM spam_numbers WHERE numberHash = :hash")
    suspend fun deleteByHash(hash: String)

    @Query("DELETE FROM spam_numbers")
    suspend fun clearAll()
}
