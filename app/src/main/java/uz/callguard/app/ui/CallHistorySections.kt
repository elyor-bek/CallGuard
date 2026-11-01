package uz.callguard.app.ui

import uz.callguard.app.data.CallHistoryEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal enum class HistorySection(val title: String) {
    TODAY("Сегодня"), YESTERDAY("Вчера"), EARLIER("Ранее")
}

internal fun groupCallHistory(
    calls: List<CallHistoryEntity>,
    today: LocalDate,
    zone: ZoneId
): Map<HistorySection, List<CallHistoryEntity>> {
    val sorted = calls.sortedByDescending { it.timestampMs }
    val groups = sorted.groupBy {
        when (Instant.ofEpochMilli(it.timestampMs).atZone(zone).toLocalDate()) {
            today -> HistorySection.TODAY
            today.minusDays(1) -> HistorySection.YESTERDAY
            else -> HistorySection.EARLIER
        }
    }
    return HistorySection.entries.mapNotNull { section -> groups[section]?.let { section to it } }.toMap()
}
