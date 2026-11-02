package uz.callguard.app.ui

import uz.callguard.app.R
import androidx.compose.ui.res.stringResource
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

@Composable
fun ProtectionRulesScreen(modifier: Modifier = Modifier, viewModel: RulesViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    val rules = state

    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        if (rules == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text(stringResource(R.string.smart_rules), style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
                }
                item { Text(stringResource(R.string.general_filters), style = MaterialTheme.typography.titleMedium) }
                item {
                    RuleSwitchCard(stringResource(R.string.block_hidden), rules.settings.blockHiddenNumbers,
                        !busy, viewModel::setBlockHiddenNumbers)
                }
                item {
                    RuleSwitchCard(stringResource(R.string.block_foreign), rules.settings.blockForeignNumbers,
                        !busy, viewModel::setBlockForeignNumbers)
                }
                item {
                    Text(stringResource(R.string.foreign_hint),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item {
                    Text(stringResource(R.string.mask_blocking), style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 12.dp))
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(input, onValueChange = { input = it }, modifier = Modifier.weight(1f),
                            label = { Text(stringResource(R.string.prefix)) }, placeholder = { Text(stringResource(R.string.prefix_example)) },
                            singleLine = true, enabled = !busy,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            shape = MaterialTheme.shapes.medium)
                        Button(onClick = { viewModel.addPattern(input) { input = "" } },
                            enabled = !busy && input.isNotBlank()) { Text(stringResource(R.string.add)) }
                    }
                }
                item {
                    Text(stringResource(R.string.mask_hint),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (rules.patterns.isEmpty()) {
                    item {
                        ListEmptyState(Icons.Rounded.Security, stringResource(R.string.masks_empty), stringResource(R.string.masks_hint),
                            Modifier.heightIn(min = 220.dp))
                    }
                }
                items(rules.patterns, key = { it.pattern }) { pattern ->
                    Card(Modifier.fillMaxWidth().animateItem(), shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(pattern.pattern, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            IconButton(onClick = { viewModel.deletePattern(pattern.pattern) }, enabled = !busy) {
                                Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.delete_pattern, pattern.pattern),
                                    tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
    ListErrorDialog(error, viewModel::dismissError)
}

@Composable
private fun RuleSwitchCard(title: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Card(shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
    }
}
