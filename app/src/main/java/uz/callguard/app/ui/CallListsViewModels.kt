package uz.callguard.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uz.callguard.app.data.CallGuardDatabase
import uz.callguard.app.data.CallHistoryEntity
import uz.callguard.app.data.SpamNumberEntity
import uz.callguard.app.data.SpamNumberRepository

abstract class CallListsViewModel(application: Application) : AndroidViewModel(application) {
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun dismissError() { _error.value = null }

    protected fun performAction(action: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _error.value = "Не удалось сохранить изменения. Попробуйте ещё раз."
            } finally {
                _busy.value = false
            }
        }
    }
}

class CallListViewModel(application: Application) : CallListsViewModel(application) {
    private val historyDao = CallGuardDatabase.getInstance(application).callHistoryDao()
    private val spamRepository = SpamNumberRepository(application)
    val history: StateFlow<List<CallHistoryEntity>?> = historyDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun reportSpam(call: CallHistoryEntity) = performAction { spamRepository.reportCallAsSpam(call) }
    fun deleteHistory(id: Long) = performAction { historyDao.deleteById(id) }
}

class SpamNumbersViewModel(application: Application) : CallListsViewModel(application) {
    private val repository = SpamNumberRepository(application)
    val numbers: StateFlow<List<SpamNumberEntity>?> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun addNumber(number: String) = performAction { repository.addNumber(number, source = "manual") }
    fun deleteNumber(hash: String) = performAction { repository.removeNumberByHash(hash) }
}
