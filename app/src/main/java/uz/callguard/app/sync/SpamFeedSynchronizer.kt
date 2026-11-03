package uz.callguard.app.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import uz.callguard.app.data.SpamFeedEntity
import uz.callguard.app.data.SpamNumberEntity

data class FeedSyncSummary(val successful: Int, val failed: Int, val skipped: Int)

class SpamFeedSynchronizer {
    suspend fun sync(
        feeds: List<SpamFeedEntity>,
        download: suspend (String) -> String,
        commit: suspend (SpamFeedEntity, List<SpamNumberEntity>, Long) -> Boolean,
        cleanup: suspend (Long) -> Unit,
        onFailure: (SpamFeedEntity, Exception) -> Unit,
        now: () -> Long = System::currentTimeMillis
    ): FeedSyncSummary {
        var successful = 0
        var failed = 0
        var skipped = 0
        for (feed in feeds.filter { it.isActive }) {
            currentCoroutineContext().ensureActive()
            try {
                val json = download(feed.url)
                val timestamp = now()
                val entries = GlobalSpamFeed.parse(json, timestamp)
                if (commit(feed, entries, timestamp)) successful++ else skipped++
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                failed++
                onFailure(feed, error)
            }
        }
        cleanup(now())
        return FeedSyncSummary(successful, failed, skipped)
    }
}
