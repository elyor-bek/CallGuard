package uz.callguard.app

import org.junit.Assert.*
import org.junit.Test
import uz.callguard.app.heuristic.HeuristicFilter
import uz.callguard.app.heuristic.HeuristicResult.Verdict
import java.time.LocalTime

class HeuristicFilterTest {
    private val normal = "+998901234567"
    @Test fun nightBoundariesAreLocalAndExclusiveAtSeven() {
        for (time in listOf(LocalTime.of(23, 0), LocalTime.MIDNIGHT, LocalTime.of(6, 59))) {
            val result = HeuristicFilter.evaluate(normal, time)
            assertEquals(Verdict.SUSPICIOUS, result.verdict)
            assertTrue(result.reasons.contains("unusual_call_time"))
        }
        for (time in listOf(LocalTime.of(7, 0), LocalTime.of(22, 59))) {
            assertEquals(Verdict.ALLOW, HeuristicFilter.evaluate(normal, time).verdict)
        }
    }
    @Test fun contactsNeverTriggerHeuristics() {
        assertEquals(Verdict.ALLOW, HeuristicFilter.evaluate("+998000000000", LocalTime.MIDNIGHT, true).verdict)
    }
    @Test fun abnormalLengthAndPrefixWarnDuringDay() {
        for (number in listOf("123", "+998001234567", "+998000000000", "+1234567890123456")) {
            assertEquals(number, Verdict.SUSPICIOUS, HeuristicFilter.evaluate(number, LocalTime.NOON).verdict)
        }
    }
    @Test fun validForeignNumberIsNotMalformed() {
        val result = HeuristicFilter.evaluate("+447700900123", LocalTime.NOON)
        assertFalse(result.reasons.contains("malformed_number_format"))
    }
    @Test fun shortServiceNumbersAreAllowedDuringDayButWarnAtNight() {
        assertEquals(Verdict.ALLOW, HeuristicFilter.evaluate("1000", LocalTime.NOON).verdict)
        assertEquals(Verdict.SUSPICIOUS, HeuristicFilter.evaluate("1000", LocalTime.MIDNIGHT).verdict)
    }
    @Test fun multipleHintsNeverProduceBlockingVerdict() {
        assertEquals(Verdict.SUSPICIOUS, HeuristicFilter.evaluate("+998000000000", LocalTime.MIDNIGHT).verdict)
    }
}
