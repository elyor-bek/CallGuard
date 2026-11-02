package uz.callguard.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import uz.callguard.app.data.CallGuardDatabase
import uz.callguard.app.data.SpamPolicy

class GlobalSpamSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val now = System.currentTimeMillis()
        val entries = GlobalSpamFeed.parse(GlobalSpamFeed.stubJson, now)
        CallGuardDatabase.getInstance(applicationContext).spamNumberDao()
            .applyGlobalSnapshot(entries, now - SpamPolicy.TTL_MS)
        Result.success()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
}
