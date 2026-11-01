package uz.callguard.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingScreen(
    isRoleGranted: Boolean,
    onRequestRole: () -> Unit,
    isContactsGranted: Boolean,
    onRequestContactsPermission: () -> Unit,
    isBatteryOptIgnored: Boolean,
    onRequestBatteryOpt: () -> Unit,
    onCompleteOnboarding: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Добро пожаловать в CallGuard",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Настройте разрешения для полной локальной защиты от спама и скама",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
                )

                // Шаг 1: Роль CALL_SCREENING
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isRoleGranted) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "1. Защита звонков (ROLE_CALL_SCREENING)",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (isRoleGranted) "✓ Разрешение предоставлено" else "Назначьте CallGuard приложением по умолчанию для фильтрации звонков",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        if (!isRoleGranted) {
                            Button(
                                onClick = onRequestRole,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            ) {
                                Text("Назначить фильтром вызовов")
                            }
                        }
                    }
                }

                // Шаг 2: Разрешение на Контакты
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isContactsGranted) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "2. Белый список контактов (READ_CONTACTS)",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (isContactsGranted) "✓ Доступ к контактам разрешен" else "Разрешите доступ к контактам, чтобы вызовы от ваших знакомых никогда не блокировались",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        if (!isContactsGranted) {
                            Button(
                                onClick = onRequestContactsPermission,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            ) {
                                Text("Предоставить доступ к контактам")
                            }
                        }
                    }
                }

                // Шаг 3: Оптимизация батареи (Samsung/Xiaomi)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "3. Фоновое функционирование",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "⚠️ Внимание: если у вас устройство Samsung или Xiaomi, отключите оптимизацию батареи, чтобы система не останавливала фоновый сервис.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        if (!isBatteryOptIgnored) {
                            OutlinedButton(
                                onClick = onRequestBatteryOpt,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            ) {
                                Text("Отключить оптимизацию батареи")
                            }
                        } else {
                            Text("✓ Оптимизация отключена", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            Button(
                onClick = onCompleteOnboarding,
                enabled = isRoleGranted,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Перейти к приложению")
            }
        }
    }
}
