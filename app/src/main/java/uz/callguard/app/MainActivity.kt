package uz.callguard.app

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import uz.callguard.app.data.BlockMode
import uz.callguard.app.data.SettingsRepository
import uz.callguard.app.ui.CallListScreen
import uz.callguard.app.ui.OnboardingScreen
import uz.callguard.app.ui.SpamNumbersScreen
import uz.callguard.app.ui.theme.CallGuardTheme

class MainActivity : ComponentActivity() {

    private var isRoleGranted by mutableStateOf(false)
    private var isNotificationGranted by mutableStateOf(false)
    private var isOverlayGranted by mutableStateOf(false)
    private var isContactsGranted by mutableStateOf(false)
    private var isBatteryOptIgnored by mutableStateOf(false)

    private val requestRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Log.d("CallGuard", "ROLE_CALL_SCREENING успешно получена")
            isRoleGranted = true
        } else {
            Log.w("CallGuard", "Пользователь отклонил запрос ROLE_CALL_SCREENING")
            isRoleGranted = isCallScreeningRoleGranted()
        }
    }

    private val permissionRequestLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissions.forEach { (perm, granted) ->
            Log.d("CallGuard", "Permission $perm granted: $granted")
        }
        checkAllPermissionsStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CallGuardTheme {
                val scope = rememberCoroutineScope()
                val settingsRepository = remember { SettingsRepository(applicationContext) }
                var currentBlockMode by remember { mutableStateOf(BlockMode.WARN) }
                var currentTab by remember { mutableStateOf(0) }

                LaunchedEffect(Unit) {
                    currentBlockMode = settingsRepository.getBlockMode()
                }

                // Показываем Онбординг, если ключевые разрешения еще не даны
                val showOnboarding = !isRoleGranted || !isContactsGranted

                if (showOnboarding) {
                    OnboardingScreen(
                        isRoleGranted = isRoleGranted,
                        onRequestRole = { requestCallScreeningRole() },
                        isContactsGranted = isContactsGranted,
                        onRequestContactsPermission = { requestContactsPermission() },
                        isBatteryOptIgnored = isBatteryOptIgnored,
                        onRequestBatteryOpt = { requestIgnoreBatteryOptimizations() },
                        onCompleteOnboarding = { checkAllPermissionsStatus() }
                    )
                } else {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        bottomBar = {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = currentTab == 0,
                                    onClick = { currentTab = 0 },
                                    label = { Text("Звонки") },
                                    icon = { Text("📞") }
                                )
                                NavigationBarItem(
                                    selected = currentTab == 1,
                                    onClick = { currentTab = 1 },
                                    label = { Text("Спам-база") },
                                    icon = { Text("🛡") }
                                )
                                NavigationBarItem(
                                    selected = currentTab == 2,
                                    onClick = { currentTab = 2 },
                                    label = { Text("Настройки") },
                                    icon = { Text("⚙") }
                                )
                            }
                        }
                    ) { innerPadding ->
                        Box(modifier = Modifier.padding(innerPadding)) {
                            when (currentTab) {
                                0 -> CallListScreen()
                                1 -> SpamNumbersScreen()
                                2 -> SettingsScreen(
                                    currentBlockMode = currentBlockMode,
                                    onBlockModeSelected = { mode ->
                                        currentBlockMode = mode
                                        scope.launch { settingsRepository.setBlockMode(mode) }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkAllPermissionsStatus()
    }

    private fun checkAllPermissionsStatus() {
        isRoleGranted = isCallScreeningRoleGranted()
        isNotificationGranted = isNotificationAccessGranted()
        isOverlayGranted = Settings.canDrawOverlays(this)
        isContactsGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        isBatteryOptIgnored = powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    fun isCallScreeningRoleGranted(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            return roleManager?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true
        }
        return true
    }

    fun requestCallScreeningRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager != null && !roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                requestRoleLauncher.launch(intent)
            }
        }
    }

    private fun requestContactsPermission() {
        permissionRequestLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
    }

    private fun isNotificationAccessGranted(): Boolean {
        val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(this)
        return packageName in enabledPackages
    }

    private fun requestIgnoreBatteryOptimizations() {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("CallGuard", "Ошибка открытия настроек оптимизации батареи", e)
        }
    }
}

@Composable
fun SettingsScreen(
    currentBlockMode: BlockMode,
    onBlockModeSelected: (BlockMode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Настройки защиты",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Режим реакции на спам-вызов",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Выберите, как приложение должно обрабатывать обнаруженный спам:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = currentBlockMode == BlockMode.WARN,
                            onClick = { onBlockModeSelected(BlockMode.WARN) },
                            label = { Text("WARN (Предупреждать)") }
                        )
                        FilterChip(
                            selected = currentBlockMode == BlockMode.MUTE,
                            onClick = { onBlockModeSelected(BlockMode.MUTE) },
                            label = { Text("MUTE (Без звука)") }
                        )
                    }

                    FilterChip(
                        selected = currentBlockMode == BlockMode.REJECT,
                        onClick = { onBlockModeSelected(BlockMode.REJECT) },
                        label = { Text("REJECT (Сбрасывать)") }
                    )
                }
            }
        }
    }
}
