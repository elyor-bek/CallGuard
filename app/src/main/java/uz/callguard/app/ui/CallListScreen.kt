package uz.callguard.app.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.callguard.app.data.CallGuardDatabase
import uz.callguard.app.data.CallHistoryEntity
import uz.callguard.app.data.SpamNumberRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CallListScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val database = remember { CallGuardDatabase.getInstance(context) }
    val callHistoryDao = remember { database.callHistoryDao() }
    val spamRepository = remember { SpamNumberRepository(context) }
    val scope = rememberCoroutineScope()

    val historyFlow = remember { callHistoryDao.observeAll() }
    val historyList by historyFlow.collectAsState(initial = emptyList())

    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                text = "История проверенных звонков",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (historyList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "История звонков пока пуста",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(historyList, key = { it.id }) { item ->
                        CallHistoryItemCard(
                            item = item,
                            onReportSpam = {
                                scope.launch(Dispatchers.IO) {
                                    spamRepository.addNumber(item.number, source = "user_report")
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Номер внесен в черный список (user_report)", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onReportNotSpam = {
                                scope.launch(Dispatchers.IO) {
                                    spamRepository.removeNumber(item.number)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Номер удален из спам-базы", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CallHistoryItemCard(
    item: CallHistoryEntity,
    onReportSpam: () -> Unit,
    onReportNotSpam: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm · dd.MM.yyyy", Locale.getDefault()) }
    val formattedTime = remember(item.timestampMs) { dateFormat.format(Date(item.timestampMs)) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.number,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // Метка Вердикта
                val badgeColor = if (item.isSpam) Color(0xFFD32F2F) else Color(0xFF388E3C)
                val badgeText = if (item.isSpam) "СПАМ (${item.blockModeApplied})" else "ЧИСТЫЙ"

                Box(
                    modifier = Modifier
                        .background(badgeColor, shape = RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = "$formattedTime ${if (item.reasonDetails.isNotBlank()) "· ${item.reasonDetails}" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )

            // UserReport Кнопки
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onReportSpam,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text("Это спам", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onReportNotSpam,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Не спам", fontSize = 12.sp)
                }
            }
        }
    }
}
