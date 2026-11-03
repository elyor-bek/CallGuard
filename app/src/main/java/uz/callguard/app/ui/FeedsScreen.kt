package uz.callguard.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import uz.callguard.app.R
import uz.callguard.app.sync.FeedUrlPolicy
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedsScreen(onBack: () -> Unit, modifier: Modifier = Modifier, model: FeedsViewModel = viewModel()) {
    val feeds by model.feeds.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var deleting by rememberSaveable { mutableStateOf<Long?>(null) }
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale) }
    val valid = remember(name, url) { name.trim().length in 1..120 && runCatching { FeedUrlPolicy.validate(url.trim()) }.isSuccess }
    Scaffold(modifier.fillMaxSize(), topBar = {
        TopAppBar(title = { Text(stringResource(R.string.feeds_title)) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
        })
    }, floatingActionButton = {
        FloatingActionButton(onClick = { if (!busy) adding = true }) { Icon(Icons.Rounded.Add, stringResource(R.string.feeds_add)) }
    }) { padding ->
        when {
            feeds == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            feeds.orEmpty().isEmpty() -> ListEmptyState(Icons.Rounded.CloudDownload, stringResource(R.string.feeds_empty),
                stringResource(R.string.feeds_empty_hint), Modifier.padding(padding))
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item(key = "feeds:cache-hint") {
                    Text(stringResource(R.string.feeds_cache_hint), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(feeds.orEmpty(), key = { it.id }) { feed ->
                    Card(Modifier.fillMaxWidth().animateItem(), shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(feed.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                val switchLabel = stringResource(R.string.feeds_toggle, feed.name)
                                Switch(checked = feed.isActive, enabled = !busy, onCheckedChange = { model.setActive(feed.id, it) },
                                    modifier = Modifier.semantics { contentDescription = switchLabel })
                            }
                            Text(feed.url, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val date = if (feed.lastSync <= 0) stringResource(R.string.feeds_never_synced)
                                    else Instant.ofEpochMilli(feed.lastSync).atZone(ZoneId.systemDefault()).format(formatter)
                                Text(stringResource(R.string.feeds_last_sync, date), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                IconButton(enabled = !busy, onClick = { deleting = feed.id }) {
                                    Icon(Icons.Rounded.Delete, stringResource(R.string.feeds_delete), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (adding) AlertDialog(onDismissRequest = { if (!busy) adding = false }, title = { Text(stringResource(R.string.feeds_add)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, onValueChange = { name = it.take(120) }, enabled = !busy, singleLine = true,
                label = { Text(stringResource(R.string.feeds_name)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(url, onValueChange = { url = it.take(2048) }, enabled = !busy, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                label = { Text(stringResource(R.string.feeds_url)) }, modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.feeds_https_hint), style = MaterialTheme.typography.bodySmall)
        } }, confirmButton = { TextButton(enabled = valid && !busy, onClick = {
            model.add(name, url) { adding = false; name = ""; url = "" }
        }) { Text(stringResource(R.string.add)) } }, dismissButton = {
            TextButton(enabled = !busy, onClick = { adding = false }) { Text(stringResource(R.string.cancel)) }
        })
    if (deleting != null) AlertDialog(onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.feeds_delete)) },
        text = { Text(stringResource(R.string.feeds_delete_hint)) },
        confirmButton = { TextButton(enabled = !busy, onClick = { deleting?.let(model::delete); deleting = null }) { Text(stringResource(R.string.yes)) } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } })
    error?.let { ListErrorDialog(stringResource(it), model::dismissError) }
}
