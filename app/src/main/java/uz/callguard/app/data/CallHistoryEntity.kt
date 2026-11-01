package uz.callguard.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "call_history")
data class CallHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val number: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val isSpam: Boolean,
    val blockModeApplied: String,
    val reasonDetails: String = ""
)
