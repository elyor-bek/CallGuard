package uz.callguard.app

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import uz.callguard.app.data.*
import uz.callguard.app.heuristic.*
import uz.callguard.app.sync.GlobalSpamFeed

class HybridSpamTest {
    private val now = 2_000_000_000_000L
    private fun global(score: Int, updated: Long = now) = SpamNumberEntity("a".repeat(64), "global_sync",
        spamScore = score, isGlobal = true, updatedAt = updated, category = "advertising")

    @Test fun thresholdsAreExact() {
        assertEquals(SpamPolicy.Action.NONE, SpamPolicy.classify(global(29), now))
        assertEquals(SpamPolicy.Action.WARN, SpamPolicy.classify(global(30), now))
        assertEquals(SpamPolicy.Action.WARN, SpamPolicy.classify(global(79), now))
        assertEquals(SpamPolicy.Action.BLOCK, SpamPolicy.classify(global(80), now))
        assertEquals(SpamPolicy.Action.BLOCK, SpamPolicy.classify(global(100), now))
    }
    @Test fun expiredGlobalIgnoredButLocalNeverExpires() {
        assertEquals(SpamPolicy.Action.NONE, SpamPolicy.classify(global(100, now - SpamPolicy.TTL_MS - 1), now))
        assertEquals(SpamPolicy.Action.BLOCK, SpamPolicy.classify(global(100, now - SpamPolicy.TTL_MS), now))
        assertEquals(SpamPolicy.Action.BLOCK, SpamPolicy.classify(global(0, 0).copy(isGlobal = false), now))
    }
    @Test fun feedIsHashOnlyAndRefreshesTimestamp() {
        val rows = GlobalSpamFeed.parse("""[{"numberHash":"${"d".repeat(64)}","spamScore":85,"category":"fraud"},{"numberHash":"${"e".repeat(64)}","spamScore":45,"category":"advertising"}]""", now)
        assertEquals(2, rows.size)
        assertTrue(rows.all { it.isGlobal && it.phoneNumber == null && it.updatedAt == now })
    }
    @Test fun backupsPreserveHybridMetadataAndRejectBadScores() {
        val row = global(45)
        assertEquals(listOf(row), SpamBackupCodec.decode(SpamBackupCodec.encode(listOf(row))))
        try {
            SpamBackupCodec.decode("""[{"numberHash":"${"a".repeat(64)}","spamScore":101}]""")
            fail("score outside 0..100 must fail")
        } catch (_: IllegalArgumentException) { }
    }
    @Test fun globalWarningsAndBlocksSkipLowerPriorityFilters() = runBlocking {
        for (score in listOf(30, 79, 80)) {
            val result = CallProtectionEngine().evaluate("+998901234567", false, { 0 }, { false },
                { error("Database decisions must precede settings") },
                { error("Database decisions must precede masks") }, { global(score) }, { now })
            assertEquals(if (score >= 80) ScreeningDecision.GLOBAL_BLOCKED else ScreeningDecision.GLOBAL_WARNING, result)
        }
    }
}
