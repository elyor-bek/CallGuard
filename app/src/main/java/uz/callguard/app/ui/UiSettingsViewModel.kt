package uz.callguard.app.ui

import android.app.Application
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import uz.callguard.app.R
import uz.callguard.app.data.*
import uz.callguard.app.sms.SmsKeywordMatcher

class UiSettingsViewModel(application: Application) : CallListsViewModel(application) {
    private val settings = SettingsRepository(application)
    private val sms = CallGuardDatabase.getInstance(application).smsSpamDao()
    val preferences: StateFlow<UiPreferences?> = settings.uiPreferencesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val keywords = sms.observeKeywords().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val blocked = sms.observeBlocked().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun setTheme(mode: ThemeMode, errorMessage: String) = performAction(errorMessage) { settings.setThemeMode(mode) }
    fun setLanguage(language: AppLanguage, errorMessage: String) = performAction(errorMessage) { settings.setAppLanguage(language) }
    fun addKeyword(keyword: String, regex: Boolean, errorMessage: String) = performAction(errorMessage) {
        require(SmsKeywordMatcher.validate(keyword.trim(), regex))
        require(sms.getKeywords().size < 500)
        sms.insertKeyword(SmsKeywordEntity(keyword = keyword.trim(), isRegex = regex))
    }
    fun deleteKeyword(id: Long, errorMessage: String) = performAction(errorMessage) { sms.deleteKeyword(id) }
}
