package uz.callguard.app.ui

import uz.callguard.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.CloudUpload
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
import uz.callguard.app.data.CourierMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CallListScreen(modifier: Modifier = Modifier, viewModel: CallListViewModel = viewModel()) {
    val complaintSaved by viewModel.complaintSaved.collectAsStateWithLifecycle()
    val spamCount by viewModel.spamCount.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val courierModeEnd by viewModel.courierModeEnd.collectAsStateWithLifecycle()
    var pendingDeleteIds by rememberSaveable { mutableStateOf<List<Long>?>(null) }
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
    val sections = remember(history, today) { groupCallHistory(history.orEmpty(), today) }

    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(stringResource(R.string.history), style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(20.dp))
            spamCount?.let { count ->
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.spam_summary, count), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.summary_hint), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            CourierModeCard(courierModeEnd, now, busy,
                viewModel::pauseFor30Minutes, viewModel::pauseForOneHour, viewModel::resumeProtection)
            when {
                history == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                history.orEmpty().isEmpty() -> ListEmptyState(Icons.Rounded.History, stringResource(R.string.history_empty),
                    stringResource(R.string.history_hint), Modifier.weight(1f))
                else -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    sections.forEach { (section, calls) ->
                        stickyHeader(key = "section:${section.name}") {
                            Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(section.titleRes), style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
                            }
                        }
                        items(calls, key = { it.key }) { call ->
                            CallHistoryItemCard(call, busy, onReportSpam = { viewModel.reportSpam(call) },
                                onIgnore = { pendingDeleteIds = call.calls.map { it.id } },
                                onComplaint = { viewModel.queueComplaint(call) },
                                modifier = Modifier.padding(horizontal = 16.dp).animateItem())
                        }
                    }
                }
            }
        }
    }
    if (pendingDeleteIds != null) {
        AlertDialog(onDismissRequest = { pendingDeleteIds = null },
            title = { Text(if (pendingDeleteIds.orEmpty().size > 1) stringResource(R.string.delete_calls) else stringResource(R.string.delete_call)) },
            text = { Text(stringResource(R.string.delete_history_body, pendingDeleteIds.orEmpty().size)) },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    pendingDeleteIds?.let(viewModel::deleteHistory)
                    pendingDeleteIds = null
                }) { Text(stringResource(R.string.yes)) }
            }, dismissButton = { TextButton(onClick = { pendingDeleteIds = null }) { Text(stringResource(R.string.cancel)) } })
    }
    if (complaintSaved) AlertDialog(onDismissRequest = viewModel::dismissComplaint,
        title = { Text(stringResource(R.string.complaint_saved)) },
        text = { Text(stringResource(R.string.complaint_local_hint)) },
        confirmButton = { TextButton(onClick = viewModel::dismissComplaint) { Text(stringResource(R.string.ok)) } })
    ListErrorDialog(error, viewModel::dismissError)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CallHistoryItemCard(
    item: CallHistoryGroup,
    busy: Boolean,
    onReportSpam: () -> Unit,
    onIgnore: () -> Unit,
    onComplaint: () -> Unit,
    modifier: Modifier = Modifier
) {
    val formatter = remember { DateTimeFormatter.ofPattern("HH:mm · dd.MM.yyyy") }
    val time = Instant.ofEpochMilli(item.latest.timestampMs).atZone(ZoneId.systemDefault()).format(formatter)
    Card(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (item.count > 1) "${item.number} (x${item.count})" else item.number, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(time, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = onReportSpam, enabled = !busy) {
                    Icon(Icons.Rounded.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.report_spam))
                }
                OutlinedButton(onClick = onComplaint, enabled = !busy) {
                    Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.complaint))
                }
                OutlinedButton(onClick = onIgnore, enabled = !busy) {
                    Icon(Icons.Rounded.VisibilityOff, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ignore))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CourierModeCard(
    endTimestamp: Long?,
    now: Long,
    busy: Boolean,
    onPause30: () -> Unit,
    onPause60: () -> Unit,
    onResume: () -> Unit
) {
    val deadline = endTimestamp ?: 0L
    val active = endTimestamp != null && CourierMode.isActive(deadline, now)
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = if (active) MaterialTheme.colorScheme.tertiaryContainer
            else MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.Security, contentDescription = null)
                Text(when {
                    endTimestamp == null -> stringResource(R.string.loading_protection)
                    active -> stringResource(R.string.courier_remaining, CourierMode.remainingMinutes(deadline, now))
                    else -> stringResource(R.string.protection_active)
                }, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            }
            Text(if (active) stringResource(R.string.pause_hint)
                else stringResource(R.string.courier_hint),
                style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (active) {
                    Button(onClick = onResume, enabled = !busy) { Text(stringResource(R.string.resume)) }
                } else {
                    Button(onClick = onPause30, enabled = !busy && endTimestamp != null) { Text(stringResource(R.string.pause_30)) }
                    OutlinedButton(onClick = onPause60, enabled = !busy && endTimestamp != null) { Text(stringResource(R.string.pause_60)) }
                }
            }
        }
    }
}
