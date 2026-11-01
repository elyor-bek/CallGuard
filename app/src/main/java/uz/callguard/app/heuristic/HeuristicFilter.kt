package uz.callguard.app.heuristic

/**
 * Быстрый первый слой. Работает синхронно, без сети/БД — только regex и множества в памяти.
 * Если возвращает BLOCK/ALLOW с высокой уверенностью — второй слой (LocalSpamDB)
 * можно даже не спрашивать.
 */
object HeuristicFilter {

    fun evaluate(rawNumber: String): HeuristicResult {
        val reasons = mutableListOf<String>()
        var score = 0

        val format = NumberFormatRules.check(rawNumber)

        if (!format.isWellFormed && !format.isShortServiceNumber) {
            score += 35
            reasons += "malformed_number_format"
        }

        if (format.isWellFormed && !format.isKnownOperatorPrefix) {
            score += 15
            reasons += "unknown_operator_prefix"
        }

        if (VoipPrefixRanges.isLikelyVoip(format.normalized)) {
            score += 30
            reasons += "voip_prefix_match"
        }

        val spoofed = SpoofDetection.isSpoofedShortNumber(format.normalized)
        if (spoofed) {
            score += 50
            reasons += "bank_short_number_spoof"
        }

        if (format.isShortServiceNumber && !spoofed) {
            return HeuristicResult(
                verdict = HeuristicResult.Verdict.ALLOW,
                score = 0,
                reasons = listOf("known_short_service_number")
            )
        }

        val verdict = when {
            score >= 60 -> HeuristicResult.Verdict.BLOCK
            score >= 30 -> HeuristicResult.Verdict.SUSPECT
            format.isWellFormed && format.isKnownOperatorPrefix -> HeuristicResult.Verdict.ALLOW
            else -> HeuristicResult.Verdict.UNKNOWN
        }

        return HeuristicResult(
            verdict = verdict,
            score = score.coerceIn(0, 100),
            reasons = reasons
        )
    }
}
