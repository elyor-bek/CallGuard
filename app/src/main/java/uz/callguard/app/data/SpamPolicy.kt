package uz.callguard.app.data

object SpamPolicy {
    const val TTL_MS = 14 * 24 * 60 * 60_000L
    enum class Action { NONE, WARN, BLOCK }

    fun classify(entry: SpamNumberEntity?, now: Long = System.currentTimeMillis()): Action = when {
        entry == null -> Action.NONE
        !entry.isGlobal -> Action.BLOCK
        entry.updatedAt < now - TTL_MS -> Action.NONE
        entry.spamScore >= 80 -> Action.BLOCK
        entry.spamScore >= 30 -> Action.WARN
        else -> Action.NONE
    }
}
