package uz.callguard.app

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import uz.callguard.app.data.BlockMode
import uz.callguard.app.data.CallGuardDatabase
import uz.callguard.app.data.CallHistoryEntity
import uz.callguard.app.data.SettingsRepository
import uz.callguard.app.data.SpamNumberRepository
import uz.callguard.app.heuristic.HeuristicFilter
import uz.callguard.app.heuristic.HeuristicResult
import uz.callguard.app.heuristic.NumberFormatRules
import uz.callguard.app.notification.NotificationHelper
import uz.callguard.app.overlay.OverlayWarningTrigger
import uz.callguard.app.whitelist.ContactWhitelistManager

class CallScreeningServiceImpl : CallScreeningService() {

    companion object {
        private const val SCREENING_TIMEOUT_MS = 2500L
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onScreenCall(callDetails: Call.Details) {
        try {
            // 1. Если звонок исходящий — пропускаем
            if (callDetails.callDirection == Call.Details.DIRECTION_OUTGOING) {
                allowCall(callDetails)
                return
            }

            val rawNumber = callDetails.handle?.schemeSpecificPart
            if (rawNumber.isNullOrBlank()) {
                allowCall(callDetails)
                return
            }

            val normalizedNumber = NumberFormatRules.check(rawNumber).normalized
            if (normalizedNumber.isBlank()) {
                allowCall(callDetails)
                return
            }

            // Безопасный асинхронный вызов с жестким таймаутом (2.5 с)
            val isHandled = runBlocking(Dispatchers.IO) {
                withTimeoutOrNull(SCREENING_TIMEOUT_MS) {
                    processCall(callDetails, normalizedNumber)
                    true
                } ?: false
            }

            // Если произошел таймаут — защитный фолбэк: пропускаем звонок
            if (!isHandled) {
                Log.e("CallGuard", "Таймаут обработки звонка ($SCREENING_TIMEOUT_MS мс) для $normalizedNumber — фолбэк allowCall")
                allowCall(callDetails)
            }
        } catch (e: Exception) {
            Log.e("CallGuard", "Критическая ошибка при фильтрации звонка — фолбэк allowCall", e)
            allowCall(callDetails)
        }
    }

    private suspend fun processCall(callDetails: Call.Details, normalizedNumber: String) {
        val appContext = applicationContext
        val callHistoryDao = CallGuardDatabase.getInstance(appContext).callHistoryDao()

        // 2. Белый список контактов (I/O)
        val contactWhitelistManager = ContactWhitelistManager(appContext)
        if (contactWhitelistManager.isContact(normalizedNumber)) {
            Log.d("CallGuard", "Номер $normalizedNumber найден в контактах — пропускаем")
            allowCall(callDetails)
            asyncSaveCallHistory(callHistoryDao, normalizedNumber, isSpam = false, blockModeApplied = "NONE", reason = "contact_whitelist")
            return
        }

        // 3. Быстрая эвристика (память/регекс)
        val heuristicResult = HeuristicFilter.evaluate(normalizedNumber)

        // 4. Проверка в локальной Room БД (I/O)
        val spamRepository = SpamNumberRepository(appContext)
        val isKnownInDb = spamRepository.isKnownSpam(normalizedNumber)

        val isSpamCandidate = (heuristicResult.verdict == HeuristicResult.Verdict.BLOCK) ||
                (heuristicResult.verdict == HeuristicResult.Verdict.SUSPECT) ||
                isKnownInDb

        if (!isSpamCandidate) {
            allowCall(callDetails)
            asyncSaveCallHistory(callHistoryDao, normalizedNumber, isSpam = false, blockModeApplied = "NONE", reason = "clean")
            return
        }

        Log.w("CallGuard", "Обнаружен спам/скам звонок: $normalizedNumber (score=${heuristicResult.score}, inDb=$isKnownInDb)")

        // 5. Получение режима блокировки из DataStore (I/O)
        val settingsRepository = SettingsRepository(appContext)
        val blockMode = settingsRepository.getBlockMode()

        // 6. Формирование ответа Telecom
        val responseBuilder = CallResponse.Builder()

        when (blockMode) {
            BlockMode.REJECT -> {
                responseBuilder
                    .setDisallowCall(true)
                    .setRejectCall(true)
                    .setSkipNotification(false)
            }
            BlockMode.MUTE -> {
                responseBuilder
                    .setDisallowCall(false)
                    .setSilenceCall(true)
            }
            BlockMode.WARN -> {
                responseBuilder
                    .setDisallowCall(false)
            }
        }

        respondToCall(callDetails, responseBuilder.build())

        // 7. Сохранение истории в фоновом режиме (fire-and-forget, дедупликация в пределах 2 с)
        val riskScore = if (isKnownInDb) maxOf(heuristicResult.score, 80) else heuristicResult.score
        val reasonsList = if (isKnownInDb) heuristicResult.reasons + "known_in_local_spam_db" else heuristicResult.reasons
        val reasonsText = reasonsList.joinToString(", ")

        asyncSaveCallHistory(
            dao = callHistoryDao,
            number = normalizedNumber,
            isSpam = true,
            blockModeApplied = blockMode.name,
            reason = reasonsText
        )

        // 8. Оверлей и системное уведомление
        withContext(Dispatchers.Main) {
            OverlayWarningTrigger.show(
                appContext,
                normalizedNumber,
                riskScore,
                reasonsList,
            )
        }

        val notificationHelper = NotificationHelper(appContext)
        notificationHelper.showSpamWarningNotification(
            number = normalizedNumber,
            details = "Риск: $riskScore% | $reasonsText"
        )
    }

    private fun asyncSaveCallHistory(
        dao: uz.callguard.app.data.CallHistoryDao,
        number: String,
        isSpam: Boolean,
        blockModeApplied: String,
        reason: String
    ) {
        serviceScope.launch {
            try {
                dao.insertIfNotRecent(
                    CallHistoryEntity(
                        number = number,
                        isSpam = isSpam,
                        blockModeApplied = blockModeApplied,
                        reasonDetails = reason
                    )
                )
            } catch (e: Exception) {
                Log.e("CallGuard", "Ошибка записи истории вызова", e)
            }
        }
    }

    private fun allowCall(callDetails: Call.Details) {
        val response = CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSkipNotification(false)
            .build()
        respondToCall(callDetails, response)
    }
}
