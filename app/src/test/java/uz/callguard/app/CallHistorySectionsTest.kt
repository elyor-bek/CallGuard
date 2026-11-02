package uz.callguard.app

import org.junit.Assert.*
import org.junit.Test
import uz.callguard.app.data.CallHistoryEntity
import uz.callguard.app.ui.HistorySection
import uz.callguard.app.ui.groupCallHistory
import uz.callguard.app.ui.groupDailyCalls
import java.time.LocalDate
import java.time.ZoneId

class CallHistorySectionsTest {
    private val zone = ZoneId.of("Asia/Tashkent")
    private val today = LocalDate.of(2026, 11, 1)

    private fun call(id: Long, day: LocalDate, hour: Int, number: String = "+998901234567") = CallHistoryEntity(
        id = id, number = number, isSpam = false, blockModeApplied = "none",
        timestampMs = day.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    )

    @Test fun mergesRepeatedNumbersWithinDayAndSortsByLatestCall() {
        val recent = call(1, today, 15)
        val early = call(2, today, 8)
        val third = call(3, today, 12, "+998 (90) 123-45-67")
        val other = call(4, today, 14, "+998711234567")
        val groups = groupDailyCalls(listOf(other, early, recent, third), zone)
        assertEquals(2, groups.size)
        assertEquals(3, groups.first().count)
        assertEquals(listOf(1L, 3L, 2L), groups.first().calls.map { it.id })
        assertEquals(recent, groups.first().latest)
        assertEquals(other, groups.last().latest)
    }

    @Test fun keepsSameNumberSeparateOnDifferentDaysIncludingEarlierSection() {
        val calls = listOf(call(1, today, 15), call(2, today.minusDays(1), 23),
            call(3, today.minusDays(3), 12), call(4, today.minusDays(4), 12))
        val daily = groupDailyCalls(calls.reversed(), zone)
        assertEquals(4, daily.size)
        val sections = groupCallHistory(daily, today)
        assertEquals(listOf(HistorySection.TODAY, HistorySection.YESTERDAY, HistorySection.EARLIER), sections.keys.toList())
        assertEquals(2, sections[HistorySection.EARLIER]?.size)
        assertEquals(4, daily.map { it.key }.distinct().size)
    }

    @Test fun keyRemainsStableWhenAnotherCallArrivesOrLatestIsDeleted() {
        val first = call(1, today, 8)
        val second = call(2, today, 15)
        assertEquals(groupDailyCalls(listOf(first), zone).first().key,
            groupDailyCalls(listOf(second, first), zone).first().key)
    }

    @Test fun reclassifiesAtLocalMidnight() {
        val midnight = call(1, today, 0)
        val daily = groupDailyCalls(listOf(midnight), zone)
        assertEquals(daily, groupCallHistory(daily, today)[HistorySection.TODAY])
        assertEquals(daily, groupCallHistory(daily, today.plusDays(1))[HistorySection.YESTERDAY])
    }

    @Test fun usesCalendarDaysAcrossDaylightSavingTransition() {
        val dstZone = ZoneId.of("America/New_York")
        val day = LocalDate.of(2026, 3, 9)
        val previousMidnight = call(1, today, 0).copy(
            timestampMs = day.minusDays(1).atStartOfDay(dstZone).toInstant().toEpochMilli())
        val daily = groupDailyCalls(listOf(previousMidnight), dstZone)
        assertEquals(daily, groupCallHistory(daily, day)[HistorySection.YESTERDAY])
    }

    @Test fun emptyHistoryHasNoSections() {
        assertTrue(groupCallHistory(groupDailyCalls(emptyList(), zone), today).isEmpty())
    }
}
