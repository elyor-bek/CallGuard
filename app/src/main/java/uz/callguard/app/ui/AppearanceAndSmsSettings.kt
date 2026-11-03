package uz.callguard.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.callguard.app.R
import uz.callguard.app.data.*

@Composable
fun AppearanceAndSmsSettings(model: UiSettingsViewModel, notificationAccess: Boolean, onRequestAccess: () -> Unit) {
    val saveError = stringResource(R.string.save_error)
    val invalidRule = stringResource(R.string.sms_invalid_rule)
    val preferences by model.preferences.collectAsStateWithLifecycle()
    val keywords by model.keywords.collectAsStateWithLifecycle()
    val blocked by model.blocked.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    var regex by rememberSaveable { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.appearance), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.theme_mode), style = MaterialTheme.typography.titleMedium)
            ThemeMode.entries.forEach { mode ->
                val label = when (mode) { ThemeMode.SYSTEM -> R.string.follow_system; ThemeMode.LIGHT -> R.string.theme_light; ThemeMode.DARK -> R.string.theme_dark }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = preferences?.theme == mode, enabled = !busy && preferences != null,
                        onClick = { model.setTheme(mode, saveError) })
                    Text(stringResource(label))
                }
            }
            Text(stringResource(R.string.app_language), style = MaterialTheme.typography.titleMedium)
            AppLanguage.entries.forEach { language ->
                val label = when (language) { AppLanguage.SYSTEM -> R.string.follow_system; AppLanguage.EN -> R.string.language_en; AppLanguage.RU -> R.string.language_ru; AppLanguage.UZ -> R.string.language_uz }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = preferences?.language == language, enabled = !busy && preferences != null,
                        onClick = { model.setLanguage(language, saveError) })
                    Text(stringResource(label))
                }
            }
        }
    }
    Card(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.sms_filter), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.sms_privacy_hint), style = MaterialTheme.typography.bodyMedium)
            if (!notificationAccess) OutlinedButton(onClick = onRequestAccess) { Text(stringResource(R.string.sms_allow_access)) }
            else Text(stringResource(R.string.sms_access_granted))
            OutlinedTextField(value = input, onValueChange = { input = it }, label = { Text(stringResource(R.string.sms_keyword)) },
                modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = regex, enabled = !busy, onCheckedChange = { regex = it })
                Text(stringResource(R.string.sms_regex))
            }
            Button(enabled = !busy && input.isNotBlank(), onClick = { model.addKeyword(input, regex, invalidRule) }) { Text(stringResource(R.string.add)) }
            keywords.forEach { rule ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(rule.keyword, modifier = Modifier.weight(1f))
                    if (rule.isRegex) Text(stringResource(R.string.sms_regex))
                    IconButton(enabled = !busy, onClick = { model.deleteKeyword(rule.id, saveError) }) {
                        Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.sms_delete_keyword))
                    }
                }
            }
            Text(stringResource(R.string.sms_recent), style = MaterialTheme.typography.titleMedium)
            if (blocked.isEmpty()) Text(stringResource(R.string.sms_history_empty))
            blocked.take(5).forEach { message ->
                HorizontalDivider()
                Text(message.sender.ifBlank { message.packageName }, style = MaterialTheme.typography.titleSmall)
                Text(message.body, maxLines = 3, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    ListErrorDialog(error, model::dismissError)
}
