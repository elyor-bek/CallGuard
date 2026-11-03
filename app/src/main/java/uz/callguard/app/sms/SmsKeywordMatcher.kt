package uz.callguard.app.sms

import com.google.re2j.Pattern
import uz.callguard.app.data.SmsKeywordEntity

object SmsKeywordMatcher {
    fun validate(keyword: String, isRegex: Boolean): Boolean {
        if (keyword.isBlank() || keyword.length > 128) return false
        return !isRegex || runCatching { Pattern.compile(keyword, Pattern.CASE_INSENSITIVE) }.isSuccess
    }

    fun match(text: String, rules: List<SmsKeywordEntity>): SmsKeywordEntity? {
        if (text.isBlank()) return null
        val boundedText = text.take(16_384)
        return rules.firstOrNull { rule ->
            if (!validate(rule.keyword, rule.isRegex)) false
            else if (!rule.isRegex) boundedText.contains(rule.keyword, ignoreCase = true)
            else Pattern.compile(rule.keyword, Pattern.CASE_INSENSITIVE).matcher(boundedText).find()
        }
    }
}
