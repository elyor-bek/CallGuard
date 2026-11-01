package uz.callguard.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Одна запись в LocalSpamDB. Хранится только хэш номера (number_hash)
 * для обеспечения приватности.
 */
@Entity(tableName = "spam_numbers")
data class SpamNumberEntity(
    @PrimaryKey
    val numberHash: String,
    val source: String,      // "user_report" | "telegram" | "operator" | "kaggle" | "manual" | "assets"
    val reportCount: Int = 1,
    val addedAtEpochMs: Long = System.currentTimeMillis()
)
