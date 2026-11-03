package uz.callguard.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Feed metadata. New custom subscriptions are inactive until explicitly enabled. */
@Entity(tableName = "spam_feeds")
data class SpamFeedEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val url: String,
    val isActive: Boolean = false,
    val lastSync: Long = 0
)
