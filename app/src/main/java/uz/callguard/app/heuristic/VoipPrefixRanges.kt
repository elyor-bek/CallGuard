package uz.callguard.app.heuristic

/**
 * Диапазоны префиксов, часто ассоциируемые с VoIP/SIP-транками,
 * через которые массово льют скам/спам звонки (в т.ч. подмену caller ID).
 * Placeholder — заменить реальными диапазонами после анализа DataSourcing.
 */
object VoipPrefixRanges {

    private val SUSPECT_VOIP_PREFIXES = setOf(
        "78", "79", "55", "50" // TODO: заменить реальными данными
    )

    fun isLikelyVoip(normalizedNumber: String): Boolean {
        if (!normalizedNumber.startsWith("+998") || normalizedNumber.length != 13) return false
        val prefix = normalizedNumber.removePrefix("+998").take(2)
        return SUSPECT_VOIP_PREFIXES.contains(prefix)
    }
}
