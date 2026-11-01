package uz.callguard.app.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import uz.callguard.app.data.SpamNumberRepository
import uz.callguard.app.heuristic.HeuristicFilter
import uz.callguard.app.heuristic.HeuristicResult
import uz.callguard.app.overlay.OverlayWarningTrigger

/**
 * Фолбэк-перехватчик звонков. Решение зафиксировано после того, как выяснилось,
 * что Samsung Dialer (com.samsung.android.dialer) не вызывает третьесторонний
 * CallScreeningService должным образом (spam_default_agent == null) — спам-защита
 * зашита в закрытый Telecom-слой Samsung.
 *
 * Вместо этого читаем собственное уведомление Samsung о входящем звонке
 * (тот же трюк использует Truecaller). Компромисс: задержка обнаружения ~5с
 * против настоящего call screening через CallScreeningService.
 *
 * Если у пользователя дефолтный дайлер AOSP/Google Phone, а не Samsung —
 * этот путь не нужен, там ROLE_CALL_SCREENING работает нормально. Пока
 * ограничиваемся Samsung-фолбэком, т.к. это основной кейс в UZ.
 */
class CallGuardNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: SpamNumberRepository

    // Пакеты дайлеров, чьи уведомления о входящем звонке мы слушаем
    private val watchedPackages = setOf(
        "com.samsung.android.incallui",
        "com.samsung.android.dialer",
        "com.android.dialer",
        "com.google.android.dialer"
    )

    override fun onCreate() {
        super.onCreate()
        repository = SpamNumberRepository(applicationContext)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in watchedPackages) return

        val number = extractPhoneNumber(sbn) ?: run {
            Log.d("CallGuard", "Incoming call notification without a parseable number from ${sbn.packageName}")
            return
        }

        scope.launch {
            evaluateAndAct(number)
        }
    }

    /**
     * Уведомление о звонке не даёт "сырой" номер напрямую — вытаскиваем из заголовка/текста.
     * Это best-effort: если дайлер показывает имя контакта вместо номера, детект не сработает
     * (для контакта из адресной книги риск спама и так низкий).
     */
    private fun extractPhoneNumber(sbn: StatusBarNotification): String? {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(NotificationCompat.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(NotificationCompat.EXTRA_TEXT)?.toString().orEmpty()

        val phoneRegex = Regex("(\\+?\\d[\\d\\s\\-()]{6,}\\d)")
        return phoneRegex.find(title)?.value
            ?: phoneRegex.find(text)?.value
    }

    private suspend fun evaluateAndAct(number: String) {
        val heuristicResult = HeuristicFilter.evaluate(number)

        when (heuristicResult.verdict) {
            HeuristicResult.Verdict.BLOCK -> {
                Log.w("CallGuard", "BLOCK candidate: $number reasons=${heuristicResult.reasons}")
                OverlayWarningTrigger.show(applicationContext, number, heuristicResult.score, heuristicResult.reasons)
            }
            HeuristicResult.Verdict.SUSPECT -> {
                OverlayWarningTrigger.show(applicationContext, number, heuristicResult.score, heuristicResult.reasons)
            }
            HeuristicResult.Verdict.UNKNOWN -> {
                // второй слой — LocalSpamDB
                if (repository.isKnownSpam(number)) {
                    OverlayWarningTrigger.show(applicationContext, number, 80, listOf("known_in_local_spam_db"))
                }
            }
            HeuristicResult.Verdict.ALLOW -> {
                // ничего не делаем
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // не используется пока
    }
}
