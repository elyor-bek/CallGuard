package uz.callguard.app.heuristic

import java.time.LocalTime

/** Local hints are uncertain: they warn, never reject a call. */
object HeuristicFilter {
    private val international = Regex("^\\+[1-9][0-9]{6,14}$")
    private val unusualUzPrefix = Regex("^\\+9980[0-9].*")
    private val repeatedDigits = Regex("([0-9])\\1{6,}")

    fun evaluate(rawNumber: String, time: LocalTime = LocalTime.now(), isContact: Boolean = false): HeuristicResult {
        if (isContact) return HeuristicResult(HeuristicResult.Verdict.ALLOW, 0, emptyList())
        val reasons = mutableListOf<String>()
        val format = NumberFormatRules.check(rawNumber)
        if (time.hour >= 23 || time.hour < 7) reasons += "unusual_call_time"
        if (!format.isShortServiceNumber && !international.matches(format.normalized)) reasons += "malformed_number_format"
        if (unusualUzPrefix.matches(format.normalized) || repeatedDigits.containsMatchIn(format.normalized)) reasons += "unusual_number_pattern"
        if (VoipPrefixRanges.isLikelyVoip(format.normalized)) reasons += "voip_prefix_match"
        if (SpoofDetection.isSpoofedShortNumber(format.normalized)) reasons += "bank_short_number_spoof"
        return HeuristicResult(
            if (reasons.isNotEmpty()) HeuristicResult.Verdict.SUSPICIOUS else HeuristicResult.Verdict.ALLOW,
            (reasons.size * 30).coerceAtMost(90), reasons
        )
    }
}
