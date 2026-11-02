package uz.callguard.app.ui

import uz.callguard.app.R
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.callguard.app.data.CallGuardDatabase
import uz.callguard.app.data.NumberPatternEntity
import uz.callguard.app.data.ProtectionRuleSettings
import uz.callguard.app.data.SettingsRepository
import uz.callguard.app.heuristic.ProtectionRules

data class RulesState(
    val settings: ProtectionRuleSettings,
    val patterns: List<NumberPatternEntity>
)

class RulesViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = SettingsRepository(application)
    private val dao = CallGuardDatabase.getInstance(application).numberPatternDao()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    val state: StateFlow<RulesState?> = combine(settings.protectionRulesFlow, dao.observeAll()) { filters, patterns ->
        RulesState(filters, patterns)
    }.catch { failure ->
        if (failure is CancellationException) throw failure
        _error.value = getApplication<android.app.Application>().getString(R.string.rules_load_error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun dismissError() { _error.value = null }
    fun setBlockHiddenNumbers(enabled: Boolean) = save { settings.setBlockHiddenNumbers(enabled) }
    fun setBlockForeignNumbers(enabled: Boolean) = save { settings.setBlockForeignNumbers(enabled) }
    fun deletePattern(pattern: String) = save { dao.delete(pattern) }

    fun addPattern(input: String, onAdded: () -> Unit) {
        val pattern = ProtectionRules.normalizePattern(input)
        if (pattern == null) {
            _error.value = getApplication<android.app.Application>().getString(R.string.mask_invalid)
            return
        }
        save {
            dao.insert(NumberPatternEntity(pattern))
            withContext(Dispatchers.Main.immediate) { onAdded() }
        }
    }

    private fun save(action: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _error.value = getApplication<android.app.Application>().getString(R.string.rule_save_error)
            } finally {
                _busy.value = false
            }
        }
    }
}
