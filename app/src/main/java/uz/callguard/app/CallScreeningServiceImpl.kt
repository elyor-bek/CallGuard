package uz.callguard.app

import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.TelecomManager
import kotlinx.coroutines.flow.first
import uz.callguard.app.heuristic.CallProtectionEngine
import uz.callguard.app.heuristic.ScreeningDecision
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
            val hidden = rawNumber.isNullOrBlank() || callDetails.handlePresentation != TelecomManager.PRESENTATION_ALLOWED
            val normalizedNumber = if (hidden) "" else NumberFormatRules.check(rawNumber.orEmpty()).normalized

            // Безопасный асинхронный вызов с жестким таймаутом (2.5 с)
            val isHandled = runBlocking(Dispatchers.IO) {
                withTimeoutOrNull(SCREENING_TIMEOUT_MS) {
                    processCall(callDetails, normalizedNumber, hidden)
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

    private suspend fun processCall(callDetails: Call.Details, normalizedNumber: String, hidden: Boolean) {
        val appContext = applicationContext
        val settingsRepository = SettingsRepository(appContext)
        val database by lazy { CallGuardDatabase.getInstance(appContext) }
        var spamEntry: uz.callguard.app.data.SpamNumberEntity? = null
        val decision = CallProtectionEngine().evaluate(
            number = normalizedNumber,
            hidden = hidden,
            readCourierEnd = { settingsRepository.courierModeEndFlow.first() },
            isContact = { ContactWhitelistManager(appContext).isContact(normalizedNumber) },
            readSettings = { settingsRepository.protectionRulesFlow.first() },
            readPatterns = { database.numberPatternDao().getAll().map { it.pattern } },
            readSpamEntry = { SpamNumberRepository(appContext).findNumber(normalizedNumber).also { spamEntry = it } }
        )
        if (decision != null) {
            if (decision.blocked) {
                respondToCall(callDetails, CallResponse.Builder()
                    .setDisallowCall(true)
                    .setRejectCall(true)
                    .setSkipNotification(true)
                    .build())
                postBlockedNotification(if (hidden) "Скрытый номер" else normalizedNumber)
            } else {
                allowCall(callDetails)
            }
            if (decision == ScreeningDecision.GLOBAL_WARNING) {
                OverlayWarningTrigger.show(appContext, normalizedNumber, spamEntry?.spamScore ?: 30, emptyList(),
                    category = spamEntry?.category ?: "unknown")
            }
            if (decision != ScreeningDecision.COURIER && decision != ScreeningDecision.HIDDEN_ALLOWED) {
                asyncSaveCallHistory(database.callHistoryDao(), if (hidden) "Скрытый номер" else normalizedNumber,
                    isSpam = decision.blocked || decision == ScreeningDecision.GLOBAL_WARNING, blockModeApplied = if (decision.blocked) BlockMode.REJECT.name else if (decision == ScreeningDecision.GLOBAL_WARNING) BlockMode.WARN.name else "NONE",
                    reason = decision.reason)
            }
            return
        }
        val callHistoryDao = database.callHistoryDao()

        // All deterministic checks already passed, including contacts and the spam database.
        val heuristicResult = HeuristicFilter.evaluate(normalizedNumber)
        allowCall(callDetails)
        val suspicious = heuristicResult.verdict == HeuristicResult.Verdict.SUSPICIOUS
        asyncSaveCallHistory(callHistoryDao, normalizedNumber, isSpam = suspicious,
            blockModeApplied = if (suspicious) BlockMode.WARN.name else "NONE",
            reason = if (suspicious) heuristicResult.reasons.joinToString(", ") else "clean")
        if (suspicious) {
            // OverlayWarningTrigger marshals to the main looper; never wait for UI inside the Telecom deadline.
            OverlayWarningTrigger.show(appContext, normalizedNumber, heuristicResult.score, heuristicResult.reasons)
        }
    }

    private fun postBlockedNotification(number: String) {
        serviceScope.launch {
            try {
                NotificationHelper(applicationContext).showBlockedCallNotification(number)
            } catch (error: Exception) {
                Log.e("CallGuard", "Не удалось показать уведомление о блокировке", error)
            }
        }
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
