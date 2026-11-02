package uz.callguard.app.data

object CourierMode {
    const val HALF_HOUR_MS = 30 * 60_000L
    const val ONE_HOUR_MS = 60 * 60_000L

    fun isActive(endTimestampMs: Long, nowMs: Long): Boolean = nowMs < endTimestampMs

    fun remainingMinutes(endTimestampMs: Long, nowMs: Long): Long {
        if (!isActive(endTimestampMs, nowMs)) return 0
        val difference = endTimestampMs - nowMs
        return difference / 60_000 + if (difference % 60_000 != 0L) 1 else 0
    }
}
