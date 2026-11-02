package uz.callguard.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "number_patterns")
data class NumberPatternEntity(
    @PrimaryKey val pattern: String,
    val addedAtEpochMs: Long = System.currentTimeMillis()
)
