package uz.callguard.app.ui

import uz.callguard.app.R
import uz.callguard.app.data.CallHistoryEntity
import uz.callguard.app.heuristic.NumberFormatRules
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal enum class HistorySection(val titleRes: Int) {
    TODAY(R.string.today), YESTERDAY(R.string.yesterday), EARLIER(R.string.earlier)
}

data class CallHistoryGroup(
    val number: String,
    val day: LocalDate,
    val calls: List<CallHistoryEntity>
) {
    val key: String get() = "call:$day:$number"
    val latest: CallHistoryEntity get() = calls.first()
    val count: Int get() = calls.size
}

internal fun groupDailyCalls(calls: List<CallHistoryEntity>, zone: ZoneId): List<CallHistoryGroup> =
    calls.sortedByDescending { it.timestampMs }
        .groupBy { NumberFormatRules.check(it.number).normalized to Instant.ofEpochMilli(it.timestampMs).atZone(zone).toLocalDate() }
        .map { (key, entries) -> CallHistoryGroup(key.first, key.second, entries) }

internal fun groupCallHistory(
    calls: List<CallHistoryGroup>,
    today: LocalDate
): Map<HistorySection, List<CallHistoryGroup>> {
    val groups = calls.groupBy {
        when (it.day) {
            today -> HistorySection.TODAY
            today.minusDays(1) -> HistorySection.YESTERDAY
            else -> HistorySection.EARLIER
        }
    }
    return HistorySection.entries.mapNotNull { section -> groups[section]?.let { section to it } }.toMap()
}
