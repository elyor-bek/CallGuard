package uz.callguard.app.heuristic

import uz.callguard.app.data.ProtectionRuleSettings

/** Prefix masks only: a single trailing star, no arbitrary regex or internal wildcards. */
object ProtectionRules {
    fun normalizePattern(input: String): String? {
        val compact = input.trim().replace(Regex("[\\s()\\-]"), "")
            .removeSuffix("...").removeSuffix("…")
        val international = if (compact.startsWith("00")) "+" + compact.drop(2) else compact
        if (!international.matches(Regex("\\+?[0-9]{1,15}\\*?"))) return null
        return international.removeSuffix("*") + "*"
    }

    fun blockingReason(
        normalizedNumber: String,
        hidden: Boolean,
        settings: ProtectionRuleSettings,
        patterns: List<String>
    ): String? {
        return settingsBlockingReason(normalizedNumber, hidden, settings)
            ?: if (!hidden && matchesPattern(normalizedNumber, patterns)) "rule_number_pattern" else null
    }

    fun settingsBlockingReason(number: String, hidden: Boolean, settings: ProtectionRuleSettings): String? {
        if (hidden) return if (settings.blockHiddenNumbers) "rule_hidden_number" else null
        if (settings.blockForeignNumbers && number.matches(Regex("\\+[0-9]{4,15}")) &&
            !number.startsWith("+998")) return "rule_foreign_number"
        return null
    }

    fun matchesPattern(number: String, patterns: List<String>): Boolean = patterns.any { pattern ->
        val canonical = normalizePattern(pattern)
        canonical != null && number.startsWith(canonical.removeSuffix("*"))
    }
}
