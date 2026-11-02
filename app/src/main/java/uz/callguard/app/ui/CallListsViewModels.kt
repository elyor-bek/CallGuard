package uz.callguard.app.ui

import uz.callguard.app.R
import android.app.Application
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.withContext
import uz.callguard.app.data.SpamBackupCodec
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import uz.callguard.app.data.CallGuardDatabase
import uz.callguard.app.data.CourierMode
import uz.callguard.app.data.SettingsRepository
import java.time.ZoneId
import uz.callguard.app.data.SpamNumberEntity
import uz.callguard.app.data.SpamNumberRepository

abstract class CallListsViewModel(application: Application) : AndroidViewModel(application) {
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun dismissError() { _error.value = null }

    protected fun performAction(errorMessage: String = getApplication<android.app.Application>().getString(R.string.save_error), action: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _error.value = errorMessage
            } finally {
                _busy.value = false
            }
        }
    }
}

class CallListViewModel(application: Application) : CallListsViewModel(application) {
    private val historyDao = CallGuardDatabase.getInstance(application).callHistoryDao()
    private val spamRepository = SpamNumberRepository(application)
    private val settingsRepository = SettingsRepository(application)
    val history: StateFlow<List<CallHistoryGroup>?> = historyDao.observeAll()
        .map { groupDailyCalls(it, ZoneId.systemDefault()) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val spamCount: StateFlow<Int?> = spamRepository.observeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val courierModeEnd: StateFlow<Long?> = settingsRepository.courierModeEndFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _complaintSaved = MutableStateFlow(false)
    val complaintSaved = _complaintSaved.asStateFlow()
    fun dismissComplaint() { _complaintSaved.value = false }
    fun queueComplaint(group: CallHistoryGroup) = performAction {
        spamRepository.queueComplaint(group.latest.id)
        _complaintSaved.value = true
    }

    fun reportSpam(group: CallHistoryGroup) = performAction { spamRepository.reportCallsAsSpam(group.calls) }
    fun deleteHistory(ids: List<Long>) = performAction { historyDao.deleteEntries(ids) }
    fun pauseFor30Minutes() = performAction { settingsRepository.pauseProtection(CourierMode.HALF_HOUR_MS) }
    fun pauseForOneHour() = performAction { settingsRepository.pauseProtection(CourierMode.ONE_HOUR_MS) }
    fun resumeProtection() = performAction { settingsRepository.resumeProtection() }
}

class SpamNumbersViewModel(application: Application) : CallListsViewModel(application) {
    private val repository = SpamNumberRepository(application)
    val numbers: StateFlow<List<SpamNumberEntity>?> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _transferMessage = MutableStateFlow<String?>(null)
    val transferMessage = _transferMessage.asStateFlow()
    fun clearTransferMessage() { _transferMessage.value = null }

    fun exportBackup(onReady: (Uri) -> Unit) = performAction(getApplication<android.app.Application>().getString(R.string.export_error)) {
        val app = getApplication<Application>()
        val json = SpamBackupCodec.encode(repository.exportNumbers())
        require(json.toByteArray(Charsets.UTF_8).size <= SpamBackupCodec.MAX_BYTES)
        val directory = File(app.cacheDir, "spam_exports").apply { mkdirs() }
        val file = File(directory, "CallGuard-spam-${UUID.randomUUID()}.json")
        file.writeText(json, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        withContext(Dispatchers.Main.immediate) { onReady(uri) }
    }

    fun importBackup(uri: Uri) = performAction(getApplication<android.app.Application>().getString(R.string.import_error)) {
        val stream = requireNotNull(getApplication<Application>().contentResolver.openInputStream(uri))
        val bytes = stream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var count = input.read(buffer)
            while (count != -1) {
                require(output.size() + count <= SpamBackupCodec.MAX_BYTES)
                output.write(buffer, 0, count)
                count = input.read(buffer)
            }
            output.toByteArray()
        }
        val imported = SpamBackupCodec.decode(bytes.toString(Charsets.UTF_8))
        val added = repository.importNumbers(imported)
        _transferMessage.value = getApplication<android.app.Application>().getString(R.string.import_result, added, imported.size - added)
    }

    fun addNumber(number: String) = performAction { repository.addNumber(number, source = "manual") }
    fun deleteNumber(hash: String) = performAction { repository.removeNumberByHash(hash) }
}
