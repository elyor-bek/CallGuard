package uz.callguard.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "sms_keywords", indices = [Index(value = ["keyword", "isRegex"], unique = true)])
data class SmsKeywordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val keyword: String,
    val isRegex: Boolean = false
)

@Entity(tableName = "blocked_sms")
data class BlockedSmsEntity(
    @PrimaryKey val id: String,
    val notificationKey: String,
    val packageName: String,
    val sender: String,
    val body: String,
    val matchedKeyword: String,
    val blockedAt: Long = System.currentTimeMillis()
)

@Dao
interface SmsSpamDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertKeyword(entity: SmsKeywordEntity)
    @Query("DELETE FROM sms_keywords WHERE id = :id")
    suspend fun deleteKeyword(id: Long)
    @Query("SELECT * FROM sms_keywords ORDER BY id DESC")
    fun observeKeywords(): Flow<List<SmsKeywordEntity>>
    @Query("SELECT * FROM sms_keywords ORDER BY id DESC")
    suspend fun getKeywords(): List<SmsKeywordEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBlocked(entity: BlockedSmsEntity)
    @Query("SELECT * FROM blocked_sms ORDER BY blockedAt DESC")
    fun observeBlocked(): Flow<List<BlockedSmsEntity>>
}
