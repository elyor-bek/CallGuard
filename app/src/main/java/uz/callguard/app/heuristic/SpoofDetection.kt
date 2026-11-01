package uz.callguard.app.heuristic

/**
 * Ловит попытки спуфинга под короткие "банковские"/сервисные номера.
 * Placeholder-список — свериться с реальными номерами банков/операторов.
 */
object SpoofDetection {

    private val KNOWN_BANK_SHORT_NUMBERS = setOf(
        "1000", "5959", "3232", "9990" // TODO: заменить реальными данными
    )

    fun isSpoofedShortNumber(normalized: String): Boolean {
        if (KNOWN_BANK_SHORT_NUMBERS.contains(normalized)) return false
        return KNOWN_BANK_SHORT_NUMBERS.any { shortNum ->
            normalized.length > shortNum.length && normalized.endsWith(shortNum)
        }
    }
}
