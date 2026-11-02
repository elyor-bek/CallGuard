package uz.callguard.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Хэш используется для поиска, номер — для отображения пользовательских записей.
 * У старых и импортированных записей номер может отсутствовать.
 */
@Entity(tableName = "spam_numbers")
data class SpamNumberEntity(
    @PrimaryKey
    val numberHash: String,
    val source: String,      // "user_report" | "telegram" | "operator" | "kaggle" | "manual" | "assets"
    val reportCount: Int = 1,
    val addedAtEpochMs: Long = System.currentTimeMillis(),
    val phoneNumber: String? = null,
    @ColumnInfo(defaultValue = "100") val spamScore: Int = 100,
    val category: String? = null,
    @ColumnInfo(defaultValue = "0") val isGlobal: Boolean = false,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = addedAtEpochMs
) {
    init { require(spamScore in 0..100) }
}
