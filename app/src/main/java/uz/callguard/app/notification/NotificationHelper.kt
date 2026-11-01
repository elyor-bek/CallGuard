package uz.callguard.app.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import uz.callguard.app.R

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "callguard_spam_alerts"
        const val CHANNEL_NAME = "Предупреждения о спаме"
        private const val NOTIFICATION_ID_BASE = 1001
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Канал уведомлений о подозрительных и спам-звонках"
        }
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    fun showSpamWarningNotification(
        number: String,
        details: String = "Подозрение на спам/скам звонок"
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Подозрительный звонок")
            .setContentText("Номер: $number · $details")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(
                (NOTIFICATION_ID_BASE + (System.currentTimeMillis() % 10000)).toInt(),
                notification
            )
        } catch (_: SecurityException) {
            // Разрешение на уведомления не предоставлено
        }
    }
}
