package uz.callguard.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import uz.callguard.app.data.CallHistoryEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CallListScreen(modifier: Modifier = Modifier, viewModel: CallListViewModel = viewModel()) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var pendingDeleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    val today by produceState(LocalDate.now()) {
        while (true) {
            value = LocalDate.now()
            delay(30_000)
        }
    }
    val zone = ZoneId.systemDefault()
    val sections = remember(history, today, zone) { groupCallHistory(history.orEmpty(), today, zone) }

    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text("История вызовов", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(20.dp))
            when {
                history == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                history.orEmpty().isEmpty() -> ListEmptyState(Icons.Rounded.History, "История пуста",
                    "Здесь появятся проверенные звонки", Modifier.weight(1f))
                else -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    sections.forEach { (section, calls) ->
                        stickyHeader(key = section.name) {
                            Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                                Text(section.title, style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
                            }
                        }
                        items(calls, key = { it.id }) { call ->
                            CallHistoryItemCard(call, busy, onReportSpam = { viewModel.reportSpam(call) },
                                onIgnore = { pendingDeleteId = call.id },
                                modifier = Modifier.padding(horizontal = 16.dp).animateItem())
                        }
                    }
                }
            }
        }
    }
    if (pendingDeleteId != null) {
        AlertDialog(onDismissRequest = { pendingDeleteId = null },
            title = { Text("Точно удалить запись из истории?") },
            text = { Text("Запись исчезнет из истории. Список спама не изменится.") },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    pendingDeleteId?.let(viewModel::deleteHistory)
                    pendingDeleteId = null
                }) { Text("Да") }
            }, dismissButton = { TextButton(onClick = { pendingDeleteId = null }) { Text("Отмена") } })
    }
    ListErrorDialog(error, viewModel::dismissError)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CallHistoryItemCard(
    item: CallHistoryEntity,
    busy: Boolean,
    onReportSpam: () -> Unit,
    onIgnore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val formatter = remember { DateTimeFormatter.ofPattern("HH:mm · dd.MM.yyyy") }
    val time = Instant.ofEpochMilli(item.timestampMs).atZone(ZoneId.systemDefault()).format(formatter)
    Card(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(item.number, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(time, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = onReportSpam, enabled = !busy) {
                    Icon(Icons.Rounded.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("В спам")
                }
                OutlinedButton(onClick = onIgnore, enabled = !busy) {
                    Icon(Icons.Rounded.VisibilityOff, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Не спам / Игнор")
                }
            }
        }
    }
}
