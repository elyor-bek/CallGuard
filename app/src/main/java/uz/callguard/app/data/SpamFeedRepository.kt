package uz.callguard.app.data

import android.content.Context
import androidx.room.withTransaction
import uz.callguard.app.sync.FeedUrlPolicy

class SpamFeedRepository(context: Context) {
    private val database = CallGuardDatabase.getInstance(context.applicationContext)
    private val feeds = database.spamFeedDao()
    fun observeAll() = feeds.observeAll()
    suspend fun getActive() = feeds.getActive()
    suspend fun insert(feed: SpamFeedEntity): Long { validate(feed); return feeds.insert(feed) }
    suspend fun update(feed: SpamFeedEntity): Int { validate(feed); return feeds.update(feed) }
    suspend fun setActive(id: Long, active: Boolean) = database.withTransaction {
        val current = requireNotNull(feeds.findById(id))
        val changed = current.copy(isActive = active)
        validate(changed)
        feeds.update(changed)
    }
    suspend fun delete(id: Long) = feeds.delete(id)

    suspend fun commit(feed: SpamFeedEntity, entries: List<SpamNumberEntity>, now: Long): Boolean = database.withTransaction {
        // A removed/disabled/edited subscription must not commit a download from its old URL.
        val current = feeds.findById(feed.id)
        if (current == null || !current.isActive || current.url != feed.url) return@withTransaction false
        database.spamNumberDao().applyGlobalSnapshot(entries, now - SpamPolicy.TTL_MS)
        feeds.markSynced(feed.id, now)
        true
    }
    suspend fun cleanup(now: Long) { database.spamNumberDao().deleteOldGlobalNumbers(now - SpamPolicy.TTL_MS) }

    private fun validate(feed: SpamFeedEntity) {
        require(feed.name.isNotBlank() && feed.name.length <= 120)
        if (feed.isActive || feed.url.isNotBlank()) FeedUrlPolicy.validate(feed.url)
    }
}
