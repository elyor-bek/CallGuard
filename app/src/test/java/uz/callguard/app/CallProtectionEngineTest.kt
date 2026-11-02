package uz.callguard.app

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import uz.callguard.app.data.SpamNumberEntity
import uz.callguard.app.data.ProtectionRuleSettings
import uz.callguard.app.heuristic.CallProtectionEngine
import uz.callguard.app.heuristic.ScreeningDecision

class CallProtectionEngineTest {
    private val reads = mutableListOf<String>()

    private suspend fun evaluate(
        end: Long = 0, now: Long = 100, contact: Boolean = false, hidden: Boolean = false,
        settings: ProtectionRuleSettings = ProtectionRuleSettings(),
        patterns: List<String> = emptyList(), known: Boolean = false,
        number: String = "+998711234567"
    ) = CallProtectionEngine().evaluate(
        number, hidden,
        readCourierEnd = { reads += "courier"; end },
        isContact = { reads += "contacts"; contact },
        readSettings = { reads += "settings"; settings },
        readPatterns = { reads += "masks"; patterns },
        readSpamEntry = { reads += "spam"; if (known) SpamNumberEntity("a".repeat(64), "manual") else null },
        now = { now }
    )

    @Test fun courierSkipsEveryFilterEvenForBlockedNumber() = runBlocking {
        assertEquals(ScreeningDecision.COURIER, evaluate(end = 101, contact = true, known = true, patterns = listOf("+998*")))
        assertEquals(listOf("courier"), reads)
    }

    @Test fun contactsOverrideMasksForeignRuleAndSpamDatabase() = runBlocking {
        assertEquals(ScreeningDecision.CONTACT, evaluate(contact = true, number = "+447700900123", known = true,
            settings = ProtectionRuleSettings(blockForeignNumbers = true), patterns = listOf("+44*")))
        assertEquals(listOf("courier", "contacts"), reads)
    }

    @Test fun hiddenRuleRejectsWithoutReadingMasksOrSpam() = runBlocking {
        assertEquals(ScreeningDecision.HIDDEN_BLOCKED, evaluate(hidden = true,
            settings = ProtectionRuleSettings(blockHiddenNumbers = true)))
        assertEquals(listOf("courier", "settings"), reads)
    }

    @Test fun hiddenNumbersAreAllowedWhenRuleIsOff() = runBlocking {
        assertEquals(ScreeningDecision.HIDDEN_ALLOWED, evaluate(hidden = true, known = true))
        assertEquals(listOf("courier", "settings"), reads)
    }

    @Test fun foreignSettingIsCheckedAfterContactsAndBeforeMasks() = runBlocking {
        assertEquals(ScreeningDecision.FOREIGN_BLOCKED, evaluate(number = "+447700900123",
            settings = ProtectionRuleSettings(blockForeignNumbers = true), known = false))
        assertEquals(listOf("courier", "contacts", "spam", "settings"), reads)
    }

    @Test fun localDatabaseTakesPriorityOverPrefixRules() = runBlocking {
        assertEquals(ScreeningDecision.SPAM_BLOCKED, evaluate(patterns = listOf("+99871*"), known = true))
        assertEquals(listOf("courier", "contacts", "spam"), reads)
    }

    @Test fun knownSpamAlwaysProducesRejectDecision() = runBlocking {
        val result = evaluate(known = true)
        assertEquals(ScreeningDecision.SPAM_BLOCKED, result)
        assertTrue(result!!.blocked)
        assertEquals(listOf("courier", "contacts", "spam"), reads)
    }

    @Test fun expiredPauseRunsAllChecksInOrder() = runBlocking {
        assertNull(evaluate(end = 100, now = 100))
        assertEquals(listOf("courier", "contacts", "spam", "settings", "masks"), reads)
    }
}
