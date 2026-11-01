package uz.callguard.app.heuristic

/**
 * Итог работы HeuristicFilter по одному номеру.
 *
 * @param verdict итоговое решение первого (быстрого) слоя
 * @param score агрегированный риск-скор 0..100 (чем выше — тем подозрительнее)
 * @param reasons список сработавших правил, для логов/отладки/будущего ML-фичесета
 */
data class HeuristicResult(
    val verdict: Verdict,
    val score: Int,
    val reasons: List<String>
) {
    enum class Verdict {
        ALLOW,      // явно чистый номер
        UNKNOWN,    // эвристика не дала уверенного ответа — смотреть в LocalSpamDB
        SUSPECT,    // похоже на спам/скам, но не 100% — предупредить пользователя
        BLOCK       // высокая уверенность скама/спуфинга
    }
}
