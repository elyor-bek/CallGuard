package uz.callguard.app.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import uz.callguard.app.MainActivity
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
        const val BLOCKED_CHANNEL_ID = "callguard_blocked_silent_v1"
        const val CHANNEL_ID = "callguard_spam_alerts"

        private const val NOTIFICATION_ID_BASE = 1001
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.warning_channel),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.warning_description)
        }
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
        notificationManager.createNotificationChannel(NotificationChannel(
            BLOCKED_CHANNEL_ID, context.getString(R.string.blocked_channel), NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.blocked_description)
            setSound(null, null)
            enableVibration(false)
        })
    }

    fun showBlockedCallNotification(number: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val openApp = PendingIntent.getActivity(context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, BLOCKED_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield_notification)
            .setContentTitle(context.getString(R.string.blocked_title))
            .setContentText(context.getString(R.string.blocked_body, number))
            .setContentIntent(openApp)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify("blocked:$number", 0, notification)
        } catch (_: SecurityException) {
            // Notifications may be revoked between the permission check and notify().
        }
    }

    fun showSpamWarningNotification(
        number: String,
        details: String = context.getString(R.string.suspected_spam)
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
            .setContentTitle(context.getString(R.string.suspicious_call))
            .setContentText(context.getString(R.string.warning_body, number, details))
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
