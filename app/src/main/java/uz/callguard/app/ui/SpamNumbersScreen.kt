package uz.callguard.app.ui

import uz.callguard.app.R
import androidx.compose.ui.res.stringResource
import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Download
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SpamNumbersScreen(modifier: Modifier = Modifier, viewModel: SpamNumbersViewModel = viewModel()) {
    val numbers by viewModel.numbers.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val exportTitle = stringResource(R.string.export)
    val shareLabel = stringResource(R.string.share_label)
    val message by viewModel.transferMessage.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.clearTransferMessage() }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(viewModel::importBackup)
    }
    var input by rememberSaveable { mutableStateOf("") }
    val formatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy") }
    // Keep manual entry, but do not allow arbitrary labels or hashes into the phone display.
    val validInput = input.trim().matches(Regex("\\+?[0-9 ()-]{3,25}")) && input.count(Char::isDigit) in 3..15

    Scaffold(modifier = modifier.fillMaxSize(), snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.spam_base), style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(enabled = !busy && numbers != null, onClick = {
                    viewModel.exportBackup { uri ->
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            clipData = ClipData.newUri(context.contentResolver, shareLabel, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(send, exportTitle))
                    }
                }) { Icon(Icons.Rounded.Upload, contentDescription = stringResource(R.string.export)) }
                IconButton(enabled = !busy, onClick = { importLauncher.launch("application/json") }) {
                    Icon(Icons.Rounded.Download, contentDescription = stringResource(R.string.import_backup))
                }
            }
            Text(stringResource(R.string.backup_hint),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = input, onValueChange = { input = it },
                    label = { Text(stringResource(R.string.phone)) }, placeholder = { Text("+998 90 123 45 67") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    enabled = !busy, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
                Button(onClick = { viewModel.addNumber(input.trim()); input = "" }, enabled = validInput && !busy,
                    modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.add)) }
            }
            when {
                numbers == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                numbers.orEmpty().isEmpty() -> ListEmptyState(Icons.Rounded.Security, stringResource(R.string.spam_empty),
                    stringResource(R.string.spam_hint), Modifier.weight(1f))
                else -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(numbers.orEmpty(), key = { it.numberHash }) { number ->
                        Card(Modifier.fillMaxWidth().animateItem(), shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                            Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer) {
                                    Icon(Icons.Rounded.Security, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(12.dp).size(24.dp))
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(number.phoneNumber ?: stringResource(R.string.unknown_number), style = MaterialTheme.typography.titleMedium)
                                    Text(stringResource(R.string.added_date, Instant.ofEpochMilli(number.addedAtEpochMs)
                                        .atZone(ZoneId.systemDefault()).format(formatter)),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { viewModel.deleteNumber(number.numberHash) }, enabled = !busy) {
                                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.delete_spam, number.phoneNumber ?: stringResource(R.string.record)),
                                        tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    ListErrorDialog(error, viewModel::dismissError)
}
