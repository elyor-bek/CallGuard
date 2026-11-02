package uz.callguard.app

import org.junit.Assert.*
import org.junit.Test
import uz.callguard.app.data.ProtectionRuleSettings
import uz.callguard.app.heuristic.ProtectionRules

class ProtectionRulesTest {
    @Test fun normalizesPrefixesAndExampleNotation() {
        for (input in listOf("+998 71...", "+998 (71)*", "0099871", "+99871…", "+99871")) {
            assertEquals("+99871*", ProtectionRules.normalizePattern(input))
        }
    }

    @Test fun rejectsEmptyOrOverbroadAndInvalidMasks() {
        for (input in listOf("", "*", "+", "***", "+998*71", "abc", "+998.71", "+1234567890123456")) {
            assertNull(input, ProtectionRules.normalizePattern(input))
        }
    }

    @Test fun hiddenRuleOnlyActsWhenEnabled() {
        assertEquals("rule_hidden_number", reason("", hidden = true, settings = ProtectionRuleSettings(blockHiddenNumbers = true)))
        assertNull(reason("", hidden = true, settings = ProtectionRuleSettings(blockForeignNumbers = true), patterns = listOf("+1*")))
    }

    @Test fun foreignRuleKeepsUzbekAndLocalShortNumbers() {
        val settings = ProtectionRuleSettings(blockForeignNumbers = true)
        assertEquals("rule_foreign_number", reason("+447700900123", settings = settings))
        for (number in listOf("+998901234567", "1234", "901234567", "", "unknown")) {
            assertNull(number, reason(number, settings = settings))
        }
        assertNull(reason("+447700900123"))
    }

    @Test fun masksMatchPrefixesRatherThanSubstrings() {
        assertEquals("rule_number_pattern", reason("+998711234567", patterns = listOf("+99871*")))
        assertNull(reason("+998901234567", patterns = listOf("+99871*")))
        assertNull(reason("+199871123456", patterns = listOf("+99871*")))
        assertNull(reason("+998711234567", patterns = listOf("*", "")))
    }

    private fun reason(number: String, hidden: Boolean = false,
        settings: ProtectionRuleSettings = ProtectionRuleSettings(), patterns: List<String> = emptyList()) =
        ProtectionRules.blockingReason(number, hidden, settings, patterns)
}
