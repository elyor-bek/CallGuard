package uz.callguard.app.sync

import uz.callguard.app.data.SpamBackupCodec
import uz.callguard.app.data.SpamNumberEntity

object GlobalSpamFeed {
    // Synthetic hashes, not phone numbers. Replace the source only after a real feed is configured.
    val stubJson = """[
        {"numberHash":"${"d".repeat(64)}","spamScore":85,"category":"fraud"},
        {"numberHash":"${"e".repeat(64)}","spamScore":45,"category":"advertising"}
    ]"""

    fun parse(json: String, now: Long): List<SpamNumberEntity> = SpamBackupCodec.decode(json).map {
        it.copy(source = "global_sync", isGlobal = true, phoneNumber = null, updatedAt = now)
    }
}
