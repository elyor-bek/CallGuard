package uz.callguard.app.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import uz.callguard.app.data.SpamFeedRepository

class GlobalSpamSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val repository = SpamFeedRepository(applicationContext)
        val summary = SpamFeedSynchronizer().sync(
            feeds = repository.getActive(), download = FeedHttpClient()::download,
            commit = repository::commit, cleanup = repository::cleanup,
            onFailure = { feed, error ->
                // URLs can contain access tokens in their query strings. Do not log URLs or response bodies.
                val code = (error as? FeedHttpException)?.status
                Log.w("CallGuard", "Feed ${feed.id} failed: ${error.javaClass.simpleName}; HTTP=$code")
            }
        )
        if (summary.failed > 0 && runAttemptCount < 3) Result.retry() else Result.success(workDataOf(
            "successful" to summary.successful, "failed" to summary.failed, "skipped" to summary.skipped
        ))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Log.w("CallGuard", "Feed synchronization failed: ${error.javaClass.simpleName}")
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
}
