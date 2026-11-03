package uz.callguard.app

import org.junit.Assert.*
import org.junit.Test
import uz.callguard.app.data.SmsKeywordEntity
import uz.callguard.app.sms.SmsKeywordMatcher

class SmsKeywordMatcherTest {
    @Test fun literalMatchingIsCaseInsensitiveAndNotRegex() {
        val rule = SmsKeywordEntity(keyword = "WIN.*PRIZE")
        assertNull(SmsKeywordMatcher.match("win a prize", listOf(rule)))
        assertEquals(rule, SmsKeywordMatcher.match("WIN.*prize today", listOf(rule)))
    }
    @Test fun supportsRussianAndUzbekLiterals() {
        for (word in listOf("ВЫИГРЫШ", "YUTUQ")) {
            val rule = SmsKeywordEntity(keyword = word)
            assertEquals(rule, SmsKeywordMatcher.match("test ${word.lowercase()} test", listOf(rule)))
        }
    }
    @Test fun regexMatchesAndInvalidLegacyRulesAreIgnored() {
        val rule = SmsKeywordEntity(keyword = "win\\s+\\d+", isRegex = true)
        assertEquals(rule, SmsKeywordMatcher.match("WIN 100 now", listOf(SmsKeywordEntity(keyword = "[", isRegex = true), rule)))
    }
    @Test fun rejectsUnsupportedRegexAndEmptyPatterns() {
        assertFalse(SmsKeywordMatcher.validate("", false))
        assertFalse(SmsKeywordMatcher.validate(" ", true))
        assertFalse(SmsKeywordMatcher.validate("(?=secret)", true))
        assertFalse(SmsKeywordMatcher.validate("x".repeat(129), false))
    }
    @Test fun pathologicalBacktrackingPatternDoesNotHang() {
        assertNull(SmsKeywordMatcher.match("a".repeat(10_000) + "!", listOf(SmsKeywordEntity(keyword = "^(a+)+$", isRegex = true))))
    }
    @Test fun emptyTextAndNoRulesDoNotMatch() {
        assertNull(SmsKeywordMatcher.match("", listOf(SmsKeywordEntity(keyword = "hello"))))
        assertNull(SmsKeywordMatcher.match("hello", emptyList()))
    }
}
