package uz.callguard.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import uz.callguard.app.data.SpamNumberRepository

/**
 * Простой CRUD для LocalSpamDB: добавить номер вручную, посмотреть список, удалить.
 */
@Composable
fun SpamNumbersScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = remember { SpamNumberRepository(context) }
    val scope = rememberCoroutineScope()

    var input by remember { mutableStateOf("") }
    val numbersFlow = remember { repository.observeAll() }
    val numbers by numbersFlow.collectAsState(initial = emptyList())

    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(text = "LocalSpamDB — ${numbers.size} хэшей")

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("+998 90 123 45 67") },
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = {
                    val toAdd = input
                    if (toAdd.isNotBlank()) {
                        scope.launch(Dispatchers.IO) {
                            repository.addNumber(toAdd, source = "manual")
                        }
                        input = ""
                    }
                }) {
                    Text("Добавить")
                }
            }

            LazyColumn {
                items(numbers, key = { it.numberHash }) { entity ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = entity.numberHash, maxLines = 1)
                            Text(text = "${entity.source} · reports: ${entity.reportCount}")
                        }
                        TextButton(onClick = {
                            scope.launch(Dispatchers.IO) {
                                repository.removeNumberByHash(entity.numberHash)
                            }
                        }) {
                            Text("Удалить")
                        }
                    }
                }
            }
        }
    }
}
