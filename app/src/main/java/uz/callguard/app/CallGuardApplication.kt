package uz.callguard.app

import android.app.Application
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import uz.callguard.app.sync.GlobalSpamSyncWorker

class CallGuardApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "global-spam-daily", ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<GlobalSpamSyncWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
        )
    }
}
