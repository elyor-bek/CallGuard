package uz.callguard.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NumberPatternDao {
    @Query("SELECT * FROM number_patterns ORDER BY addedAtEpochMs DESC, pattern ASC")
    fun observeAll(): Flow<List<NumberPatternEntity>>

    @Query("SELECT * FROM number_patterns")
    suspend fun getAll(): List<NumberPatternEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(pattern: NumberPatternEntity)

    @Query("DELETE FROM number_patterns WHERE pattern = :pattern")
    suspend fun delete(pattern: String)
}
