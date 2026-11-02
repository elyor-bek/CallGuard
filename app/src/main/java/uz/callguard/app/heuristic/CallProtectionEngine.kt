package uz.callguard.app.heuristic

import uz.callguard.app.data.SpamNumberEntity
import uz.callguard.app.data.SpamPolicy
import uz.callguard.app.data.CourierMode
import uz.callguard.app.data.ProtectionRuleSettings

enum class ScreeningDecision(val blocked: Boolean, val reason: String) {
    COURIER(false, "courier_mode"),
    CONTACT(false, "contact_whitelist"),
    HIDDEN_ALLOWED(false, "hidden_number_allowed"),
    HIDDEN_BLOCKED(true, "rule_hidden_number"),
    FOREIGN_BLOCKED(true, "rule_foreign_number"),
    PATTERN_BLOCKED(true, "rule_number_pattern"),
    GLOBAL_BLOCKED(true, "global_high_score"),
    GLOBAL_WARNING(false, "global_medium_score"),
    SPAM_BLOCKED(true, "known_in_local_spam_db")
}

/** Lazy reads enforce precedence: a match must never query or apply later filters. */
class CallProtectionEngine {
    suspend fun evaluate(
        number: String,
        hidden: Boolean,
        readCourierEnd: suspend () -> Long,
        isContact: suspend () -> Boolean,
        readSettings: suspend () -> ProtectionRuleSettings,
        readPatterns: suspend () -> List<String>,
        readSpamEntry: suspend () -> SpamNumberEntity?,
        now: () -> Long = System::currentTimeMillis
    ): ScreeningDecision? {
        if (CourierMode.isActive(readCourierEnd(), now())) return ScreeningDecision.COURIER
        if (!hidden && number.isNotBlank() && isContact()) return ScreeningDecision.CONTACT
        if (!hidden && number.isNotBlank()) {
            val entry = readSpamEntry()
            when (SpamPolicy.classify(entry, now())) {
                SpamPolicy.Action.BLOCK -> return if (entry!!.isGlobal) ScreeningDecision.GLOBAL_BLOCKED else ScreeningDecision.SPAM_BLOCKED
                SpamPolicy.Action.WARN -> return ScreeningDecision.GLOBAL_WARNING
                SpamPolicy.Action.NONE -> Unit
            }
        }
        val reason = ProtectionRules.settingsBlockingReason(number, hidden, readSettings())
        if (reason == "rule_hidden_number") return ScreeningDecision.HIDDEN_BLOCKED
        if (reason == "rule_foreign_number") return ScreeningDecision.FOREIGN_BLOCKED
        if (hidden || number.isBlank()) return ScreeningDecision.HIDDEN_ALLOWED
        if (ProtectionRules.matchesPattern(number, readPatterns())) return ScreeningDecision.PATTERN_BLOCKED
        return null
    }
}
