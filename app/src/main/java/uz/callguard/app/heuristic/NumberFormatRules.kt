package uz.callguard.app.heuristic

/**
 * Первый, самый дешёвый чек: адекватен ли вообще формат номера
 * для узбекского контекста (+998, локальные короткие номера банков/операторов).
 */
object NumberFormatRules {

    private val UZ_MOBILE_REGEX = Regex("^\\+998\\d{9}$")

    // Известные диапазоны кодов операторов Узбекистана (XX после +998)
    // TODO: свериться с актуальным списком Beeline/Ucell/Mobiuz/UMS/Perfectum
    private val KNOWN_OPERATOR_PREFIXES = setOf(
        "90", "91", "93", "94", "95", "97", "98", "99",
        "33", "88", "20", "77", "71"
    )

    private val SHORT_SERVICE_NUMBER_REGEX = Regex("^\\d{4,5}$")

    data class FormatCheck(
        val isWellFormed: Boolean,
        val isKnownOperatorPrefix: Boolean,
        val isShortServiceNumber: Boolean,
        val normalized: String
    )

    fun check(rawNumber: String): FormatCheck {
        val normalized = normalize(rawNumber)

        val isShort = SHORT_SERVICE_NUMBER_REGEX.matches(normalized)
        if (isShort) {
            return FormatCheck(
                isWellFormed = true,
                isKnownOperatorPrefix = false,
                isShortServiceNumber = true,
                normalized = normalized
            )
        }

        val wellFormed = UZ_MOBILE_REGEX.matches(normalized)
        val prefix = normalized.removePrefix("+998").take(2)
        val knownPrefix = wellFormed && KNOWN_OPERATOR_PREFIXES.contains(prefix)

        return FormatCheck(
            isWellFormed = wellFormed,
            isKnownOperatorPrefix = knownPrefix,
            isShortServiceNumber = false,
            normalized = normalized
        )
    }

    private fun normalize(rawNumber: String): String {
        var n = rawNumber.trim().replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
        if (n.startsWith("00")) n = "+" + n.substring(2)
        if (n.startsWith("8") && n.length == 12) n = "+998" + n.substring(1)
        if (!n.startsWith("+") && n.startsWith("998")) n = "+$n"
        return n
    }
}
