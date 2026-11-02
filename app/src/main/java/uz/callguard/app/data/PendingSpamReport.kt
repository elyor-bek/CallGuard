package uz.callguard.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey

/** Local-only outbox. No phone number, contacts or call history are sent anywhere. */
@Entity(tableName = "pending_spam_reports")
data class PendingSpamReport(
    @PrimaryKey val callId: Long,
    val numberHash: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface PendingSpamReportDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(report: PendingSpamReport)
}
