package uz.callguard.app

import org.junit.Assert.assertEquals
import org.junit.Test
import uz.callguard.app.data.CallHistoryEntity
import uz.callguard.app.ui.HistorySection
import uz.callguard.app.ui.groupCallHistory
import java.time.LocalDate
import java.time.ZoneId

class CallHistorySectionsTest {
    private val zone = ZoneId.of("Asia/Tashkent")
    private val today = LocalDate.of(2026, 11, 1)

    private fun call(id: Long, day: LocalDate, hour: Int) = CallHistoryEntity(
        id = id, number = "+998901234567", isSpam = false, blockModeApplied = "none",
        timestampMs = day.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    )

    @Test fun sortsCallsAndOrdersSections() {
        val recent = call(1, today, 15)
        val early = call(2, today, 8)
        val yesterday = call(3, today.minusDays(1), 23)
        val old = call(4, today.minusDays(3), 12)
        val groups = groupCallHistory(listOf(old, early, yesterday, recent), today, zone)
        assertEquals(listOf(HistorySection.TODAY, HistorySection.YESTERDAY, HistorySection.EARLIER), groups.keys.toList())
        assertEquals(listOf(recent, early), groups[HistorySection.TODAY])
        assertEquals(listOf(yesterday), groups[HistorySection.YESTERDAY])
        assertEquals(listOf(old), groups[HistorySection.EARLIER])
    }

    @Test fun usesLocalMidnightAndReclassifiesOnNextDay() {
        val midnight = call(1, today, 0)
        val groups = groupCallHistory(listOf(midnight), today, zone)
        assertEquals(listOf(midnight), groups[HistorySection.TODAY])
        assertEquals(listOf(midnight), groupCallHistory(listOf(midnight), today.plusDays(1), zone)[HistorySection.YESTERDAY])
    }

    @Test fun usesCalendarDaysAcrossDaylightSavingTransition() {
        val dstZone = ZoneId.of("America/New_York")
        val day = LocalDate.of(2026, 3, 9)
        val previousMidnight = call(1, today, 0).copy(
            timestampMs = day.minusDays(1).atStartOfDay(dstZone).toInstant().toEpochMilli())
        assertEquals(listOf(previousMidnight), groupCallHistory(listOf(previousMidnight), day, dstZone)[HistorySection.YESTERDAY])
    }

    @Test fun emptyHistoryHasNoSections() {
        assertEquals(emptyMap<HistorySection, List<CallHistoryEntity>>(), groupCallHistory(emptyList(), today, zone))
    }
}
