package uz.callguard.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.callguard.app.R
import uz.callguard.app.data.SpamFeedEntity
import uz.callguard.app.data.SpamFeedRepository

class FeedsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SpamFeedRepository(application)
    private val _error = MutableStateFlow<Int?>(null)
    val error = _error.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    val feeds: StateFlow<List<SpamFeedEntity>?> = repository.observeAll()
        .catch { failure ->
            if (failure is CancellationException) throw failure
            _error.value = R.string.feeds_load_error
            emit(emptyList())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun dismissError() { _error.value = null }
    fun add(name: String, url: String, onAdded: () -> Unit) = write(R.string.feeds_add_error) {
        repository.insert(SpamFeedEntity(name = name.trim(), url = url.trim(), isActive = true))
        withContext(Dispatchers.Main.immediate) { onAdded() }
    }
    fun setActive(id: Long, active: Boolean) = write(R.string.feeds_save_error) { repository.setActive(id, active) }
    fun delete(id: Long) = write(R.string.feeds_save_error) { repository.delete(id) }

    private fun write(error: Int, action: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _error.value = error }
            finally { _busy.value = false }
        }
    }
}
